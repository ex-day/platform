-- ex-day：サンプルデータ（DB 設計 v0.2、Issue #135）
--
-- ■ 注意
-- 会話・Discovery・企画の内容は利用イメージであり、史実と異なる場合がある。
-- 場所の位置・形はおおよそで、オープンデータから取り込んだものではない（source_dataset = 'SAMPLE'）。
-- 坂の勾配などの推定の値は仮の値。ニックネームは架空のもの。
--
-- ■ 内容（論理設計 10節・MVP スコープ 16節のシナリオから、横浜の始める地域で）
-- 1. 桜木町からの散歩：場所を決めずに話す → 場所の共有の誘いで桜木町を確かめる → 返信で汽車道・山下公園が
--    採用される → Discovery が自動で成立する（由来＝会話）→ 運営が見直す（変更なし）。非公開にした返信と通報も入れる
-- 2. 小机城址：場所のページから話す（入口から場所が決まる）。運営が登録した見本の Discovery
-- 3. 鶴見の急坂番付（推定）：運営のお題の企画。推定の値の順の最初の並び → 企画そのものの会話の声
--    →「この坂も入れて」で項目を足し、順番を変える（企画の変更の出来事）。企画をきっかけにした会話（場所未定）
-- 4. 水車のあるそば屋：場所を探す問い（新しい場所の案の候補、「ここかも」の声）。TOP・場所のページの「探しています」
-- 5. ユーザーの企画（コース）
-- 6. 季節の違う Discovery（S01 の「いま、この時期」）と、成立を取り消した Discovery
--
-- ■ 仕組み
-- Flyway の繰り返し実行のマイグレーション（R__）。内容を変えると、次の migrate で全部消して入れ直す。
-- public_id は、キーから決まる値（md5 の先頭12文字）にする。本番では db/sample を読み込ませない。

TRUNCATE app_user, place, subject, decision, discovery, reach.line, reach.build RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------
-- 補助の関数（このスクリプトの中だけで使う）
-- ---------------------------------------------------------

CREATE FUNCTION pg_temp.sid(k text) RETURNS text LANGUAGE sql IMMUTABLE
    AS $$ SELECT substr(md5('ex-day-sample:' || k), 1, 12) $$;
CREATE FUNCTION pg_temp.geo(wkt text) RETURNS geography LANGUAGE sql IMMUTABLE
    AS $$ SELECT ST_GeogFromText('SRID=4326;' || wkt) $$;
-- 中心と半径（度）から四角いエリアを作る
CREATE FUNCTION pg_temp.box(lon float8, lat float8, d float8) RETURNS geography LANGUAGE sql IMMUTABLE
    AS $$ SELECT ST_MakeEnvelope(lon - d, lat - d * 0.8, lon + d, lat + d * 0.8, 4326)::geography $$;
