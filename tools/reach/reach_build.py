"""寄り道の到達圏（F06）の版を作る・使う版を切り替える（#168。手順の決まりは #163）。

使い方：
  python reach_build.py build --version <版> --n02 <N02 の UTF-8 フォルダ> --n02-year <年度> --osm <歩ける道の .osm.pbf> [--bbox 西 南 東 北]
  python reach_build.py activate --version <版>
  python reach_build.py list

  例：python reach_build.py build --version 2026-11-19.1 --n02 ~/Downloads/N02-25_GML/UTF-8 --n02-year 2025 \\
        --osm ~/Downloads/kanto-261119-5339-highway.osm.pbf --bbox 139 35.3333 140 36

接続先は環境変数 EXDAY_DSN（既定：compose.yaml の DB、postgresql://exday:exday@127.0.0.1:5433/exday）。

- build：版（reach.build）を BUILDING で作り、N02 から駅の場所・計算の中の駅・路線と鉄道のグラフを、OSM の歩ける道から
  駅から場所への歩きを作って、READY にする。失敗したら FAILED にする（使っている版はそのまま）。鉄道のグラフが空
  （辺かグラフに結び付いた駅の点が0）なら失敗にする
- 探索用の路線（運行系統）：--services のフォルダ（既定 tools/reach/services）の *.toml を読み、版の鉄道のグラフに足す
  （build_service.sql。F06「データの形」9、#170）。停車駅・線路が見つからない、置き換えると乗れなくなる駅があるときは失敗にする
- activate：READY（または前に使っていた RETIRED）の版を ACTIVE にし、それまでの ACTIVE を RETIRED にする（1つのトランザクション）。
  切り戻しは、前の版を activate する
- OSM の .osm.pbf は、highway の道を切り出したもの（ex-day/poc reach/osmwalk/extract_highway.py）。小さな島を除いた歩きの
  グラフと駅の点は、版に残す（DB V5）。登録した場所の分だけ歩きを作り直すため（API の処理が reach.refresh_place_walk を呼ぶ）
- 版の行を足すときに、場所の変更と同じ鍵（reach.build_lock_key()）を排他で取る。版の行より前に確定した場所の変更はこの処理が
  読み、後の変更は場所の変更のトリガーが待ち行列（reach.place_walk_job）に積む。READY になると API の処理が作り直す
"""
import argparse
import json
import math
import os
import sys
import time
import tomllib
from collections import Counter
from pathlib import Path

import osmium
import psycopg

DSN = os.environ.get("EXDAY_DSN", "postgresql://exday:exday@127.0.0.1:5433/exday")
SQL_DIR = Path(__file__).resolve().parent / "sql"
SERVICES_DIR = Path(__file__).resolve().parent / "services"

# 計算の決まりの値（F06「設定で持つ値」）。API の exday.detour.* と同じ値にする
TRANSFER_MINUTES = 10
WALK_CAP_MINUTES = 15
WALK_METERS_PER_MINUTE = 80
# 小さな島の閾値（点の数。2026-11-26 に #163 で確定）と、道の点に結ぶ距離の上限（m。poc #46）
MIN_ISLAND_NODES = 100
SNAP_METERS = 300

# 歩ける道（poc #46）：highway のうち、これらを除く。foot=no・use_sidepath と、foot=yes 等の無い access=private/no も除く
EXCLUDED_HIGHWAY = {
    "motorway", "motorway_link", "construction", "proposed", "abandoned", "disused", "razed",
    "raceway", "bus_guideway", "escape", "rest_area", "services", "emergency_bay",
}
FOOT_OK = {"yes", "designated", "permissive", "official"}


def log(message: str) -> None:
    print(f"{time.strftime('%H:%M:%S')} {message}", flush=True)


def walkable(tags) -> bool:
    hw = tags.get("highway")
    if hw is None or hw in EXCLUDED_HIGHWAY:
        return False
    foot = tags.get("foot")
    if foot in ("no", "use_sidepath"):
        return False
    if tags.get("access") in ("private", "no") and foot not in FOOT_OK:
        return False
    return True


