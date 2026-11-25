"""寄り道の到達圏（F06）の版を作る・使う版を切り替える（#168。手順の決まりは #163）。

使い方：
  python reach_build.py build --version <版> --n02 <N02 の UTF-8 フォルダ> --n02-year <年度> --osm <歩ける道の .osm.pbf> [--bbox 西 南 東 北]
  python reach_build.py activate --version <版>
  python reach_build.py list

  例：python reach_build.py build --version 2026-11-19.1 --n02 ~/Downloads/N02-25_GML/UTF-8 --n02-year 2025 \\
        --osm ~/Downloads/kanto-261119-5339-highway.osm.pbf --bbox 139 35.3333 140 36

接続先は環境変数 EXDAY_DSN（既定：compose.yaml の DB、postgresql://exday:exday@127.0.0.1:5433/exday）。

- build：版（reach.build）を BUILDING で作り、N02 から駅の場所・計算の中の駅・路線と鉄道のグラフを、OSM の歩ける道から
  駅から場所への歩きを作って、READY にする。失敗したら FAILED にする（使っている版はそのまま）
- activate：READY（または前に使っていた RETIRED）の版を ACTIVE にし、それまでの ACTIVE を RETIRED にする（1つのトランザクション）。
  切り戻しは、前の版を activate する
- OSM の .osm.pbf は、highway の道を切り出したもの（ex-day/poc reach/osmwalk/extract_highway.py）。OSM から作った歩きの
  グラフは、この処理の一時の表だけで使い、DB には残さない（ODbL。DEC-0014 決定1）
"""
import argparse
import json
import math
import os
import sys
import time
from collections import Counter
from pathlib import Path

import osmium
import psycopg

DSN = os.environ.get("EXDAY_DSN", "postgresql://exday:exday@127.0.0.1:5433/exday")
SQL_DIR = Path(__file__).resolve().parent / "sql"

# 計算の決まりの値（F06「設定で持つ値」）。API の exday.detour.* と同じ値にする
TRANSFER_MINUTES = 10
WALK_CAP_MINUTES = 15
WALK_METERS_PER_MINUTE = 80
# 小さな島の閾値（点の数）と、道の点に結ぶ距離の上限（m）。poc #46 の暫定の値。確定は #163
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


def settings(cur, build_id: int, n02_year: int) -> None:
    for key, value in (("reach.build_id", build_id), ("reach.n02_year", n02_year),
                       ("reach.transfer_minutes", TRANSFER_MINUTES), ("reach.walk_cap_minutes", WALK_CAP_MINUTES),
                       ("reach.walk_meters_per_minute", WALK_METERS_PER_MINUTE),
                       ("reach.min_island_nodes", MIN_ISLAND_NODES), ("reach.snap_meters", SNAP_METERS)):
        cur.execute("SELECT set_config(%s, %s, false)", (key, str(value)))


def build(args) -> None:
    sources = {"n02": args.n02.name if args.n02.name != "UTF-8" else args.n02.parent.name,
               "n02_year": args.n02_year, "osm": args.osm.name, "bbox": args.bbox,
               "transfer_minutes": TRANSFER_MINUTES, "walk_cap_minutes": WALK_CAP_MINUTES,
               "min_island_nodes": MIN_ISLAND_NODES}
    with psycopg.connect(DSN, autocommit=True) as conn:
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
                        " (SELECT count(DISTINCT place_id) FROM n02_group)", (build_id, build_id, ))
            log("鉄道：点 {} 個、辺 {} 本、駅の場所 {} 件".format(*cur.fetchone()))
            load_osm_walk(cur, args.osm.expanduser())
            cur.execute((SQL_DIR / "build_walk.sql").read_text(encoding="utf-8"))
            cur.execute("SELECT count(*), count(DISTINCT place_id) FROM reach.place_walk WHERE build_id = %s", (build_id,))
            log("歩き：駅と場所（到達点）の組 {} 件、場所 {} 件".format(*cur.fetchone()))
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
    b.set_defaults(func=build)
    a = sub.add_parser("activate")
    a.add_argument("--version", required=True)
    a.set_defaults(func=activate)
    sub.add_parser("list").set_defaults(func=list_builds)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