CREATE FUNCTION pg_temp.uid(nick text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT user_id FROM user_profile WHERE nickname = nick $$;
CREATE FUNCTION pg_temp.plid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM place WHERE public_id = pg_temp.sid('place:' || k) $$;
CREATE FUNCTION pg_temp.cid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM conversation WHERE public_id = pg_temp.sid('conv:' || k) $$;
CREATE FUNCTION pg_temp.pid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM post WHERE public_id = pg_temp.sid('post:' || k) $$;
CREATE FUNCTION pg_temp.did(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM discovery WHERE public_id = pg_temp.sid('disc:' || k) $$;
CREATE FUNCTION pg_temp.prid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM project WHERE public_id = pg_temp.sid('proj:' || k) $$;
CREATE FUNCTION pg_temp.pcid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM place_candidate WHERE public_id = pg_temp.sid('cand:' || k) $$;
CREATE FUNCTION pg_temp.qid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM open_question WHERE public_id = pg_temp.sid('q:' || k) $$;
CREATE FUNCTION pg_temp.decid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM decision WHERE public_id = pg_temp.sid('dec:' || k) $$;

-- 判断の記録を1行足す
CREATE FUNCTION pg_temp.dec(k text, kind text, workflow text, actor_kind text, actor text, role text, at timestamptz)
    RETURNS bigint LANGUAGE sql AS $$
    INSERT INTO decision (public_id, kind, workflow, method, actor_kind, actor_user_id, actor_role,
                          rule_version, analysis_version, decided_at)
    VALUES (pg_temp.sid('dec:' || k), kind, workflow,
            CASE actor_kind WHEN 'SYSTEM' THEN 'AUTO' ELSE 'HUMAN' END,
            actor_kind, pg_temp.uid(actor), role,
            CASE actor_kind WHEN 'SYSTEM' THEN 'sample-rule-1' END,
            CASE workflow WHEN 'ANALYSIS' THEN 'sample-analysis-1' END,
            at)
    RETURNING id
$$;

-- ---------------------------------------------------------
-- User
-- ---------------------------------------------------------

INSERT INTO app_user (status) SELECT 'ACTIVE' FROM generate_series(1, 6);
INSERT INTO user_profile (user_id, nickname) VALUES
    (1, 'ex-day 運営'),       -- 運営のアカウント（運営かどうかは Cognito のグループで決まる）
    (2, '港の散歩人'),
    (3, 'はまっこ'),
    (4, '坂好き'),
    (5, '昔の鶴見っ子'),
    (6, 'そば好き');
INSERT INTO user_identity (user_id, issuer, subject, login_method)
SELECT id, 'https://cognito-idp.example/sample', 'sample-sub-' || id, CASE WHEN id % 2 = 0 THEN 'GOOGLE' ELSE 'EMAIL' END
  FROM app_user;

-- ---------------------------------------------------------
-- 場所（オープンデータの代わりのサンプル）
-- ---------------------------------------------------------

INSERT INTO place (public_id, name, spatial_type, geom, origin, source_dataset, source_record_id, source_license, imported_at) VALUES
    (pg_temp.sid('place:kanagawa'),   '神奈川県',       'AREA',  pg_temp.box(139.45, 35.40, 0.30),  'OPEN_DATA', 'SAMPLE', 'kanagawa',   'sample', now()),
    (pg_temp.sid('place:yokohama'),   '横浜市',         'AREA',  pg_temp.box(139.60, 35.45, 0.12),  'OPEN_DATA', 'SAMPLE', 'yokohama',   'sample', now()),
    (pg_temp.sid('place:naka'),       '横浜市中区',     'AREA',  pg_temp.box(139.645, 35.440, 0.020), 'OPEN_DATA', 'SAMPLE', 'naka',     'sample', now()),
    (pg_temp.sid('place:nishi'),      '横浜市西区',     'AREA',  pg_temp.box(139.620, 35.460, 0.015), 'OPEN_DATA', 'SAMPLE', 'nishi',    'sample', now()),
    (pg_temp.sid('place:tsurumi'),    '横浜市鶴見区',   'AREA',  pg_temp.box(139.675, 35.505, 0.025), 'OPEN_DATA', 'SAMPLE', 'tsurumi',  'sample', now()),
    (pg_temp.sid('place:kohoku'),     '横浜市港北区',   'AREA',  pg_temp.box(139.620, 35.520, 0.025), 'OPEN_DATA', 'SAMPLE', 'kohoku',   'sample', now()),
    (pg_temp.sid('place:kozukue-cho'),'小机町',         'AREA',  pg_temp.box(139.597, 35.510, 0.005), 'OPEN_DATA', 'SAMPLE', 'kozukue-cho', 'sample', now()),
    (pg_temp.sid('place:minatomirai'),'みなとみらい',   'AREA',  pg_temp.box(139.633, 35.457, 0.008), 'OPEN_DATA', 'SAMPLE', 'minatomirai', 'sample', now()),
    (pg_temp.sid('place:sakuragicho'),'桜木町駅',       'POINT', pg_temp.geo('POINT(139.6308 35.4509)'), 'OPEN_DATA', 'SAMPLE', 'sakuragicho', 'sample', now()),
    (pg_temp.sid('place:yokohama-st'),'横浜駅',         'POINT', pg_temp.geo('POINT(139.6221 35.4658)'), 'OPEN_DATA', 'SAMPLE', 'yokohama-st', 'sample', now()),
    (pg_temp.sid('place:tsurumi-st'), '鶴見駅',         'POINT', pg_temp.geo('POINT(139.6760 35.5076)'), 'OPEN_DATA', 'SAMPLE', 'tsurumi-st',  'sample', now()),
    (pg_temp.sid('place:kozukue-st'), '小机駅',         'POINT', pg_temp.geo('POINT(139.5987 35.5091)'), 'OPEN_DATA', 'SAMPLE', 'kozukue-st',  'sample', now()),
    (pg_temp.sid('place:ishikawacho'),'石川町駅',       'POINT', pg_temp.geo('POINT(139.6430 35.4386)'), 'OPEN_DATA', 'SAMPLE', 'ishikawacho', 'sample', now()),
    (pg_temp.sid('place:kishamichi'), '汽車道',         'ROUTE', pg_temp.geo('LINESTRING(139.6318 35.4518, 139.6345 35.4545, 139.6370 35.4552)'), 'OPEN_DATA', 'SAMPLE', 'kishamichi', 'sample', now()),
    (pg_temp.sid('place:yamashita'),  '山下公園',       'POINT', pg_temp.geo('POINT(139.6505 35.4462)'), 'OPEN_DATA', 'SAMPLE', 'yamashita',   'sample', now()),
    (pg_temp.sid('place:hikawamaru'), '氷川丸',         'POINT', pg_temp.geo('POINT(139.6527 35.4469)'), 'OPEN_DATA', 'SAMPLE', 'hikawamaru',  'sample', now()),
    (pg_temp.sid('place:ooka'),       '大岡川',         'ROUTE', pg_temp.geo('LINESTRING(139.6200 35.4300, 139.6290 35.4410, 139.6340 35.4470)'), 'OPEN_DATA', 'SAMPLE', 'ooka', 'sample', now()),
    (pg_temp.sid('place:kozukue-castle'), '小机城址',   'POINT', pg_temp.geo('POINT(139.5960 35.5130)'), 'OPEN_DATA', 'SAMPLE', 'kozukue-castle', 'sample', now()),
    (pg_temp.sid('place:slope-a'),    '鶴見の坂A',      'ROUTE', pg_temp.geo('LINESTRING(139.6700 35.5030, 139.6712 35.5042)'), 'OPEN_DATA', 'SAMPLE', 'slope-a', 'sample', now()),
    (pg_temp.sid('place:slope-b'),    '鶴見の坂B',      'ROUTE', pg_temp.geo('LINESTRING(139.6650 35.5100, 139.6661 35.5108)'), 'OPEN_DATA', 'SAMPLE', 'slope-b', 'sample', now()),
    (pg_temp.sid('place:slope-c'),    '鶴見の坂C',      'ROUTE', pg_temp.geo('LINESTRING(139.6800 35.5000, 139.6815 35.5006)'), 'OPEN_DATA', 'SAMPLE', 'slope-c', 'sample', now()),
    (pg_temp.sid('place:slope-d'),    '鶴見の坂D',      'ROUTE', pg_temp.geo('LINESTRING(139.6600 35.4980, 139.6608 35.4991)'), 'OPEN_DATA', 'SAMPLE', 'slope-d', 'sample', now());

-- 行き方の代表の駅
UPDATE place SET access_station_place_id = pg_temp.plid('sakuragicho') WHERE public_id IN (pg_temp.sid('place:kishamichi'), pg_temp.sid('place:minatomirai'));
UPDATE place SET access_station_place_id = pg_temp.plid('ishikawacho') WHERE public_id IN (pg_temp.sid('place:yamashita'), pg_temp.sid('place:hikawamaru'));
UPDATE place SET access_station_place_id = pg_temp.plid('kozukue-st') WHERE public_id = pg_temp.sid('place:kozukue-castle');
UPDATE place SET access_station_place_id = pg_temp.plid('tsurumi-st') WHERE public_id LIKE ANY (ARRAY[pg_temp.sid('place:slope-a'), pg_temp.sid('place:slope-b'), pg_temp.sid('place:slope-c'), pg_temp.sid('place:slope-d')]);

INSERT INTO place_name (place_id, name, kind) VALUES
    (pg_temp.plid('kishamichi'), '汽車道（旧臨港線）', 'ALIAS'),
    (pg_temp.plid('kozukue-castle'), '小机城', 'ALIAS');

INSERT INTO place_kind (place_id, kind)
SELECT pg_temp.plid(k), kind FROM (VALUES
    ('kanagawa', '都道府県'), ('yokohama', '市区町村'), ('naka', '市区町村'), ('nishi', '市区町村'),
    ('tsurumi', '市区町村'), ('kohoku', '市区町村'), ('kozukue-cho', '町丁・字'), ('minatomirai', '観光のエリア'),
    ('sakuragicho', '駅'), ('yokohama-st', '駅'), ('tsurumi-st', '駅'), ('kozukue-st', '駅'), ('ishikawacho', '駅'),
    ('kishamichi', '遊歩道'), ('yamashita', '公園'), ('hikawamaru', '船'), ('ooka', '川'),
    ('kozukue-castle', '城跡'), ('kozukue-castle', '公園'),
    ('slope-a', '坂'), ('slope-b', '坂'), ('slope-c', '坂'), ('slope-d', '坂')
) AS v (k, kind);

-- 計算の中の駅（路線ごとの駅。DB V3、F06「データの形」1）。路線は実在のものだが、位置はおおよそで、
-- N02 から取り込んだものではない。駅コード・グループコードは架空の値。事業者・路線の名前は N02 の書き方
-- （駅の候補では、DB V5 の呼び名の表で「JR東日本」「ブルーライン」等にして見せる）
INSERT INTO reach.line (operator, name) VALUES
    ('東日本旅客鉄道', '東海道線'), ('東日本旅客鉄道', '横須賀線'), ('東日本旅客鉄道', '根岸線'), ('東日本旅客鉄道', '横浜線'), ('東日本旅客鉄道', '鶴見線'),
    ('京浜急行電鉄', '本線'), ('東急電鉄', '東横線'), ('相模鉄道', '相鉄本線'), ('横浜市', '3号線'),
    ('横浜高速鉄道', 'みなとみらい21線');

INSERT INTO reach.station (place_id, line_id, name, geom)
SELECT pg_temp.plid(v.k), l.id, v.name, pg_temp.geo(v.wkt)
  FROM (VALUES
    ('sakuragicho', '東日本旅客鉄道', '根岸線', '桜木町', 'POINT(139.6310 35.4508)'),
    ('sakuragicho', '横浜市', '3号線', '桜木町', 'POINT(139.6303 35.4505)'),
    ('yokohama-st', '東日本旅客鉄道', '東海道線', '横浜', 'POINT(139.6222 35.4660)'),
    ('yokohama-st', '東日本旅客鉄道', '横須賀線', '横浜', 'POINT(139.6224 35.4659)'),
    ('yokohama-st', '東日本旅客鉄道', '根岸線', '横浜', 'POINT(139.6220 35.4657)'),
    ('yokohama-st', '京浜急行電鉄', '本線', '横浜', 'POINT(139.6226 35.4663)'),
    ('yokohama-st', '東急電鉄', '東横線', '横浜', 'POINT(139.6230 35.4655)'),
    ('yokohama-st', '相模鉄道', '相鉄本線', '横浜', 'POINT(139.6210 35.4662)'),
    ('yokohama-st', '横浜市', '3号線', '横浜', 'POINT(139.6218 35.4652)'),
    ('yokohama-st', '横浜高速鉄道', 'みなとみらい21線', '横浜', 'POINT(139.6229 35.4653)'),
    ('tsurumi-st', '東日本旅客鉄道', '東海道線', '鶴見', 'POINT(139.6760 35.5077)'),
    ('tsurumi-st', '東日本旅客鉄道', '鶴見線', '鶴見', 'POINT(139.6763 35.5079)'),
    ('yokohama-st', '東日本旅客鉄道', '横浜線', '横浜', 'POINT(139.6221 35.4658)'),
    ('kozukue-st', '東日本旅客鉄道', '横浜線', '小机', 'POINT(139.5987 35.5090)'),
    ('ishikawacho', '東日本旅客鉄道', '根岸線', '石川町', 'POINT(139.6430 35.4387)')
  ) AS v (k, operator, line, name, wkt)
  JOIN reach.line l ON l.operator = v.operator AND l.name = v.line;

INSERT INTO reach.station_source (station_id, dataset_year, station_code, group_code)
SELECT s.id, 2024, 'sample-' || s.id, 'sample-group-' || s.place_id FROM reach.station s;

INSERT INTO place_estimate (place_id, metric, value, unit, method, method_version, computed_at)
SELECT pg_temp.plid(k), 'SLOPE_GRADE', v, 'percent', 'elevation-x-road', 'sample-1', timestamptz '2026-03-20 09:00+09'
  FROM (VALUES ('slope-a', 14.2), ('slope-b', 12.8), ('slope-c', 10.5), ('slope-d', 13.1)) AS t (k, v);

-- 行政の階層と、エリアの包含
INSERT INTO place_relation (from_place_id, to_place_id, kind, origin)
SELECT pg_temp.plid(f), pg_temp.plid(t), kind, 'OPEN_DATA' FROM (VALUES
    ('kanagawa', 'yokohama', 'ADMINISTRATIVE'),
    ('yokohama', 'naka', 'ADMINISTRATIVE'), ('yokohama', 'nishi', 'ADMINISTRATIVE'),
    ('yokohama', 'tsurumi', 'ADMINISTRATIVE'), ('yokohama', 'kohoku', 'ADMINISTRATIVE'),
    ('kohoku', 'kozukue-cho', 'ADMINISTRATIVE'),
    ('minatomirai', 'kishamichi', 'AREA'), ('minatomirai', 'sakuragicho', 'AREA'),
    ('naka', 'yamashita', 'CONTAINS'), ('naka', 'hikawamaru', 'CONTAINS'),
    ('kozukue-cho', 'kozukue-castle', 'CONTAINS'), ('nishi', 'yokohama-st', 'CONTAINS'),
    ('tsurumi', 'slope-a', 'CONTAINS'), ('tsurumi', 'slope-b', 'CONTAINS'),
    ('tsurumi', 'slope-c', 'CONTAINS'), ('tsurumi', 'slope-d', 'CONTAINS')
) AS v (f, t, kind);

-- ---------------------------------------------------------
-- Subject
-- ---------------------------------------------------------

INSERT INTO subject (label, kind, granularity, status) VALUES
    ('散歩', 'ASPECT', NULL, 'ADOPTED'),
    ('歴史', 'ASPECT', NULL, 'ADOPTED'),
    ('季節の楽しみ', 'ASPECT', NULL, 'ADOPTED'),
    ('汽車道', 'TARGET', 'SPECIFIC', 'ADOPTED'),
    ('空堀', 'TARGET', 'GENERAL', 'ADOPTED'),
    ('桜', 'TARGET', 'GENERAL', 'ADOPTED'),
    ('バラ', 'TARGET', 'GENERAL', 'ADOPTED');

INSERT INTO user_interest (user_id, place_id) VALUES
    (pg_temp.uid('はまっこ'), pg_temp.plid('naka')),
    (pg_temp.uid('坂好き'), pg_temp.plid('tsurumi'));

-- ---------------------------------------------------------
-- 1. 桜木町からの散歩（場所を決めずに話す）
-- ---------------------------------------------------------

INSERT INTO conversation (public_id, starter_user_id, created_at)
VALUES (pg_temp.sid('conv:walk'), pg_temp.uid('港の散歩人'), '2026-04-05 10:00+09');

INSERT INTO post (public_id, conversation_id, user_id, body, moderation_status, posted_at) VALUES
    (pg_temp.sid('post:walk-1'), pg_temp.cid('walk'), pg_temp.uid('港の散歩人'),
     '桜木町から山下公園まで散歩したよ！海が見えて気持ちよかった', 'CHECKED', '2026-04-05 10:00+09'),
    (pg_temp.sid('post:walk-2'), pg_temp.cid('walk'), pg_temp.uid('はまっこ'),
     'いいですね。汽車道経由ですか？', 'CHECKED', '2026-04-05 12:10+09'),
    (pg_temp.sid('post:walk-3'), pg_temp.cid('walk'), pg_temp.uid('港の散歩人'),
     'そう、汽車道を通りました。線路の跡を歩けるのが楽しい', 'CHECKED', '2026-04-05 12:30+09'),
    (pg_temp.sid('post:walk-4'), pg_temp.cid('walk'), pg_temp.uid('昔の鶴見っ子'),
     '（規約に反する内容のため非公開）', 'CHECKED', '2026-04-05 13:00+09');
INSERT INTO post_reference (from_post_id, to_post_id) VALUES
    (pg_temp.pid('walk-2'), pg_temp.pid('walk-1')),
    (pg_temp.pid('walk-3'), pg_temp.pid('walk-2'));

-- 投稿の直後の照合（F05）で、桜木町と山下公園が場所の候補になる
INSERT INTO place_candidate (public_id, conversation_id, place_id, origin, created_at) VALUES
    (pg_temp.sid('cand:walk-sakuragicho'), pg_temp.cid('walk'), pg_temp.plid('sakuragicho'), 'AI', '2026-04-05 10:00+09'),
    (pg_temp.sid('cand:walk-yamashita'),   pg_temp.cid('walk'), pg_temp.plid('yamashita'),   'AI', '2026-04-05 10:00+09'),
    (pg_temp.sid('cand:walk-kishamichi'),  pg_temp.cid('walk'), pg_temp.plid('kishamichi'),  'HUMAN', '2026-04-05 12:10+09');
INSERT INTO place_candidate_basis (candidate_id, post_id, origin, added_at) VALUES
    (pg_temp.pcid('walk-sakuragicho'), pg_temp.pid('walk-1'), 'AI', '2026-04-05 10:00+09'),
    (pg_temp.pcid('walk-yamashita'),   pg_temp.pid('walk-1'), 'AI', '2026-04-05 10:00+09'),
    (pg_temp.pcid('walk-kishamichi'),  pg_temp.pid('walk-2'), 'HUMAN', '2026-04-05 12:10+09'),
    (pg_temp.pcid('walk-kishamichi'),  pg_temp.pid('walk-3'), 'HUMAN', '2026-04-05 12:30+09');
INSERT INTO place_candidate_state_change (candidate_id, to_status, actor_kind, reason, changed_at)
SELECT id, 'CONSIDERING', 'SYSTEM', '投稿の直後の照合・返信から', created_at FROM place_candidate WHERE conversation_id = pg_temp.cid('walk');
INSERT INTO place_candidate_voice (candidate_id, user_id, stance, post_id) VALUES
    (pg_temp.pcid('walk-kishamichi'), pg_temp.uid('はまっこ'), 'SUPPORT', pg_temp.pid('walk-2'));

-- 場所の共有の誘いで、投稿した人が桜木町をワンタップで確かめる（DEC-0023）
SELECT pg_temp.dec('walk-adopt-1', 'PLACE_ADOPT', 'USER_ACTION', 'USER', '港の散歩人', 'CONVERSATION_STARTER', '2026-04-05 10:01+09');
INSERT INTO place_adoption (public_id, candidate_id, conversation_id, place_id, role, seq, actor_kind, actor_user_id, created_decision_id, adopted_at)
VALUES (pg_temp.sid('adopt:walk-sakuragicho'), pg_temp.pcid('walk-sakuragicho'), pg_temp.cid('walk'), pg_temp.plid('sakuragicho'),
        'START', 1, 'CONVERSATION_STARTER', pg_temp.uid('港の散歩人'), pg_temp.decid('walk-adopt-1'), '2026-04-05 10:01+09');
INSERT INTO location_sharing_prompt (post_id, status, shown_at, answered_at, place_adoption_id)
VALUES (pg_temp.pid('walk-1'), 'ANSWERED', '2026-04-05 10:00+09', '2026-04-05 10:01+09',
        (SELECT id FROM place_adoption WHERE public_id = pg_temp.sid('adopt:walk-sakuragicho')));

-- 「汽車道経由？」「そう」で、会話を始めた人が汽車道と山下公園を採用する
SELECT pg_temp.dec('walk-adopt-2', 'PLACE_ADOPT', 'USER_ACTION', 'USER', '港の散歩人', 'CONVERSATION_STARTER', '2026-04-05 12:31+09');
INSERT INTO place_adoption (public_id, candidate_id, conversation_id, place_id, role, seq, actor_kind, actor_user_id, created_decision_id, adopted_at) VALUES
    (pg_temp.sid('adopt:walk-kishamichi'), pg_temp.pcid('walk-kishamichi'), pg_temp.cid('walk'), pg_temp.plid('kishamichi'),
     'VIA', 2, 'CONVERSATION_STARTER', pg_temp.uid('港の散歩人'), pg_temp.decid('walk-adopt-2'), '2026-04-05 12:31+09'),
    (pg_temp.sid('adopt:walk-yamashita'), pg_temp.pcid('walk-yamashita'), pg_temp.cid('walk'), pg_temp.plid('yamashita'),
     'END', 3, 'CONVERSATION_STARTER', pg_temp.uid('港の散歩人'), pg_temp.decid('walk-adopt-2'), '2026-04-05 12:31+09');
INSERT INTO decision_target (decision_id, role, place_adoption_id, after_state)
SELECT created_decision_id, 'RESULT', id, 'ADOPTED' FROM place_adoption WHERE conversation_id = pg_temp.cid('walk');
INSERT INTO decision_basis_post (decision_id, post_id) VALUES
    (pg_temp.decid('walk-adopt-1'), pg_temp.pid('walk-1')),
    (pg_temp.decid('walk-adopt-2'), pg_temp.pid('walk-2')),
    (pg_temp.decid('walk-adopt-2'), pg_temp.pid('walk-3'));

-- 会話の解析（F02）：主張 → わかってきたこと（価値）
INSERT INTO claim (post_id, content, analysis_version) VALUES
    (pg_temp.pid('walk-1'), '桜木町から山下公園まで歩ける', 'sample-analysis-1'),
    (pg_temp.pid('walk-3'), '汽車道は線路の跡を歩ける', 'sample-analysis-1');
-- Finding と最初の版は同じ文で作る（finding.current_version の外部キーは遅延の制約）
WITH f AS (
    INSERT INTO finding (public_id, conversation_id, kind, analysis_version, created_at, updated_at)
    VALUES (pg_temp.sid('finding:walk-value'), pg_temp.cid('walk'), 'VALUE', 'sample-analysis-1',
            '2026-04-06 03:00+09', '2026-04-06 03:00+09')
    RETURNING id
)
INSERT INTO finding_version (finding_id, version, content, unconfirmed, analysis_version, created_at)
SELECT id, 1, '桜木町から汽車道を通って山下公園まで、海を見ながら散歩できる', false, 'sample-analysis-1',
       timestamptz '2026-04-06 03:00+09' FROM f;
INSERT INTO finding_version_claim (finding_id, version, claim_id)
SELECT f.id, 1, c.id FROM finding f, claim c
 WHERE f.public_id = pg_temp.sid('finding:walk-value') AND c.post_id IN (pg_temp.pid('walk-1'), pg_temp.pid('walk-3'));

-- Discovery の候補 → 自動で成立する（由来＝会話）
INSERT INTO discovery_candidate (conversation_id, proposal, confidence, status, analysis_version)
VALUES (pg_temp.cid('walk'), '{"places":["桜木町駅","汽車道","山下公園"],"subjects":["散歩","汽車道"]}', 0.82, 'ACTIVE', 'sample-analysis-1');
INSERT INTO discovery_candidate_finding (candidate_id, finding_id)
SELECT dc.id, f.id FROM discovery_candidate dc, finding f
 WHERE dc.conversation_id = pg_temp.cid('walk') AND f.public_id = pg_temp.sid('finding:walk-value');

SELECT pg_temp.dec('walk-establish', 'ESTABLISH', 'ANALYSIS', 'SYSTEM', NULL, NULL, '2026-04-06 03:00+09');
INSERT INTO discovery (public_id, title, summary, established_at, created_at)
VALUES (pg_temp.sid('disc:walk'), '汽車道を通って、桜木町から山下公園へ海沿いの散歩',
        '桜木町駅から汽車道を渡り、海を見ながら山下公園まで歩ける。線路の跡を歩けるのも楽しい。',
        '2026-04-06 03:00+09', '2026-04-06 03:00+09');
UPDATE discovery_candidate SET status = 'ESTABLISHED', discovery_id = pg_temp.did('walk') WHERE conversation_id = pg_temp.cid('walk');
INSERT INTO discovery_place (discovery_id, place_id, role, seq, created_decision_id) VALUES
    (pg_temp.did('walk'), pg_temp.plid('sakuragicho'), 'START', 1, pg_temp.decid('walk-establish')),
    (pg_temp.did('walk'), pg_temp.plid('kishamichi'),  'VIA',   2, pg_temp.decid('walk-establish')),
    (pg_temp.did('walk'), pg_temp.plid('yamashita'),   'END',   3, pg_temp.decid('walk-establish'));
INSERT INTO discovery_value (public_id, discovery_id, description, created_decision_id)
VALUES (pg_temp.sid('value:walk'), pg_temp.did('walk'), '海を見ながら歩ける、ちょうどいい長さの散歩道', pg_temp.decid('walk-establish'));
INSERT INTO value_condition (value_id, axis, season)
SELECT id, 'SEASON', 'ALL_YEAR' FROM discovery_value WHERE public_id = pg_temp.sid('value:walk');
INSERT INTO discovery_origin (discovery_id, kind, conversation_id, decision_id)
VALUES (pg_temp.did('walk'), 'CONVERSATION', pg_temp.cid('walk'), pg_temp.decid('walk-establish'));
INSERT INTO subject_assignment (subject_id, discovery_id, assigned_by, created_decision_id)
SELECT s.id, pg_temp.did('walk'), 'SYSTEM', pg_temp.decid('walk-establish') FROM subject s WHERE s.label IN ('散歩', '汽車道');
INSERT INTO decision_target (decision_id, role, discovery_id, after_state)
VALUES (pg_temp.decid('walk-establish'), 'RESULT', pg_temp.did('walk'), 'ESTABLISHED');
INSERT INTO decision_target (decision_id, role, conversation_id)
VALUES (pg_temp.decid('walk-establish'), 'SUBJECT', pg_temp.cid('walk'));
INSERT INTO decision_basis_post (decision_id, post_id) VALUES
    (pg_temp.decid('walk-establish'), pg_temp.pid('walk-1')),
    (pg_temp.decid('walk-establish'), pg_temp.pid('walk-3'));
INSERT INTO decision_basis_snapshot (decision_id, finding_id, version, content)
SELECT pg_temp.decid('walk-establish'), f.id, 1, v.content
  FROM finding f JOIN finding_version v ON v.finding_id = f.id AND v.version = 1
 WHERE f.public_id = pg_temp.sid('finding:walk-value');

-- 運営が見直す（変更なし。F12）
SELECT pg_temp.dec('walk-review', 'REVIEWED', 'DISCOVERY_REVIEW', 'OPERATOR', 'ex-day 運営', NULL, '2026-04-07 10:00+09');
INSERT INTO decision_target (decision_id, role, discovery_id) VALUES (pg_temp.decid('walk-review'), 'SUBJECT', pg_temp.did('walk'));

-- 返信の1つが通報され、運営が非公開にする（F10）
INSERT INTO content_report (public_id, reporter_user_id, post_id, reason_code, description, status, received_at, resolved_at)
VALUES (pg_temp.sid('report:walk-4'), pg_temp.uid('はまっこ'), pg_temp.pid('walk-4'), 'RULE_VIOLATION', '関係のない宣伝です',
        'RESOLVED', '2026-04-05 13:10+09', '2026-04-05 15:00+09');
SELECT pg_temp.dec('walk-hide', 'VISIBILITY_CHANGE', 'REPORT_HANDLING', 'OPERATOR', 'ex-day 運営', NULL, '2026-04-05 15:00+09');
UPDATE decision SET reason_code = 'RULE_VIOLATION' WHERE id = pg_temp.decid('walk-hide');
UPDATE post SET visibility = 'HIDDEN' WHERE id = pg_temp.pid('walk-4');
INSERT INTO decision_target (decision_id, role, post_id, before_state, after_state)
VALUES (pg_temp.decid('walk-hide'), 'SUBJECT', pg_temp.pid('walk-4'), 'PUBLIC', 'HIDDEN');
INSERT INTO decision_target (decision_id, role, content_report_id, before_state, after_state)
SELECT pg_temp.decid('walk-hide'), 'SUBJECT', id, 'RECEIVED', 'RESOLVED' FROM content_report WHERE public_id = pg_temp.sid('report:walk-4');

-- リアクション
INSERT INTO reaction (user_id, post_id, kind) VALUES
    (pg_temp.uid('はまっこ'), pg_temp.pid('walk-1'), 'EMPATHY'),
    (pg_temp.uid('坂好き'), pg_temp.pid('walk-3'), 'EMPATHY');
INSERT INTO reaction (user_id, discovery_id, kind) VALUES
    (pg_temp.uid('はまっこ'), pg_temp.did('walk'), 'WANT_TO_GO'),
    (pg_temp.uid('そば好き'), pg_temp.did('walk'), 'AGREE');
INSERT INTO reaction (user_id, finding_id, finding_version, kind)
SELECT pg_temp.uid('坂好き'), id, 1, 'AGREE' FROM finding WHERE public_id = pg_temp.sid('finding:walk-value');

-- ---------------------------------------------------------
-- 2. 小机城址（場所のページから話す。運営が登録した見本の Discovery）
-- ---------------------------------------------------------

SELECT pg_temp.dec('kozukue-register', 'OPERATOR_REGISTER', 'OPERATOR_SQL', 'OPERATOR', 'ex-day 運営', NULL, '2026-03-01 10:00+09');
INSERT INTO discovery (public_id, title, summary, established_at, created_at)
VALUES (pg_temp.sid('disc:kozukue'), '小机城址で、中世の城の空堀を歩く',
        '小机城址の森の中に、深い空堀と土塁が残っている。春は竹林の緑と桜もきれい。',
        '2026-03-01 10:00+09', '2026-03-01 10:00+09');
INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id)
VALUES (pg_temp.did('kozukue'), pg_temp.plid('kozukue-castle'), 'EXPERIENCE', pg_temp.decid('kozukue-register'));
INSERT INTO discovery_value (public_id, discovery_id, description, created_decision_id) VALUES
    (pg_temp.sid('value:kozukue-moat'), pg_temp.did('kozukue'), '深い空堀と土塁を、歩いて間近に見られる', pg_temp.decid('kozukue-register')),
    (pg_temp.sid('value:kozukue-sakura'), pg_temp.did('kozukue'), '春は城跡の桜を楽しめる', pg_temp.decid('kozukue-register'));
INSERT INTO value_condition (value_id, axis, season)
SELECT id, 'SEASON', 'SPRING' FROM discovery_value WHERE public_id = pg_temp.sid('value:kozukue-sakura');
INSERT INTO discovery_origin (discovery_id, kind, decision_id)
VALUES (pg_temp.did('kozukue'), 'OPERATOR', pg_temp.decid('kozukue-register'));
INSERT INTO subject_assignment (subject_id, discovery_id, assigned_by, created_decision_id)
SELECT s.id, pg_temp.did('kozukue'), 'OPERATOR', pg_temp.decid('kozukue-register') FROM subject s WHERE s.label IN ('歴史', '空堀');
INSERT INTO decision_target (decision_id, role, discovery_id, after_state)
VALUES (pg_temp.decid('kozukue-register'), 'RESULT', pg_temp.did('kozukue'), 'ESTABLISHED');

-- 場所のページの「話す」から始めた会話：始まりの時点で、入口の場所の候補と採用を1つの判断の記録で作る
INSERT INTO conversation (public_id, entry_place_id, starter_user_id, created_at)
VALUES (pg_temp.sid('conv:kozukue'), pg_temp.plid('kozukue-castle'), pg_temp.uid('はまっこ'), '2026-04-10 09:00+09');
INSERT INTO post (public_id, conversation_id, user_id, body, moderation_status, posted_at)
VALUES (pg_temp.sid('post:kozukue-1'), pg_temp.cid('kozukue'), pg_temp.uid('はまっこ'),
        '空堀、思ったより深くてびっくりした。竹林の中を歩くのも気持ちいい', 'CHECKED', '2026-04-10 09:00+09');
INSERT INTO place_candidate (public_id, conversation_id, place_id, origin, created_at)
VALUES (pg_temp.sid('cand:kozukue-entry'), pg_temp.cid('kozukue'), pg_temp.plid('kozukue-castle'), 'ENTRY', '2026-04-10 09:00+09');
INSERT INTO place_candidate_basis (candidate_id, post_id, origin, added_at)
VALUES (pg_temp.pcid('kozukue-entry'), pg_temp.pid('kozukue-1'), 'ENTRY', '2026-04-10 09:00+09');
INSERT INTO place_candidate_state_change (candidate_id, to_status, actor_kind, actor_user_id, reason, changed_at)
VALUES (pg_temp.pcid('kozukue-entry'), 'CONSIDERING', 'USER', pg_temp.uid('はまっこ'), '場所のページから話した', '2026-04-10 09:00+09');
SELECT pg_temp.dec('kozukue-adopt', 'PLACE_ADOPT', 'USER_ACTION', 'USER', 'はまっこ', 'CONVERSATION_STARTER', '2026-04-10 09:00+09');
INSERT INTO place_adoption (public_id, candidate_id, conversation_id, place_id, role, actor_kind, actor_user_id, created_decision_id, adopted_at)
VALUES (pg_temp.sid('adopt:kozukue'), pg_temp.pcid('kozukue-entry'), pg_temp.cid('kozukue'), pg_temp.plid('kozukue-castle'),
        'TOPIC', 'CONVERSATION_STARTER', pg_temp.uid('はまっこ'), pg_temp.decid('kozukue-adopt'), '2026-04-10 09:00+09');
INSERT INTO decision_target (decision_id, role, place_adoption_id, after_state)
SELECT pg_temp.decid('kozukue-adopt'), 'RESULT', id, 'ADOPTED' FROM place_adoption WHERE public_id = pg_temp.sid('adopt:kozukue');
INSERT INTO decision_basis_post (decision_id, post_id) VALUES (pg_temp.decid('kozukue-adopt'), pg_temp.pid('kozukue-1'));

-- ---------------------------------------------------------
-- 3. 鶴見の急坂番付（推定）：運営のお題の企画
-- ---------------------------------------------------------

INSERT INTO project (public_id, creator_kind, creator_user_id, type, title, call_text, description, moderation_status, created_at, updated_at)
VALUES (pg_temp.sid('proj:slopes'), 'OPERATOR', pg_temp.uid('ex-day 運営'), 'RANKING', '鶴見の急坂番付（推定）',
        'みんなの知ってる急坂はどこ？', '標高のデータと道路から計算した勾配の順に並べた、推定の番付です。',
        'CHECKED', '2026-03-20 10:00+09', '2026-04-12 20:00+09');

-- 最初の並び（推定の値の順。F09）
INSERT INTO project_item (public_id, project_id, place_id, position, created_at) VALUES
    (pg_temp.sid('item:slopes-a'), pg_temp.prid('slopes'), pg_temp.plid('slope-a'), 1, '2026-03-20 10:00+09'),
    (pg_temp.sid('item:slopes-b'), pg_temp.prid('slopes'), pg_temp.plid('slope-b'), 2, '2026-03-20 10:00+09'),
    (pg_temp.sid('item:slopes-c'), pg_temp.prid('slopes'), pg_temp.plid('slope-c'), 3, '2026-03-20 10:00+09');
INSERT INTO project_change (public_id, project_id, kind, changed_by_user_id, changed_at)
VALUES (pg_temp.sid('change:slopes-created'), pg_temp.prid('slopes'), 'CREATED', pg_temp.uid('ex-day 運営'), '2026-03-20 10:00+09');
INSERT INTO project_change_item (change_id, place_id, kind, after_position)
SELECT (SELECT id FROM project_change WHERE public_id = pg_temp.sid('change:slopes-created')), pg_temp.plid(k), 'ADDED', pos
  FROM (VALUES ('slope-a', 1), ('slope-b', 2), ('slope-c', 3)) AS v (k, pos);

-- 企画そのものの会話（最初の発言のときに作る）
INSERT INTO conversation (public_id, entry_project_id, is_project_own, starter_user_id, created_at)
VALUES (pg_temp.sid('conv:slopes-own'), pg_temp.prid('slopes'), true, pg_temp.uid('坂好き'), '2026-04-12 18:00+09');
INSERT INTO post (public_id, conversation_id, user_id, body, moderation_status, posted_at) VALUES
    (pg_temp.sid('post:slopes-own-1'), pg_temp.cid('slopes-own'), pg_temp.uid('坂好き'),
     'この坂も入れてほしい。鶴見の坂D、自転車だと押して歩くしかない', 'CHECKED', '2026-04-12 18:00+09'),
    (pg_temp.sid('post:slopes-own-2'), pg_temp.cid('slopes-own'), pg_temp.uid('昔の鶴見っ子'),
     '坂D、たしかにきつい。坂Cより上だと思う', 'CHECKED', '2026-04-12 19:00+09');
INSERT INTO post_reference (from_post_id, to_post_id) VALUES (pg_temp.pid('slopes-own-2'), pg_temp.pid('slopes-own-1'));

-- 作った人が声を読み、自分で並びを直す（坂D を足して3位にし、坂C を4位にする）。変更の出来事が会話のタイムラインに出る
UPDATE project_item SET position = 4 WHERE public_id = pg_temp.sid('item:slopes-c');
INSERT INTO project_item (public_id, project_id, place_id, position, created_at)
VALUES (pg_temp.sid('item:slopes-d'), pg_temp.prid('slopes'), pg_temp.plid('slope-d'), 3, '2026-04-12 20:00+09');
INSERT INTO project_change (public_id, project_id, kind, changed_by_user_id, changed_at)
VALUES (pg_temp.sid('change:slopes-add-d'), pg_temp.prid('slopes'), 'UPDATED', pg_temp.uid('ex-day 運営'), '2026-04-12 20:00+09');
INSERT INTO project_change_item (change_id, place_id, kind, before_position, after_position)
SELECT (SELECT id FROM project_change WHERE public_id = pg_temp.sid('change:slopes-add-d')), pg_temp.plid(k), kind, b, a
  FROM (VALUES ('slope-d', 'ADDED', NULL::int, 3), ('slope-c', 'MOVED', 3, 4)) AS v (k, kind, b, a);

-- 企画をきっかけにした会話（入口＝この企画）。場所は決まっておらず、誘いは「あとで」
INSERT INTO conversation (public_id, entry_project_id, starter_user_id, created_at)
VALUES (pg_temp.sid('conv:slopes-snow'), pg_temp.prid('slopes'), pg_temp.uid('昔の鶴見っ子'), '2026-04-13 08:00+09');
INSERT INTO post (public_id, conversation_id, user_id, body, posted_at)
VALUES (pg_temp.sid('post:slopes-snow-1'), pg_temp.cid('slopes-snow'), pg_temp.uid('昔の鶴見っ子'),
        '雪の日、タクシーが坂を上れなくて、坂の下で降ろされたことがある', '2026-04-13 08:00+09');
INSERT INTO location_sharing_prompt (post_id, status, shown_at)
VALUES (pg_temp.pid('slopes-snow-1'), 'LATER', '2026-04-13 08:00+09');

-- ---------------------------------------------------------
-- 4. 水車のあるそば屋（場所を探す問い）
-- ---------------------------------------------------------

INSERT INTO conversation (public_id, starter_user_id, created_at)
VALUES (pg_temp.sid('conv:soba'), pg_temp.uid('そば好き'), '2026-04-15 21:00+09');
INSERT INTO post (public_id, conversation_id, user_id, body, posted_at) VALUES
    (pg_temp.sid('post:soba-1'), pg_temp.cid('soba'), pg_temp.uid('そば好き'),
     '昔、横浜駅の近くに水車のあるそば屋があったはず。どこだったか覚えている人いますか？', '2026-04-15 21:00+09'),
    (pg_temp.sid('post:soba-2'), pg_temp.cid('soba'), pg_temp.uid('はまっこ'),
     '駅の西口から少し歩いたところにあった気がします', '2026-04-15 22:00+09');
INSERT INTO post_reference (from_post_id, to_post_id) VALUES (pg_temp.pid('soba-2'), pg_temp.pid('soba-1'));
INSERT INTO location_sharing_prompt (post_id, status, shown_at, answered_at)
VALUES (pg_temp.pid('soba-1'), 'DECLINED', '2026-04-15 21:00+09', '2026-04-15 21:00+09');

INSERT INTO open_question (public_id, conversation_id, content, kind, asker_user_id, analysis_version)
VALUES (pg_temp.sid('q:soba'), pg_temp.cid('soba'), '水車のあるそば屋はどこにあったか', 'WHERE', pg_temp.uid('そば好き'), 'sample-analysis-1');
INSERT INTO open_question_post (question_id, post_id) VALUES (pg_temp.qid('soba'), pg_temp.pid('soba-1'));
INSERT INTO open_question_setting (question_id, keep_open, updated_by_user_id) VALUES (pg_temp.qid('soba'), true, pg_temp.uid('そば好き'));

-- 新しい場所の案の候補（採用されるまで Place を作らない）。位置を含む横浜駅のあたりで「探しています」に出る
INSERT INTO place_candidate (public_id, open_question_id, proposed_name, proposed_spatial_type, proposed_geom, proposed_kinds, origin, created_at)
VALUES (pg_temp.sid('cand:soba-west'), pg_temp.qid('soba'), '水車のあるそば屋（横浜駅西口のあたり）', 'POINT',
        pg_temp.geo('POINT(139.6195 35.4662)'), '{店}', 'HUMAN', '2026-04-15 22:00+09');
INSERT INTO place_candidate_basis (candidate_id, post_id, origin, added_at)
VALUES (pg_temp.pcid('soba-west'), pg_temp.pid('soba-2'), 'HUMAN', '2026-04-15 22:00+09');
INSERT INTO place_candidate_state_change (candidate_id, to_status, actor_kind, actor_user_id, reason, changed_at)
VALUES (pg_temp.pcid('soba-west'), 'CONSIDERING', 'USER', pg_temp.uid('はまっこ'), '返信で場所を指した', '2026-04-15 22:00+09');
INSERT INTO place_candidate_voice (candidate_id, user_id, stance) VALUES
    (pg_temp.pcid('soba-west'), pg_temp.uid('昔の鶴見っ子'), 'SUPPORT');
INSERT INTO reaction (user_id, open_question_id, kind) VALUES (pg_temp.uid('港の散歩人'), pg_temp.qid('soba'), 'CURIOUS');

-- ---------------------------------------------------------
-- 5. ユーザーの企画（コース）
-- ---------------------------------------------------------

INSERT INTO project (public_id, creator_kind, creator_user_id, type, title, call_text, description, created_at)
VALUES (pg_temp.sid('proj:harbor-course'), 'USER', pg_temp.uid('はまっこ'), 'COURSE', '港の見える散歩コース',
        'おすすめの寄り道があれば教えてください', '桜木町から海沿いを歩き、山下公園と氷川丸まで。', '2026-04-20 10:00+09');
INSERT INTO project_item (public_id, project_id, place_id, discovery_id, position, note, created_at) VALUES
    (pg_temp.sid('item:course-1'), pg_temp.prid('harbor-course'), NULL, pg_temp.did('walk'), 1, '汽車道を通るのがおすすめ', '2026-04-20 10:00+09'),
    (pg_temp.sid('item:course-2'), pg_temp.prid('harbor-course'), pg_temp.plid('hikawamaru'), NULL, 2, '', '2026-04-20 10:00+09');
INSERT INTO project_change (public_id, project_id, kind, changed_by_user_id, changed_at)
VALUES (pg_temp.sid('change:course-created'), pg_temp.prid('harbor-course'), 'CREATED', pg_temp.uid('はまっこ'), '2026-04-20 10:00+09');
INSERT INTO project_change_item (change_id, place_id, discovery_id, kind, after_position)
SELECT (SELECT id FROM project_change WHERE public_id = pg_temp.sid('change:course-created')), p, d, 'ADDED', pos
  FROM (VALUES (NULL::bigint, pg_temp.did('walk'), 1), (pg_temp.plid('hikawamaru'), NULL::bigint, 2)) AS v (p, d, pos);

-- ---------------------------------------------------------
-- 6. 季節の違う Discovery（運営の見本）と、成立を取り消した Discovery
-- ---------------------------------------------------------

SELECT pg_temp.dec('seasonal-register', 'OPERATOR_REGISTER', 'OPERATOR_SQL', 'OPERATOR', 'ex-day 運営', NULL, '2026-03-01 10:00+09');
INSERT INTO discovery (public_id, title, summary, established_at) VALUES
    (pg_temp.sid('disc:roses'), '山下公園のバラを、海と一緒に見る', '春と秋に、海沿いのバラ園でたくさんの品種が咲く。', '2026-03-01 10:00+09'),
    (pg_temp.sid('disc:ooka-sakura'), '大岡川の桜並木を、川沿いに歩く', '春、大岡川の両岸に桜が続き、川沿いを歩いて楽しめる。', '2026-03-01 10:00+09');
INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id) VALUES
    (pg_temp.did('roses'), pg_temp.plid('yamashita'), 'EXPERIENCE', pg_temp.decid('seasonal-register')),
    (pg_temp.did('ooka-sakura'), pg_temp.plid('ooka'), 'EXPERIENCE', pg_temp.decid('seasonal-register'));