def meters(lon1, lat1, lon2, lat2) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(a))


def in_bbox(coords, bbox) -> bool:
    if bbox is None:
        return True
    w, s, e, n = bbox
    return any(w <= x <= e and s <= y <= n for x, y in coords)


def load_n02(cur, folder: Path, bbox) -> None:
    def features(pattern):
        path = next(folder.glob(pattern))
        with path.open(encoding="utf-8") as f:
            return json.load(f)["features"]

    def ewkt(geom):
        if geom["type"] != "LineString":
            raise ValueError(f"想定していない形：{geom['type']}")
        return "SRID=6668;LINESTRING(" + ", ".join(f"{x} {y}" for x, y in geom["coordinates"]) + ")"

    cur.execute("""
        CREATE TEMP TABLE n02_station (
          id serial PRIMARY KEY, rail_class text NOT NULL, operator_type text NOT NULL, line_name text NOT NULL,
          operator text NOT NULL, station_name text NOT NULL, station_code text NOT NULL, group_code text NOT NULL,
          geom geometry(LineString, 6668) NOT NULL)""")
    cur.execute("""
        CREATE TEMP TABLE n02_section (
          id serial PRIMARY KEY, rail_class text NOT NULL, operator_type text NOT NULL, line_name text NOT NULL,
          operator text NOT NULL, geom geometry(LineString, 6668) NOT NULL)""")
    n_st = n_se = 0
    with cur.copy("COPY n02_station (rail_class, operator_type, line_name, operator, station_name, station_code,"
                  " group_code, geom) FROM STDIN") as copy:
        for f in features("N02-*_Station.geojson"):
            if not in_bbox(f["geometry"]["coordinates"], bbox):
                continue
            p = f["properties"]
            copy.write_row((p["N02_001"], p["N02_002"], p["N02_003"], p["N02_004"], p["N02_005"], p["N02_005c"],
                            p["N02_005g"], ewkt(f["geometry"])))
            n_st += 1
    with cur.copy("COPY n02_section (rail_class, operator_type, line_name, operator, geom) FROM STDIN") as copy:
        for f in features("N02-*_RailroadSection.geojson"):
            if not in_bbox(f["geometry"]["coordinates"], bbox):
                continue
            p = f["properties"]
            copy.write_row((p["N02_001"], p["N02_002"], p["N02_003"], p["N02_004"], ewkt(f["geometry"])))
            n_se += 1
    log(f"N02：駅 {n_st} 行、区間 {n_se} 本")


def load_osm_walk(cur, pbf: Path) -> None:
    cur.execute("CREATE TEMP TABLE walk_node (node_id bigint PRIMARY KEY, geom geometry(Point, 4326) NOT NULL)")
    cur.execute("""
        CREATE TEMP TABLE walk_edge (id bigserial PRIMARY KEY, source bigint NOT NULL, target bigint NOT NULL,
          cost double precision NOT NULL, reverse_cost double precision NOT NULL, geom geometry(LineString, 4326) NOT NULL)""")
    src = str(pbf)
    use = Counter()
    for w in osmium.FileProcessor(src, osmium.osm.WAY):
        if not walkable(w.tags):
            continue
        refs = [n.ref for n in w.nodes]
        if len(refs) < 2:
            continue
        use.update(set(refs))
        use[refs[0]] += 1
        use[refs[-1]] += 1
    nodes = {}
    n_edges = 0
    with cur.copy("COPY walk_edge (source, target, cost, reverse_cost, geom) FROM STDIN") as copy:
        for o in osmium.FileProcessor(src, osmium.osm.NODE | osmium.osm.WAY).with_locations():
            if not o.is_way() or not walkable(o.tags):
                continue
            pts = [(n.ref, n.location) for n in o.nodes if n.location.valid()]
            if len(pts) < 2:
                continue
            seg = [pts[0]]
            for ref, loc in pts[1:]:
                seg.append((ref, loc))
                if use[ref] > 1 or ref == pts[-1][0]:
                    if len(seg) >= 2 and seg[0][0] != seg[-1][0]:
                        length = sum(meters(a[1].lon, a[1].lat, b[1].lon, b[1].lat) for a, b in zip(seg, seg[1:]))
                        minutes = length / WALK_METERS_PER_MINUTE
                        coords = ", ".join(f"{loc.lon} {loc.lat}" for _, loc in seg)
                        copy.write_row((seg[0][0], seg[-1][0], minutes, minutes, f"SRID=4326;LINESTRING({coords})"))
                        n_edges += 1
                        for r, loc in (seg[0], seg[-1]):
                            nodes[r] = (loc.lon, loc.lat)
                    seg = [(ref, loc)]
    with cur.copy("COPY walk_node (node_id, geom) FROM STDIN") as copy:
        for r, (lon, lat) in nodes.items():
            copy.write_row((r, f"SRID=4326;POINT({lon} {lat})"))
    log(f"OSM：歩ける道の辺 {n_edges} 本、点 {len(nodes)} 個")


