-- ex-day：閲覧用のサンプルデータ（Issue #89）
--
-- ■ 注意
-- 会話や Discovery の内容は利用イメージであり、史実と異なる場合がある。
-- 場所・歴史の一部は実在の情報を参考にしているが、事実の確認をしていないものを含む。
-- 写真や地図は、権利の問題を避けるため仮の画像にしている。ニックネームは架空のもの。
--
-- ■ 内容
-- - 桜木町〜山下公園のシナリオ（ex-day/poc#2）：海沿いの散歩（会話つき）、そこから派生した山下臨港線、
--   関係する氷川丸・赤い靴はいてた女の子像・汽車道
-- - 大岡川の桜（organization の README の漫画の流れ：疑問 → 経験が集まる → 写真・資料が集まる）
-- - 「今」を変えると S01 が変わるのを見るための、季節の違う Discovery（夏・秋・冬・通年）
--
-- ■ 仕組み
-- Flyway の繰り返し実行のマイグレーション（R__）。内容を変えると、次の migrate で全部消して入れ直す。
-- Discovery の public_id は URL に出るため固定の値にする。それ以外の public_id は、キーから決まる値にする。

TRUNCATE app_user, discovery, place RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------
-- 補助の関数（このスクリプトの中だけで使う）
-- ---------------------------------------------------------

CREATE FUNCTION pg_temp.sid(k text) RETURNS text LANGUAGE sql IMMUTABLE
    AS $$ SELECT substr(md5('ex-day-sample:' || k), 1, 12) $$;