INSERT INTO discovery_value (public_id, discovery_id, description, created_decision_id) VALUES
    (pg_temp.sid('value:roses'), pg_temp.did('roses'), '海を背景に、たくさんの品種のバラが見られる', pg_temp.decid('seasonal-register')),
    (pg_temp.sid('value:ooka-sakura'), pg_temp.did('ooka-sakura'), '川沿いに続く桜並木を歩ける', pg_temp.decid('seasonal-register'));
INSERT INTO value_condition (value_id, axis, season)
SELECT v.id, 'SEASON', s FROM discovery_value v,
       (VALUES ('value:roses', 'SPRING'), ('value:roses', 'AUTUMN'), ('value:ooka-sakura', 'SPRING')) AS t (k, s)
 WHERE v.public_id = pg_temp.sid(t.k);
INSERT INTO discovery_origin (discovery_id, kind, decision_id) VALUES
    (pg_temp.did('roses'), 'OPERATOR', pg_temp.decid('seasonal-register')),
    (pg_temp.did('ooka-sakura'), 'OPERATOR', pg_temp.decid('seasonal-register'));
INSERT INTO subject_assignment (subject_id, discovery_id, assigned_by, created_decision_id)
SELECT s.id, d, 'OPERATOR', pg_temp.decid('seasonal-register')
  FROM subject s, (VALUES ('バラ', pg_temp.did('roses')), ('桜', pg_temp.did('ooka-sakura')), ('季節の楽しみ', pg_temp.did('roses'))) AS t (label, d)
 WHERE s.label = t.label;