def _lines(path: Path, key: str, operator: str, entries) -> list:
    """通る路線：「路線名」（ファイルの operator の路線）か、{ operator = "...", name = "..." }（事業者をまたぐ直通）。"""
    out = []
    for entry in entries:
        if isinstance(entry, str):
            out.append((operator, entry))
        elif isinstance(entry, dict) and set(entry) == {"operator", "name"}:
            out.append((entry["operator"], entry["name"]))
        else:
            raise ValueError(f"{path.name}:{key}：lines は「路線名」か {{ operator = ..., name = ... }}：{entry!r}")
    return out


def _points(path: Path, key: str, entries) -> list:
    """停車駅と通過地点の並び：「駅名」は停車駅、{ via = "駅名" } は通過地点（経路を決めるだけで、乗り降り・乗換はしない）。"""
    out = []
    for entry in entries:
        if isinstance(entry, str):
            out.append((entry, True))
        elif isinstance(entry, dict) and set(entry) == {"via"}:
            out.append((entry["via"], False))
        else:
            raise ValueError(f"{path.name}:{key}：stops は「駅名」か {{ via = \"駅名\" }}：{entry!r}")
    return out


def read_services(folder: Path) -> list:
    """探索用の路線（運行系統）の定義（*.toml）を読んで確かめる（版を作り始める前に。#170）。

    返すのは系統ごとの dict（key・operator・name・kind・speed_kmh・loop・lines・points・stop_names）と、置き換えてよい路線の組と、
    駅でないところの路線のつなぎ目（2つの路線と近くの駅。PR #174 のレビュー）。
    """
    services, replace, junctions = [], [], []
    for path in sorted(folder.glob("*.toml")):
        with path.open("rb") as f:
            doc = tomllib.load(f)
        operator = doc["operator"]
        replace += _lines(path, "replace_lines", operator, doc.get("replace_lines", []))
        for j in doc.get("junction", []):
            if set(j) - {"lines", "near", "note"} or not isinstance(j.get("near"), str):
                raise ValueError(f"{path.name}:junction：lines（2つ）・near（駅名）・note だけを書く：{j!r}")
            pair = _lines(path, "junction", operator, j.get("lines", []))
            if len(pair) != 2 or pair[0] == pair[1]:
                raise ValueError(f"{path.name}:junction：lines には違う路線を2つ書く：{j!r}")
            junctions.append((pair[0], pair[1], j["near"]))
        for svc in doc.get("service", []):
            key = svc["key"]
            points = _points(path, key, svc["stops"])
            stops = [name for name, is_stop in points if is_stop]
            loop = bool(svc.get("loop", False))
            if len(stops) < 2 or not points[0][1] or (not loop and not points[-1][1]):
                raise ValueError(f"{path.name}:{key}：停車駅が2つ未満、または並びが停車駅で始まらない・停車駅で終わらない")
            if loop and len(set(stops)) != len(stops):
                raise ValueError(f"{path.name}:{key}：環状で同じ駅を繰り返している（最初の駅を最後に繰り返さない）")
            names = svc.get("stop_names", {})
            unknown = set(names) - set(stops)
            if unknown:
                raise ValueError(f"{path.name}:{key}：stop_names に停車駅でない駅がある：{'・'.join(sorted(unknown))}")
            services.append({"key": key, "operator": svc.get("operator", operator), "name": svc["name"],
                             "kind": svc.get("kind", "普通"), "speed_kmh": float(svc["speed_kmh"]), "loop": loop,
                             "lines": _lines(path, key, operator, svc["lines"]), "points": points, "stop_names": names})
    keys = [s["key"] for s in services]
    if len(set(keys)) != len(keys):
        raise ValueError("系統の key が重なっている")
    return [services, replace, junctions]