CREATE FUNCTION pg_temp.uid(nick text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT user_id FROM user_profile WHERE nickname = nick $$;
CREATE FUNCTION pg_temp.did(pid text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM discovery WHERE public_id = pid $$;
CREATE FUNCTION pg_temp.pid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM post WHERE public_id = pg_temp.sid('post:' || k) $$;
CREATE FUNCTION pg_temp.vid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM discovery_value WHERE public_id = pg_temp.sid('value:' || k) $$;
CREATE FUNCTION pg_temp.fid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM finding WHERE public_id = pg_temp.sid('finding:' || k) $$;
CREATE FUNCTION pg_temp.mid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM media WHERE public_id = pg_temp.sid('media:' || k) $$;
-- 経度・緯度の組から geography を作る（'POINT(139.6 35.4)' のような WKT）
CREATE FUNCTION pg_temp.geo(wkt text) RETURNS geography LANGUAGE sql IMMUTABLE
    AS $$ SELECT ST_GeogFromText('SRID=4326;' || wkt) $$;

-- ---------------------------------------------------------
-- User（ニックネームは架空）
-- ---------------------------------------------------------

WITH nicknames(nickname) AS (
    VALUES ('ななしさん'), ('大岡川の近所'), ('さくら通り'), ('資料室の人'),
           ('夕景さんぽ'), ('はまっこ'), ('みなとの猫'), ('てつ子'), ('昭和の港'),
           ('はじめて横浜'), ('通りすがり'), ('よこはま歩き'), ('川べりの人'), ('港のカメラ')
), users AS (
    INSERT INTO app_user (status)
    SELECT 'ACTIVE' FROM nicknames
    RETURNING id
)
INSERT INTO user_profile (user_id, nickname)
SELECT u.id, n.nickname
FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM users) u
JOIN (SELECT nickname, row_number() OVER () AS rn FROM nicknames) n USING (rn);

-- ---------------------------------------------------------
-- Discovery
-- ---------------------------------------------------------

INSERT INTO discovery (public_id, title, summary, establishment_status, established_at) VALUES
    ('FMI7GE306Hqw', '大岡川の桜と風景の変化',
     '桜木町駅の近くから南へ続く大岡川の桜並木。いつから桜の名所になったのか、昔を知る人の記憶や写真が集まっている。',
     'ESTABLISHED', '2026-03-24T09:00:00+09:00'),
    ('pNhOuychaTbx', '桜木町から山下公園への海沿い散歩',
     '桜木町から汽車道、赤レンガ倉庫、象の鼻の横を通り、線路跡の高い遊歩道を歩いて山下公園へ向かう海沿いの散歩道。',
     'ESTABLISHED', '2026-03-12T09:00:00+09:00'),
    ('LCy5noRLHXj6', '山下臨港線の跡をたどる',
     '赤レンガ倉庫から山下公園へ続く高い遊歩道は、かつての貨物線（山下臨港線）の跡。横浜博覧会のころの思い出も集まっている。',
     'ESTABLISHED', '2026-03-14T09:00:00+09:00'),
    ('MIZcbwsEfmHc', '氷川丸',
     '山下公園に係留されている昭和初期の客船。船内を見学でき、夕方には公園から眺める姿も楽しめる。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('i4MiJ28PY7DV', '赤い靴はいてた女の子像',
     '山下公園にある、童謡「赤い靴」にちなんだ女の子の像。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('OglBhQeYvpZO', '汽車道',
     '桜木町駅の近くから新港地区へ、水面の上を渡る遊歩道。かつての鉄道の跡を歩ける。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('0cKuRVG95FPG', '山下公園のバラ',
     '山下公園の花壇では、春と秋にたくさんのバラが咲く。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('nRHOVxNIEiac', '山下公園通りの銀杏並木',
     '山下公園に沿った通りの銀杏並木。秋には黄色く色づいた並木道を歩ける。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('erme6N6CkQgI', '赤レンガ倉庫の冬の催し',
     '赤レンガ倉庫の広場では、冬に催しが開かれることがある。れんが造りの建物は一年中見られる。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('QWTFhGoAnUC5', '野毛山動物園',
     '桜木町駅から坂を上った野毛山公園の中にある動物園。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00'),
    ('7yYf55tZ2ja1', '横浜港の夏の花火',
     '夏の夜、横浜港で花火が打ち上げられることがある。',
     'ESTABLISHED', '2026-03-01T09:00:00+09:00');

-- ---------------------------------------------------------
-- Place（座標はおおよそ。経度 緯度 の順）
-- ---------------------------------------------------------

INSERT INTO place (name, spatial_type, geom) VALUES
    ('大岡川（桜木町駅〜蒔田）', 'ROUTE',
     pg_temp.geo('LINESTRING(139.6303 35.4507, 139.6282 35.4460, 139.6245 35.4400, 139.6190 35.4330, 139.6135 35.4290)')),
    ('桜木町〜山下公園（汽車道・赤レンガ倉庫・山下臨港線プロムナード）', 'ROUTE',
     pg_temp.geo('LINESTRING(139.6309 35.4509, 139.6330 35.4525, 139.6390 35.4555, 139.6425 35.4527, 139.6430 35.4505, 139.6470 35.4470, 139.6495 35.4460)')),
    ('山下臨港線プロムナード', 'ROUTE',
     pg_temp.geo('LINESTRING(139.6425 35.4515, 139.6445 35.4490, 139.6475 35.4468)')),
    ('山下公園（氷川丸）', 'POINT', pg_temp.geo('POINT(139.6503 35.4455)')),
    ('山下公園（赤い靴はいてた女の子像）', 'POINT', pg_temp.geo('POINT(139.6488 35.4466)')),
    ('汽車道', 'ROUTE', pg_temp.geo('LINESTRING(139.6330 35.4525, 139.6360 35.4540, 139.6390 35.4555)')),
    ('山下公園', 'AREA',
     pg_temp.geo('POLYGON((139.6470 35.4475, 139.6530 35.4450, 139.6520 35.4432, 139.6460 35.4458, 139.6470 35.4475))')),
    ('山下公園通り', 'ROUTE', pg_temp.geo('LINESTRING(139.6465 35.4460, 139.6520 35.4425)')),
    ('横浜赤レンガ倉庫', 'POINT', pg_temp.geo('POINT(139.6425 35.4527)')),
    ('野毛山公園', 'POINT', pg_temp.geo('POINT(139.6256 35.4468)')),
    ('臨港パーク', 'POINT', pg_temp.geo('POINT(139.6355 35.4620)'));

INSERT INTO discovery_place (discovery_id, place_id, is_primary)
SELECT pg_temp.did(d), (SELECT id FROM place WHERE name = p), true
FROM (VALUES
    ('FMI7GE306Hqw', '大岡川（桜木町駅〜蒔田）'),
    ('pNhOuychaTbx', '桜木町〜山下公園（汽車道・赤レンガ倉庫・山下臨港線プロムナード）'),
    ('LCy5noRLHXj6', '山下臨港線プロムナード'),
    ('MIZcbwsEfmHc', '山下公園（氷川丸）'),
    ('i4MiJ28PY7DV', '山下公園（赤い靴はいてた女の子像）'),
    ('OglBhQeYvpZO', '汽車道'),
    ('0cKuRVG95FPG', '山下公園'),
    ('nRHOVxNIEiac', '山下公園通り'),
    ('erme6N6CkQgI', '横浜赤レンガ倉庫'),
    ('QWTFhGoAnUC5', '野毛山公園'),
    ('7yYf55tZ2ja1', '臨港パーク')
) AS v(d, p);

-- ---------------------------------------------------------
-- Post（声）
-- key は post:<key> として public_id を決める。reply_to は返信先の key
-- ---------------------------------------------------------

CREATE TEMP TABLE sample_post (
    key text, discovery text, nickname text, reply_to text, posted_at timestamptz, body text
) ON COMMIT DROP;

INSERT INTO sample_post VALUES
    -- 大岡川の桜（疑問 → 経験が集まる → 写真・資料が集まる）
    ('ooka1', 'FMI7GE306Hqw', 'ななしさん',   NULL,    '2026-03-18T12:10:00+09:00', '大岡川って昔から桜の名所だったのかな？ #大岡川 #桜'),
    ('ooka2', 'FMI7GE306Hqw', '大岡川の近所', 'ooka1', '2026-03-18T19:40:00+09:00', '子どものころは、今ほど桜は多くなかった気がします。'),
    ('ooka3', 'FMI7GE306Hqw', 'さくら通り',   'ooka1', '2026-03-19T08:05:00+09:00', '春になると川沿いに屋台が出ていて、家族で歩いたのを覚えています。'),
    ('ooka4', 'FMI7GE306Hqw', '資料室の人',   'ooka2', '2026-03-21T15:30:00+09:00', '昔の川沿いの写真を見つけました。建物が低くて、空が広いですね。'),
    ('ooka5', 'FMI7GE306Hqw', 'ななしさん',   'ooka4', '2026-03-21T21:00:00+09:00', '今と比べると全然ちがう！ 同じ場所から桜を見てみたいです #桜'),
    ('ooka6', 'FMI7GE306Hqw', '夕景さんぽ',   NULL,    '2026-03-25T22:15:00+09:00', '夜の大岡川もきれいでした。川面に桜が映っていて #夜桜'),
    ('ooka7', 'FMI7GE306Hqw', '川べりの人',   'ooka6', '2026-03-26T07:30:00+09:00', '朝の人が少ない時間に歩くのもおすすめです。'),

    -- 桜木町から山下公園への海沿い散歩（ex-day/poc#2 のシナリオ P1〜P20）
    ('walk1',  'pNhOuychaTbx', '夕景さんぽ',   NULL,     '2026-03-10T17:20:00+09:00', '桜木町から山下公園まで散歩しました。海沿いがずっと気持ちよかった #散歩'),
    ('walk2',  'pNhOuychaTbx', 'はまっこ',     'walk1',  '2026-03-10T18:02:00+09:00', '汽車道は通りました？赤レンガ経由ですか？'),
    ('walk3',  'pNhOuychaTbx', '夕景さんぽ',   'walk2',  '2026-03-10T18:30:00+09:00', '汽車道→赤レンガ→象の鼻の横→線路跡の高い遊歩道→山下公園、の順です。線路跡の遊歩道が好きで'),
    ('walk4',  'pNhOuychaTbx', 'みなとの猫',   'walk3',  '2026-03-10T20:11:00+09:00', '赤レンガから山下公園までの線路跡、山下臨港線プロムナードですよね。高いところを歩けるのがいい'),
    ('walk5',  'pNhOuychaTbx', 'てつ子',       'walk3',  '2026-03-10T21:45:00+09:00', 'あの線路跡って、もともと貨物線ですよね'),
    ('walk6',  'pNhOuychaTbx', '昭和の港',     'walk5',  '2026-03-11T09:12:00+09:00', '横浜博覧会（1989年）のとき、その線路に旅客列車が走ってましたよ。レトロな気動車で、山下公園に臨時の駅があった'),
    ('walk7',  'pNhOuychaTbx', 'てつ子',       'walk6',  '2026-03-11T10:03:00+09:00', '列車の名前、汐風号と浜風号だった気がする（うろ覚え）'),
    ('walk8',  'pNhOuychaTbx', 'はまっこ',     'walk6',  '2026-03-11T12:40:00+09:00', '博覧会のころの写真あります'),
    ('walk9',  'pNhOuychaTbx', '昭和の港',     NULL,     '2026-03-11T19:20:00+09:00', '昔は山下公園の中を高架で線路が通ってたんですよね。今は撤去されて見晴らしがいい'),
    ('walk10', 'pNhOuychaTbx', 'みなとの猫',   'walk9',  '2026-03-12T08:15:00+09:00', '高架がなくなってから、氷川丸がよく見えるようになった気がする'),
    ('walk11', 'pNhOuychaTbx', '夕景さんぽ',   'walk10', '2026-03-12T18:50:00+09:00', '夕方の氷川丸、ライトアップがきれいでした #夕焼け'),
    ('walk12', 'pNhOuychaTbx', 'はじめて横浜', NULL,     '2026-03-12T20:05:00+09:00', '山下公園って、赤い靴に関連する何かなかったっけ？'),
    ('walk13', 'pNhOuychaTbx', 'はまっこ',     'walk12', '2026-03-12T20:30:00+09:00', '「赤い靴はいてた女の子」の像がありますよ'),
    ('walk14', 'pNhOuychaTbx', '昭和の港',     'walk13', '2026-03-12T21:02:00+09:00', '童謡の「赤い靴」にちなんだ像ですね。海の方を向いてます'),
    ('walk15', 'pNhOuychaTbx', 'はじめて横浜', 'walk13', '2026-03-12T21:40:00+09:00', 'ありがとうございます、今度見に行ってみます'),
    ('walk16', 'pNhOuychaTbx', 'てつ子',       'walk5',  '2026-03-13T10:25:00+09:00', 'ちなみに貨物線は山下埠頭の方まで延びてたはず'),
    ('walk17', 'pNhOuychaTbx', 'みなとの猫',   NULL,     '2026-03-13T13:00:00+09:00', '汽車道も同じ臨港線の跡なんですか？'),
    ('walk18', 'pNhOuychaTbx', 'てつ子',       'walk17', '2026-03-13T13:35:00+09:00', '汽車道は別の区間の跡で、先に遊歩道になったはず'),
    ('walk19', 'pNhOuychaTbx', '通りすがり',   NULL,     '2026-03-14T11:10:00+09:00', '高架が撤去されたのって2010年ごろでしたっけ'),
    ('walk20', 'pNhOuychaTbx', '通りすがり',   NULL,     '2026-03-14T11:12:00+09:00', '今日は暑かった'),

    -- 山下臨港線の跡をたどる（海沿いの散歩から派生した話の続き）
    ('rinko1', 'LCy5noRLHXj6', 'てつ子',       NULL,     '2026-03-14T20:00:00+09:00', '臨港線の話はこちらで。山下埠頭の方まで線路が延びていた跡、どこかに残っていないかな #臨港線'),
    ('rinko2', 'LCy5noRLHXj6', '昭和の港',     'rinko1', '2026-03-15T09:30:00+09:00', '博覧会の臨時の駅は、今の遊歩道のどのあたりだったんでしょうね'),
    ('rinko3', 'LCy5noRLHXj6', 'よこはま歩き', 'rinko1', '2026-03-16T18:45:00+09:00', '遊歩道の途中に、線路の名残みたいなところがありました');

INSERT INTO post (public_id, discovery_id, user_id, body, posted_at)
SELECT pg_temp.sid('post:' || key), pg_temp.did(discovery), pg_temp.uid(nickname), body, posted_at
FROM sample_post
ORDER BY posted_at;

INSERT INTO post_reference (from_post_id, to_post_id, kind)
SELECT pg_temp.pid(key), pg_temp.pid(reply_to), 'REPLY'
FROM sample_post
WHERE reply_to IS NOT NULL;
-- シナリオの P5 は P3 と P4 の両方に返信している
INSERT INTO post_reference (from_post_id, to_post_id, kind) VALUES (pg_temp.pid('walk5'), pg_temp.pid('walk4'), 'REPLY');

-- 本文中の #タグ（表示するタグ）
INSERT INTO post_tag (post_id, label)
SELECT DISTINCT pg_temp.pid(key), m[1]
FROM sample_post, regexp_matches(body, '#([^\s#]+)', 'g') AS m;

-- ---------------------------------------------------------
-- Media（仮の画像）
-- ---------------------------------------------------------

INSERT INTO media (public_id, discovery_id, post_id, kind, name, url, alt, source_status, source_type, source_name) VALUES
    (pg_temp.sid('media:ooka-sakura'), pg_temp.did('FMI7GE306Hqw'), NULL, 'IMAGE', 'ookagawa-sakura.svg',
     '/images/sample/placeholder-sakura.svg', '仮の画像（大岡川の桜並木）', 'SELF', NULL, NULL),
    (pg_temp.sid('media:ooka-old'), pg_temp.did('FMI7GE306Hqw'), pg_temp.pid('ooka4'), 'IMAGE', 'ookagawa-old.svg',
     '/images/sample/placeholder-old-photo.svg', '仮の画像（昔の大岡川の川沿い）', 'UNSET', NULL, NULL),
    (pg_temp.sid('media:walk-expo'), pg_temp.did('pNhOuychaTbx'), pg_temp.pid('walk8'), 'IMAGE', 'rinko-expo.svg',
     '/images/sample/placeholder-train.svg', '仮の画像（臨港線の気動車）', 'SELF', NULL, NULL),
    (pg_temp.sid('media:walk-sea'), pg_temp.did('pNhOuychaTbx'), NULL, 'IMAGE', 'walk-sea.svg',
     '/images/sample/placeholder-sea.svg', '仮の画像（海沿いの遊歩道）', 'SELF', NULL, NULL),
    (pg_temp.sid('media:hikawa'), pg_temp.did('MIZcbwsEfmHc'), NULL, 'IMAGE', 'hikawamaru.svg',
     '/images/sample/placeholder-ship.svg', '仮の画像（氷川丸）', 'SELF', NULL, NULL),
    (pg_temp.sid('media:rose'), pg_temp.did('0cKuRVG95FPG'), NULL, 'IMAGE', 'rose.svg',
     '/images/sample/placeholder-rose.svg', '仮の画像（バラの花壇）', 'SELF', NULL, NULL),
    (pg_temp.sid('media:icho'), pg_temp.did('nRHOVxNIEiac'), NULL, 'IMAGE', 'icho.svg',
     '/images/sample/placeholder-ginkgo.svg', '仮の画像（銀杏並木）', 'SELF', NULL, NULL);

-- ---------------------------------------------------------
-- Value・Condition（季節）・Hero のメッセージ
-- season が NULL の Value は、季節の条件を持たない
-- ---------------------------------------------------------

CREATE TEMP TABLE sample_value (
    key text, discovery text, subject text, summary text, body text, picture text,
    seasons text[], message text, participation text
) ON COMMIT DROP;

INSERT INTO sample_value VALUES
    ('ooka-sakura', 'FMI7GE306Hqw', '桜並木', '川沿いに続く桜並木を、水面越しに眺められる',
     '桜木町駅の近くから南へ、川の両岸に桜並木が続く。橋の上から眺めると、水面に向かって枝が伸びる様子がよく見える。',
     'ooka-sakura', ARRAY['SPRING'], '桜の季節です。川沿いの桜並木を歩いてみませんか', NULL),
    ('ooka-yozakura', 'FMI7GE306Hqw', '夜桜', '夜は川面に映る桜も楽しめる',
     '夜になると、川沿いの明かりに照らされた桜が川面に映る。',
     NULL, ARRAY['SPRING'], '夜の大岡川では、川面に映る桜も楽しめます', NULL),
    ('ooka-walk', 'FMI7GE306Hqw', '川沿いの散歩', '一年中、川沿いの遊歩道を歩ける',
     '桜の季節以外も、川沿いの遊歩道を歩いて昔の風景との違いを探せる。',
     NULL, ARRAY['ALL_YEAR'], '大岡川の川沿いを歩いて、昔の風景との違いを探してみませんか',
     '昔を知っている方、写真を持っている方、一緒に残しませんか？'),

    ('walk-sea', 'pNhOuychaTbx', '海沿いの散歩', '海を見ながら、桜木町から山下公園まで歩ける',
     '汽車道、赤レンガ倉庫、象の鼻の横を通り、海沿いを山下公園まで歩ける。',
     'walk-sea', ARRAY['ALL_YEAR'], '海沿いを歩いて、山下公園まで行ってみませんか', NULL),
    ('walk-promenade', 'pNhOuychaTbx', '線路跡の遊歩道', '線路跡の高い遊歩道を歩ける',
     '赤レンガ倉庫から山下公園へは、線路跡の高い遊歩道を通って歩ける。',
     NULL, NULL, '線路跡の高い遊歩道から、港を見下ろしながら歩けます', NULL),

    ('rinko-trace', 'LCy5noRLHXj6', '鉄道の跡', '貨物線の跡を、高い遊歩道として歩ける',
     'かつての貨物線の高架が、遊歩道として残されている。',
     NULL, NULL, '貨物線の跡を歩きながら、昔の港の姿を想像してみませんか',
     '横浜博覧会のころを覚えている方、一緒に話しませんか？'),

    ('hikawa-tour', 'MIZcbwsEfmHc', '船内見学', '昭和初期の客船の中を見学できる',
     '客室や食堂など、当時の客船の内装を見て回れる。',
     'hikawa', ARRAY['ALL_YEAR'], '昭和初期の客船の中を見学してみませんか', NULL),
    ('hikawa-evening', 'MIZcbwsEfmHc', '夕方の景色', '夕方、公園から氷川丸を眺められる',
     '夕方になると、明かりのついた氷川丸を山下公園から眺められる。',
     NULL, NULL, '夕方の山下公園から、氷川丸を眺めてみませんか', NULL),

    ('akaikutsu-statue', 'i4MiJ28PY7DV', '像', '童謡「赤い靴」にちなんだ像を見られる',
     '山下公園の中に、海の方を向いた女の子の像がある。',
     NULL, ARRAY['ALL_YEAR'], '山下公園で、童謡にちなんだ像を探してみませんか', NULL),

    ('kishamichi-night', 'OglBhQeYvpZO', '夜景', '水面の上を歩きながら、みなとみらいの夜景を眺められる',
     '汽車道は水面の上を渡る遊歩道で、夜はみなとみらいの明かりが水面に映る。',
     NULL, NULL, '水面の上を歩きながら、夜景を眺めてみませんか', NULL),

    ('rose-spring', '0cKuRVG95FPG', '春のバラ', '春、花壇いっぱいに咲くバラを見られる',
     '春の終わりごろ、山下公園の花壇にたくさんのバラが咲く。',
     'rose', ARRAY['SPRING'], 'バラの季節です。山下公園の花壇を見に行ってみませんか', NULL),
    ('rose-autumn', '0cKuRVG95FPG', '秋のバラ', '秋にもう一度咲くバラを見られる',
     '秋にも、春より落ち着いた色合いのバラが咲く。',
     'rose', ARRAY['AUTUMN'], '秋のバラの季節です。山下公園の花壇を歩いてみませんか', NULL),

    ('icho-yellow', 'nRHOVxNIEiac', '黄葉', '黄色く色づいた銀杏並木を歩ける',
     '秋の終わりごろ、通りの銀杏並木が黄色く色づく。',
     'icho', ARRAY['AUTUMN'], '銀杏並木が色づく季節です。山下公園通りを歩いてみませんか', NULL),

    ('akarenga-winter', 'erme6N6CkQgI', '冬の催し', '冬の広場の催しを楽しめる',
     '冬には、赤レンガ倉庫の広場で催しが開かれることがある。',
     NULL, ARRAY['WINTER'], '冬の赤レンガ倉庫で、広場の催しをのぞいてみませんか', NULL),
    ('akarenga-building', 'erme6N6CkQgI', 'れんが造りの建物', 'れんが造りの倉庫の建物を見られる',
     '明治・大正期に建てられたれんが造りの倉庫の外観を見られる。',
     NULL, ARRAY['ALL_YEAR'], 'れんが造りの倉庫の建物を見に行ってみませんか', NULL),

    ('nogeyama-zoo', 'QWTFhGoAnUC5', '動物園', '桜木町駅から歩いて行ける動物園で、動物を見られる',
     '野毛山公園の中にあり、身近な動物から珍しい動物まで見られる。',
     NULL, ARRAY['ALL_YEAR'], '桜木町から坂を上って、動物園に寄ってみませんか', NULL),

    ('hanabi-summer', '7yYf55tZ2ja1', '花火', '夏の夜、港で花火が打ち上げられることがある',
     '夏には、港の近くで花火大会が開かれることがある。',
     NULL, ARRAY['SUMMER'], '花火の季節です。夏の夜の港に出かけてみませんか', NULL);

INSERT INTO discovery_value (public_id, discovery_id, subject_label, summary, body, picture_media_id)
SELECT pg_temp.sid('value:' || key), pg_temp.did(discovery), subject, summary, body,
       CASE WHEN picture IS NULL THEN NULL ELSE pg_temp.mid(picture) END
FROM sample_value;

INSERT INTO value_condition (value_id, axis, season)
SELECT pg_temp.vid(key), 'SEASON', s
FROM sample_value, unnest(seasons) AS s;

INSERT INTO value_presentation (value_id, message, participation_message, generated_by)
SELECT pg_temp.vid(key), message, participation, 'SAMPLE'
FROM sample_value;

-- ---------------------------------------------------------
-- Finding（わかってきたこと）
-- ---------------------------------------------------------

CREATE TEMP TABLE sample_finding (
    key text, discovery text, kind text, text text, backed_by text, sources text[], display_order int
) ON COMMIT DROP;

INSERT INTO sample_finding VALUES
    ('ooka-night',   'FMI7GE306Hqw', 'VALUE',  '夜は川面に映る桜も楽しめるらしい', NULL, ARRAY['ooka6'], 1),
    ('ooka-morning', 'FMI7GE306Hqw', 'VALUE',  '朝の人が少ない時間に歩くのもよいらしい', NULL, ARRAY['ooka7'], 2),
    ('ooka-fewer',   'FMI7GE306Hqw', 'THEORY', '昔は今より桜の本数が少なかったのでは', NULL, ARRAY['ooka2'], 3),
    ('ooka-stalls',  'FMI7GE306Hqw', 'THEORY', '春には川沿いに屋台が出ていたらしい', NULL, ARRAY['ooka3'], 4),
    ('ooka-sky',     'FMI7GE306Hqw', 'THEORY', '昔の川沿いは、今より建物が低く空が広かった', '提供された昔の写真', ARRAY['ooka4'], 5),

    ('walk-sea',       'pNhOuychaTbx', 'VALUE',  '海沿いの散歩が気持ちいい', NULL, ARRAY['walk1'], 1),
    ('walk-promenade', 'pNhOuychaTbx', 'VALUE',  '線路跡の高い遊歩道を歩ける', NULL, ARRAY['walk3', 'walk4'], 2),
    ('walk-view',      'pNhOuychaTbx', 'THEORY', '山下公園の中の高架が撤去されて、氷川丸がよく見えるようになったのでは', NULL, ARRAY['walk9', 'walk10'], 3),

    ('rinko-freight', 'LCy5noRLHXj6', 'THEORY', '線路跡は、もともと貨物線（山下臨港線）だった', 'Wikipedia「山下臨港線プロムナード」', ARRAY['walk5', 'walk16'], 1),
    ('rinko-expo',    'LCy5noRLHXj6', 'THEORY', '横浜博覧会（1989年）のとき旅客列車が走り、山下公園に臨時の駅があった', NULL, ARRAY['walk6'], 2),
    ('rinko-names',   'LCy5noRLHXj6', 'THEORY', '列車の名前は汐風号・浜風号だったらしい', NULL, ARRAY['walk7'], 3),

    ('hikawa-light', 'MIZcbwsEfmHc', 'VALUE', '夕方の氷川丸のライトアップがきれいらしい', NULL, ARRAY['walk11'], 1),

    ('akaikutsu-sea', 'i4MiJ28PY7DV', 'THEORY', '像は海の方を向いているらしい', NULL, ARRAY['walk14'], 1),

    ('kishamichi-section', 'OglBhQeYvpZO', 'THEORY', '汽車道は臨港線とは別の区間の跡で、先に遊歩道になったらしい', NULL, ARRAY['walk18'], 1);

INSERT INTO finding (public_id, discovery_id, kind, text, backed_by, display_order)
SELECT pg_temp.sid('finding:' || key), pg_temp.did(discovery), kind, text, backed_by, display_order
FROM sample_finding;

INSERT INTO finding_source_post (finding_id, post_id)
SELECT pg_temp.fid(key), pg_temp.pid(s)
FROM sample_finding, unnest(sources) AS s;

-- ---------------------------------------------------------
-- DiscoveryRelation と派生の見立て
-- ---------------------------------------------------------

INSERT INTO discovery_relation (from_discovery_id, to_discovery_id, relation_type, description, origin_post_id) VALUES
    (pg_temp.did('pNhOuychaTbx'), pg_temp.did('LCy5noRLHXj6'), 'DERIVED', '線路跡の話から、貨物線の歴史の話が広がった', pg_temp.pid('walk8')),
    (pg_temp.did('pNhOuychaTbx'), pg_temp.did('MIZcbwsEfmHc'), 'RELATED', '散歩の途中で氷川丸の景色の話が出た', NULL),
    (pg_temp.did('pNhOuychaTbx'), pg_temp.did('i4MiJ28PY7DV'), 'RELATED', '散歩の途中で赤い靴の像の話が出た', NULL),
    (pg_temp.did('pNhOuychaTbx'), pg_temp.did('OglBhQeYvpZO'), 'RELATED', '散歩のルートに汽車道が含まれる', NULL),
    (pg_temp.did('LCy5noRLHXj6'), pg_temp.did('OglBhQeYvpZO'), 'RELATED', 'どちらも港の鉄道の跡', NULL),
    (pg_temp.did('0cKuRVG95FPG'), pg_temp.did('nRHOVxNIEiac'), 'RELATED', 'どちらも山下公園のまわりで季節を感じられる', NULL);

-- 派生が確定した後に、元の会話で続いた派生の話題の声
INSERT INTO post_continuation (post_id, discovery_id) VALUES
    (pg_temp.pid('walk16'), pg_temp.did('LCy5noRLHXj6')),
    (pg_temp.pid('walk19'), pg_temp.did('LCy5noRLHXj6'));

-- ---------------------------------------------------------
-- Reaction（数を見せるためのサンプル。ユーザーを先頭から n 人使う）
-- ---------------------------------------------------------

INSERT INTO discovery_reaction (discovery_id, user_id, reaction_type)
SELECT pg_temp.did(d), u.id, t
FROM (VALUES
    ('FMI7GE306Hqw', 'LIKE', 4), ('FMI7GE306Hqw', 'SURPRISED', 1), ('FMI7GE306Hqw', 'LOVE', 6),
    ('pNhOuychaTbx', 'LIKE', 5), ('pNhOuychaTbx', 'LOVE', 3),
    ('LCy5noRLHXj6', 'LIKE', 2), ('LCy5noRLHXj6', 'SURPRISED', 3),
    ('MIZcbwsEfmHc', 'LIKE', 3), ('0cKuRVG95FPG', 'LOVE', 2)
) AS v(d, t, n)
CROSS JOIN LATERAL (SELECT id FROM app_user ORDER BY id LIMIT v.n) AS u;

INSERT INTO post_reaction (post_id, user_id)
SELECT pg_temp.pid(k), u.id
FROM (VALUES ('ooka1', 3), ('ooka4', 5), ('ooka6', 2), ('walk1', 2), ('walk3', 3), ('walk6', 4), ('walk12', 1)) AS v(k, n)
CROSS JOIN LATERAL (SELECT id FROM app_user ORDER BY id DESC LIMIT v.n) AS u;

INSERT INTO finding_reaction (finding_id, user_id, reaction_type)
SELECT pg_temp.fid(k), u.id, t
FROM (VALUES
    ('ooka-night', 'UNDERSTAND', 3), ('ooka-night', 'WANT_TO_GO', 5),
    ('ooka-fewer', 'MAYBE', 2), ('ooka-stalls', 'MAYBE', 1),
    ('walk-sea', 'UNDERSTAND', 4), ('walk-promenade', 'WANT_TO_GO', 3),
    ('walk-view', 'MAYBE', 2), ('rinko-expo', 'MAYBE', 3), ('rinko-names', 'MAYBE', 1),
    ('hikawa-light', 'WANT_TO_GO', 2)
) AS v(k, t, n)
CROSS JOIN LATERAL (SELECT id FROM app_user ORDER BY id LIMIT v.n) AS u;