INSERT INTO decision_target (decision_id, role, discovery_id, after_state)
SELECT pg_temp.decid('seasonal-register'), 'RESULT', id, 'ESTABLISHED' FROM discovery
 WHERE public_id IN (pg_temp.sid('disc:roses'), pg_temp.sid('disc:ooka-sakura'));

-- 1つの投稿から自動で成立したが、運営が見直して成立を取り消した Discovery（F12。行は消さない）
SELECT pg_temp.dec('kozukue-post-establish', 'ESTABLISH', 'ANALYSIS', 'SYSTEM', NULL, NULL, '2026-04-11 03:00+09');
INSERT INTO discovery (public_id, title, summary, established_at, withdrawn_at)
VALUES (pg_temp.sid('disc:bamboo'), '小机の竹林を歩く', '（運営の見直しで成立を取り消した例）', '2026-04-11 03:00+09', '2026-04-11 12:00+09');
INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id)
VALUES (pg_temp.did('bamboo'), pg_temp.plid('kozukue-castle'), 'EXPERIENCE', pg_temp.decid('kozukue-post-establish'));
INSERT INTO discovery_value (public_id, discovery_id, description, created_decision_id)
VALUES (pg_temp.sid('value:bamboo'), pg_temp.did('bamboo'), '竹林の中を歩ける', pg_temp.decid('kozukue-post-establish'));
INSERT INTO discovery_origin (discovery_id, kind, post_id, decision_id)
VALUES (pg_temp.did('bamboo'), 'POST', pg_temp.pid('kozukue-1'), pg_temp.decid('kozukue-post-establish'));
INSERT INTO decision_target (decision_id, role, discovery_id, after_state)
VALUES (pg_temp.decid('kozukue-post-establish'), 'RESULT', pg_temp.did('bamboo'), 'ESTABLISHED');
INSERT INTO decision_target (decision_id, role, discovery_id)
VALUES (pg_temp.decid('kozukue-post-establish'), 'COMPARED', pg_temp.did('kozukue'));
SELECT pg_temp.dec('bamboo-withdraw', 'WITHDRAW_ESTABLISHMENT', 'DISCOVERY_REVIEW', 'OPERATOR', 'ex-day 運営', NULL, '2026-04-11 12:00+09');
UPDATE decision SET reason_text = '小机城址の Discovery の一部で、別の Discovery にするほどの違いがない' WHERE id = pg_temp.decid('bamboo-withdraw');
INSERT INTO decision_target (decision_id, role, discovery_id, before_state, after_state)
VALUES (pg_temp.decid('bamboo-withdraw'), 'SUBJECT', pg_temp.did('bamboo'), 'ESTABLISHED', 'WITHDRAWN');