def load_services(cur, defs: list) -> int:
    """読んだ系統の定義を、build_service.sql が読む一時の表に入れる（#170）。系統の数を返す。"""
    services, replace, junctions = defs
    cur.execute("""
        CREATE TEMP TABLE svc_def (key text PRIMARY KEY, operator text NOT NULL, name text NOT NULL, kind text NOT NULL,
                                   speed_kmh double precision NOT NULL, is_loop boolean NOT NULL);
        CREATE TEMP TABLE svc_def_line (key text NOT NULL, operator text NOT NULL, line_name text NOT NULL);
        CREATE TEMP TABLE svc_def_point (key text NOT NULL, seq integer NOT NULL, station_name text NOT NULL,
                                         is_stop boolean NOT NULL, display_name text);
        CREATE TEMP TABLE svc_replace (operator text NOT NULL, line_name text NOT NULL);
        CREATE TEMP TABLE svc_junction (ord integer PRIMARY KEY, operator_a text NOT NULL, line_a text NOT NULL,
                                        operator_b text NOT NULL, line_b text NOT NULL, near text NOT NULL);
    """)
    for ord_, ((op_a, line_a), (op_b, line_b), near) in enumerate(junctions, start=1):
        cur.execute("INSERT INTO svc_junction VALUES (%s, %s, %s, %s, %s, %s)", (ord_, op_a, line_a, op_b, line_b, near))
    for operator, line_name in replace:
        cur.execute("INSERT INTO svc_replace VALUES (%s, %s)", (operator, line_name))
    for svc in services:
        cur.execute("INSERT INTO svc_def VALUES (%s, %s, %s, %s, %s, %s)",
                    (svc["key"], svc["operator"], svc["name"], svc["kind"], svc["speed_kmh"], svc["loop"]))
        for operator, line_name in svc["lines"]:
            cur.execute("INSERT INTO svc_def_line VALUES (%s, %s, %s)", (svc["key"], operator, line_name))
        for seq, (station, is_stop) in enumerate(svc["points"], start=1):
            cur.execute("INSERT INTO svc_def_point VALUES (%s, %s, %s, %s, %s)",
                        (svc["key"], seq, station, is_stop, svc["stop_names"].get(station) if is_stop else None))
    return len(services)


def settings(cur, build_id: int, n02_year: int) -> None:
    for key, value in (("reach.build_id", build_id), ("reach.n02_year", n02_year),
                       ("reach.transfer_minutes", TRANSFER_MINUTES), ("reach.walk_cap_minutes", WALK_CAP_MINUTES),
                       ("reach.walk_meters_per_minute", WALK_METERS_PER_MINUTE),
                       ("reach.min_island_nodes", MIN_ISLAND_NODES), ("reach.snap_meters", SNAP_METERS)):
        cur.execute("SELECT set_config(%s, %s, false)", (key, str(value)))