-- ---------------------------------------------------------
-- 写し（会話の公開の Post の数・最終の時点）と、既読
-- ---------------------------------------------------------

UPDATE conversation c
   SET public_post_count = s.n, last_public_posted_at = s.last_at
  FROM (SELECT conversation_id, count(*) AS n, max(posted_at) AS last_at
          FROM post WHERE visibility = 'PUBLIC' GROUP BY conversation_id) AS s
 WHERE s.conversation_id = c.id;

INSERT INTO user_conversation_read (user_id, conversation_id, read_until)
VALUES (pg_temp.uid('港の散歩人'), pg_temp.cid('walk'), '2026-04-05 12:10+09');

-- 行き方と到達点（DB V3、F06「データの形」2・3）。小机城址の入口（推定）と、運営が公式の案内から登録した徒歩の行き方
SELECT pg_temp.dec('kozukue-access', 'OPERATOR_REGISTER', 'OPERATOR_SQL', 'OPERATOR', 'ex-day 運営', NULL, '2026-03-01 10:00+09');
INSERT INTO place_arrival_point (place_id, geom, kind, mode, source)
VALUES (pg_temp.plid('kozukue-castle'), pg_temp.geo('POINT(139.5968 35.5124)'), 'ESTIMATED', 'WALK', 'OPERATOR');
INSERT INTO place_access (place_id, station_place_id, mode, minutes, arrival_point_id, original_text, source,
                          verification, retrieved_on, verified_at, created_decision_id)
SELECT pg_temp.plid('kozukue-castle'), pg_temp.plid('kozukue-st'), 'WALK', 15, a.id, '小机駅から徒歩約15分',
       'OFFICIAL', 'HUMAN_VERIFIED', '2026-03-01', '2026-03-01 10:00+09', pg_temp.decid('kozukue-access')
  FROM place_arrival_point a WHERE a.place_id = pg_temp.plid('kozukue-castle');

-- 到達圏の版・鉄道のグラフ・駅から場所への歩き（DB V4、F06「データの形」5・6）。
-- 手で作った小さなグラフ（路線ごとに駅に1点、駅の間を直線の距離で結ぶ）で、N02・OSM から作ったものではない。
-- 距離はおおよそ（根岸線 横浜〜桜木町 2.0km・桜木町〜石川町 1.8km、東海道線 横浜〜鶴見 7.0km、横浜線 横浜〜小机 9.0km、
-- ブルーライン 横浜〜桜木町 2.0km）。表定速度は PoC の既定（在来線40・地下鉄30 km/h）。歩きの分も手で置いた目安
UPDATE reach.line SET service_type = 'subway', speed_kmh = 30 WHERE operator = '横浜市';
UPDATE reach.line SET service_type = 'subway', speed_kmh = 30 WHERE operator = '横浜高速鉄道';
INSERT INTO reach.build (version, status, sources, note, activated_at)
VALUES ('sample.1', 'ACTIVE', '{"n02": "sample", "osm": "sample"}', 'サンプルデータの手で作った版', '2026-03-01 00:00+09');