def report_junction(cur) -> None:
    """駅でないところの路線のつなぎ目の一覧をログに出す（build_service.sql の junction_report。PR #174 のレビュー）。

    使うつなぎ目（定義の [[junction]] に書いたもの）と、書いていない候補のうち、どれかの系統が両方の路線を通るもの。
    候補は、同じ位置にある路線の違う点で、立体交差のこともある。書く前に、地図で本当に線路がつながるかを確かめる。
    """
    cur.execute("SELECT state, lines, near, latlon FROM junction_report ORDER BY state DESC, lines, latlon")
    rows = cur.fetchall()
    for state, label in (("used", "使う"), ("unlisted", "書いていない候補")):
        items = [f"{lines}（{near}、{latlon}）" for s, lines, near, latlon in rows if s == state]
        log(f"つなぎ目：{label} {len(items)} か所" + ("" if not items else "：" + "、".join(items)))


def report_walk(cur) -> None:
    """道に結べなかった駅と、本体以外のつながりに結んだ駅・着く先の数をログに出す（build_walk.sql の walk_report。#163）。"""
    cur.execute("SELECT item, n, examples FROM walk_report")
    r = {item: (n, examples) for item, n, examples in cur.fetchall()}
    n, examples = r["station_unsnapped"]
    log(f"歩き：道に結べなかった駅 {n} か所" + (f"（{examples}）" if examples else "")
        + "。範囲の端に並ぶときは、OSM の元の地域・切り出しの範囲が N02 の範囲を覆っているか確かめる")
    log(f"歩き：本体以外のつながりに結んだ駅 {r['station_off_main'][0]} か所、"
        f"着く先 {r['target_off_main'][0]} / {r['target_snapped'][0]} 件")


def build(args) -> None:
    services = read_services(args.services.expanduser())
    sources = {"n02": args.n02.name if args.n02.name != "UTF-8" else args.n02.parent.name,
               "n02_year": args.n02_year, "osm": args.osm.name, "bbox": args.bbox,
               "transfer_minutes": TRANSFER_MINUTES, "walk_cap_minutes": WALK_CAP_MINUTES,
               "walk_meters_per_minute": WALK_METERS_PER_MINUTE, "min_island_nodes": MIN_ISLAND_NODES,
               "snap_meters": SNAP_METERS,
               "services": sorted(svc["key"] for svc in services[0])}
    with psycopg.connect(DSN) as conn:
        # 場所の変更のトランザクションが終わるのを待ってから版の行を足す（DB V5 の reach.build_lock_key の説明）
        conn.execute("SELECT pg_advisory_xact_lock(reach.build_lock_key())")
        build_id = conn.execute(
            "INSERT INTO reach.build (version, status, sources) VALUES (%s, 'BUILDING', %s) RETURNING id",
            (args.version, json.dumps(sources, ensure_ascii=False))).fetchone()[0]
    log(f"版 {args.version}（id {build_id}）を作り始めた")
    try:
        with psycopg.connect(DSN) as conn, conn.cursor() as cur:
            settings(cur, build_id, args.n02_year)
            load_n02(cur, args.n02.expanduser(), args.bbox)
            cur.execute((SQL_DIR / "build_rail.sql").read_text(encoding="utf-8"))
            cur.execute("SELECT (SELECT count(*) FROM reach.node WHERE build_id = %s),"
                        " (SELECT count(*) FROM reach.edge WHERE build_id = %s),"
                        " (SELECT count(DISTINCT place_id) FROM n02_group)", (build_id, build_id))
            n_node, n_edge, n_place = cur.fetchone()
            log(f"鉄道：点 {n_node} 個、辺 {n_edge} 本、駅の場所 {n_place} 件")
            # 鉄道のグラフが空の版は READY にしない（--bbox の誤りなどで N02 が0件でも、activate すると前の版が使えなくなるため）
            cur.execute("SELECT count(*) FROM reach.station_node WHERE build_id = %s", (build_id,))
            n_station_node = cur.fetchone()[0]
            if n_edge == 0 or n_station_node == 0:
                raise RuntimeError(f"鉄道のグラフが空（辺 {n_edge} 本、グラフに結び付いた駅の点 {n_station_node} 個）。"
                                   "N02 のフォルダ・--bbox を確かめる")
            n_service = load_services(cur, services)
            cur.execute((SQL_DIR / "build_service.sql").read_text(encoding="utf-8"))
            cur.execute("SELECT (SELECT count(*) FROM reach.build_service WHERE build_id = %s),"
                        " (SELECT count(*) FROM reach.edge WHERE build_id = %s AND service_id IS NOT NULL),"
                        " (SELECT string_agg(l.name, '・' ORDER BY l.name) FROM reach.build_replaced_line r"
                        "   JOIN reach.line l ON l.id = r.line_id WHERE r.build_id = %s)", (build_id, build_id, build_id))
            n_svc, n_svc_edge, replaced = cur.fetchone()
            log(f"系統：{n_svc} / {n_service} 本、乗車の辺 {n_svc_edge} 本、置き換えた路線 {replaced or 'なし'}")
            report_junction(cur)
            load_osm_walk(cur, args.osm.expanduser())
            cur.execute((SQL_DIR / "build_walk.sql").read_text(encoding="utf-8"))
            cur.execute("SELECT count(*), count(DISTINCT place_id) FROM reach.place_walk WHERE build_id = %s", (build_id,))
            log("歩き：駅と場所（到達点）の組 {} 件、場所 {} 件".format(*cur.fetchone()))
            report_walk(cur)
            cur.execute("UPDATE reach.build SET status = 'READY' WHERE id = %s", (build_id,))
        log(f"版 {args.version} を READY にした（使うには activate）")
    except Exception:
        with psycopg.connect(DSN, autocommit=True) as conn:
            conn.execute("UPDATE reach.build SET status = 'FAILED' WHERE id = %s", (build_id,))
        log(f"版 {args.version} は FAILED にした（使っている版はそのまま）")
        raise