-- 路線ごとに、グラフに載せる駅（1駅1点）
CREATE TEMP TABLE sample_graph_station AS
SELECT s.id AS station_id, s.place_id, s.line_id, s.geom, p.public_id
  FROM reach.station s JOIN reach.line l ON l.id = s.line_id JOIN place p ON p.id = s.place_id
 WHERE (l.operator, l.name) IN (('東日本旅客鉄道', '根岸線'), ('東日本旅客鉄道', '東海道線'), ('東日本旅客鉄道', '横浜線'), ('横浜市', '3号線'));
INSERT INTO reach.node (build_id, line_id, geom)
SELECT (SELECT id FROM reach.build WHERE version = 'sample.1'), line_id, geom FROM sample_graph_station ORDER BY station_id;
INSERT INTO reach.station_node (build_id, station_id, node_id)
SELECT n.build_id, g.station_id, n.id FROM sample_graph_station g JOIN reach.node n ON n.line_id = g.line_id AND ST_Equals(n.geom::geometry, g.geom::geometry);

INSERT INTO reach.edge (build_id, kind, line_id, source, target, length_m, cost, reverse_cost)
SELECT a.build_id, 'RIDE', a.line_id, a.node_id, b.node_id, v.length_m,
       v.length_m / 1000.0 / l.speed_kmh * 60, v.length_m / 1000.0 / l.speed_kmh * 60
  FROM (VALUES
    ('東日本旅客鉄道', '根岸線', 'yokohama-st', 'sakuragicho', 2000),
    ('東日本旅客鉄道', '根岸線', 'sakuragicho', 'ishikawacho', 1800),
    ('東日本旅客鉄道', '東海道線', 'yokohama-st', 'tsurumi-st', 7000),
    ('東日本旅客鉄道', '横浜線', 'yokohama-st', 'kozukue-st', 9000),
    ('横浜市', '3号線', 'yokohama-st', 'sakuragicho', 2000)
  ) AS v (operator, line, k_from, k_to, length_m)
  JOIN reach.line l ON l.operator = v.operator AND l.name = v.line
  JOIN (SELECT sn.build_id, g.line_id, g.place_id, sn.node_id FROM sample_graph_station g JOIN reach.station_node sn USING (station_id)) a
    ON a.line_id = l.id AND a.place_id = pg_temp.plid(v.k_from)
  JOIN (SELECT g.line_id, g.place_id, sn.node_id FROM sample_graph_station g JOIN reach.station_node sn USING (station_id)) b
    ON b.line_id = l.id AND b.place_id = pg_temp.plid(v.k_to);