def activate(args) -> None:
    with psycopg.connect(DSN) as conn:
        row = conn.execute("SELECT id, status FROM reach.build WHERE version = %s FOR UPDATE", (args.version,)).fetchone()
        if row is None:
            sys.exit(f"版 {args.version} がない")
        if row[1] not in ("READY", "RETIRED"):
            sys.exit(f"版 {args.version} は {row[1]}。READY か RETIRED の版だけを使える")
        conn.execute("UPDATE reach.build SET status = 'RETIRED' WHERE status = 'ACTIVE'")
        conn.execute("UPDATE reach.build SET status = 'ACTIVE', activated_at = now() WHERE id = %s", (row[0],))
    log(f"版 {args.version} を ACTIVE にした")


def list_builds(args) -> None:
    with psycopg.connect(DSN) as conn:
        for version, status, created, activated in conn.execute(
                "SELECT version, status, created_at, activated_at FROM reach.build ORDER BY id"):
            print(f"{version}\t{status}\t作った {created:%Y-%m-%d %H:%M}\t"
                  f"使い始めた {activated:%Y-%m-%d %H:%M}" if activated else f"{version}\t{status}\t作った {created:%Y-%m-%d %H:%M}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build")
    b.add_argument("--version", required=True)
    b.add_argument("--n02", type=Path, required=True)
    b.add_argument("--n02-year", type=int, required=True)
    b.add_argument("--osm", type=Path, required=True)
    b.add_argument("--bbox", type=float, nargs=4, metavar=("WEST", "SOUTH", "EAST", "NORTH"))
    b.add_argument("--services", type=Path, default=SERVICES_DIR,
                   help="探索用の路線（運行系統）の定義のフォルダ（*.toml。既定は tools/reach/services）")
    b.set_defaults(func=build)
    a = sub.add_parser("activate")
    a.add_argument("--version", required=True)
    a.set_defaults(func=activate)
    sub.add_parser("list").set_defaults(func=list_builds)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