-- 乗換：同じ駅の場所の、路線の違う点どうし（10分。F06「設定で持つ値」と同じ値を版を作るときに入れる）
INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
SELECT a.build_id, 'TRANSFER', a.node_id, b.node_id, 10, 10
  FROM (SELECT sn.build_id, g.place_id, g.line_id, sn.node_id FROM sample_graph_station g JOIN reach.station_node sn USING (station_id)) a
  JOIN (SELECT g.place_id, g.line_id, sn.node_id FROM sample_graph_station g JOIN reach.station_node sn USING (station_id)) b
    ON a.place_id = b.place_id AND a.line_id < b.line_id;

-- 探索用の路線（運行系統。DB V6、F06「データの形」9）：京浜東北線 鶴見〜横浜〜桜木町〜石川町（東海道線・根岸線をまたいで乗換なし）。
-- 置き換えてよい路線は付けない（N02 の路線の辺も残す）。石川町では「根岸線」と見せる（停車駅ごとの名前）
INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh)
VALUES ((SELECT id FROM reach.build WHERE version = 'sample.1'), 'jr-keihin-tohoku', '東日本旅客鉄道', '京浜東北線', '普通', 40);
CREATE TEMP TABLE sample_service_stop AS
SELECT v.seq, g.station_id, g.place_id, g.geom, v.display_name
  FROM (VALUES (1, 'tsurumi-st', '東海道線', NULL), (2, 'yokohama-st', '東海道線', NULL),
               (3, 'sakuragicho', '根岸線', NULL), (4, 'ishikawacho', '根岸線', '根岸線')) AS v (seq, k, line, display_name)
  JOIN reach.line l ON l.operator = '東日本旅客鉄道' AND l.name = v.line
  JOIN sample_graph_station g ON g.line_id = l.id AND g.place_id = pg_temp.plid(v.k);
INSERT INTO reach.build_service_stop (build_service_id, seq, station_id, display_name)
SELECT (SELECT id FROM reach.build_service WHERE service_key = 'jr-keihin-tohoku'), seq, station_id, display_name FROM sample_service_stop;
INSERT INTO reach.node (build_id, service_id, geom)
SELECT (SELECT id FROM reach.build WHERE version = 'sample.1'), (SELECT id FROM reach.build_service WHERE service_key = 'jr-keihin-tohoku'), geom
  FROM sample_service_stop ORDER BY seq;
CREATE TEMP TABLE sample_service_node AS
SELECT st.seq, st.station_id, st.place_id, n.id AS node_id, n.build_id
  FROM sample_service_stop st JOIN reach.node n ON n.service_id IS NOT NULL AND ST_Equals(n.geom::geometry, st.geom::geometry);
INSERT INTO reach.station_node (build_id, station_id, node_id) SELECT build_id, station_id, node_id FROM sample_service_node;
INSERT INTO reach.edge (build_id, kind, service_id, source, target, length_m, cost, reverse_cost)
SELECT a.build_id, 'RIDE', (SELECT id FROM reach.build_service WHERE service_key = 'jr-keihin-tohoku'), a.node_id, b.node_id,
       v.length_m, v.length_m / 1000.0 / 40 * 60, v.length_m / 1000.0 / 40 * 60
  FROM (VALUES (1, 7000), (2, 2000), (3, 1800)) AS v (seq, length_m)
  JOIN sample_service_node a ON a.seq = v.seq JOIN sample_service_node b ON b.seq = v.seq + 1;
-- 乗換：系統の点と、同じ駅の場所の N02 の路線の点（10分）
INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
SELECT a.build_id, 'TRANSFER', a.node_id, sn.node_id, 10, 10
  FROM sample_service_node a
  JOIN sample_graph_station g ON g.place_id = a.place_id
  JOIN reach.station_node sn ON sn.station_id = g.station_id AND sn.build_id = a.build_id AND sn.node_id <> a.node_id;

-- 駅から場所への歩き（分。片道15分まで）。小机城址は推定の到達点までと、場所の代表点まで
-- この版で読んだ徒歩の行き方（小机城址の公式の行き方）
INSERT INTO reach.build_access (build_id, place_access_id)
SELECT (SELECT id FROM reach.build WHERE version = 'sample.1'), pa.id FROM place_access pa WHERE pa.status = 'ACTIVE' AND pa.mode = 'WALK';

INSERT INTO reach.place_walk (build_id, station_place_id, place_id, arrival_point_id, walk_min, method)
SELECT (SELECT id FROM reach.build WHERE version = 'sample.1'), pg_temp.plid(v.st), pg_temp.plid(v.k),
       CASE WHEN v.to_arrival THEN (SELECT a.id FROM place_arrival_point a WHERE a.place_id = pg_temp.plid(v.k)) END,
       v.walk_min, 'SAMPLE'
  FROM (VALUES
    ('sakuragicho', 'kishamichi', false, 2.0),
    ('sakuragicho', 'minatomirai', false, 3.0),
    ('yokohama-st', 'minatomirai', false, 8.0),
    ('sakuragicho', 'ooka', false, 5.0),
    ('ishikawacho', 'ooka', false, 12.0),
    ('ishikawacho', 'yamashita', false, 14.0),
    ('ishikawacho', 'hikawamaru', false, 15.0),
    ('kozukue-st', 'kozukue-castle', true, 12.0),
    ('kozukue-st', 'kozukue-castle', false, 9.0),
    ('tsurumi-st', 'slope-a', false, 8.0),
    ('tsurumi-st', 'slope-b', false, 13.0),
    ('tsurumi-st', 'slope-c', false, 12.0)
  ) AS v (st, k, to_arrival, walk_min);

-- 検索のための情報（F07 の中身が決まるまでの仮のもの）
INSERT INTO search_profile (discovery_id, search_text, subjects, seasons, analysis_version)
SELECT id, title || ' ' || summary, '{}', '{}', 'sample-analysis-1' FROM visible_discovery;
INSERT INTO search_profile (project_id, search_text, analysis_version)
SELECT id, title || ' ' || call_text || ' ' || description, 'sample-analysis-1' FROM visible_project;
