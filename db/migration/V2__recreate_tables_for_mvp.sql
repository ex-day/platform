-- ex-day：MVP の表（DB 設計 v0.2、Issue #135）
--
-- docs/ex-day_db_design.md（v0.2）の表・ビュー・関数を作る。V1（閲覧用。v0.1 の論理設計で
-- Discovery を器とする形）の表は、すべて消して作り直す（2026-11-17 の人の判断、PR #150）。
-- V1 のファイルは書き換えない。
--
-- 方針（設計書 1・2節）
-- - 主キーは内部の連番（bigint）。画面・URL・API には出さない。
-- - 画面・URL・API に出す識別子は public_id（英数字12文字。exday_public_id() が既定値）。
-- - 区分値は text と CHECK。足せる形にする区分（Discovery と場所の関係の役割、場所の種類）は CHECK を付けない。
-- - 「いずれか1種類を指す」関係は、種類ごとの外部キーの列と num_nonnulls() の CHECK（設計書 2.2節）。
-- - 判断の対象・採用した構造・判断の記録の行は消さない。外部キーに ON DELETE CASCADE を付けない。
-- - 一般のユーザー向けの読み取りは visible_* のビューを通す（設計書 2.5節）。
--   ビューはアクセスの制御そのものではない。運営・本人の読み取りの権限は API で確かめる。

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =========================================================
-- V1 の表を消す
-- =========================================================

DROP TABLE IF EXISTS
    finding_reaction, post_reaction, discovery_reaction, post_continuation,
    discovery_relation, finding_source_post, finding, value_presentation,
    value_condition, discovery_value, media, post_tag, post_reference, post,
    discovery_place, place, discovery_operator_tag, discovery,
    user_profile, app_user
    CASCADE;

-- =========================================================
-- 共通
-- =========================================================

-- 画面・URL・API に出す識別子（設計書 2.1節）。gen_random_uuid() のランダムなバイトから英数字12文字を作る。
-- UUID v4 の版・変種のビットを含むバイト（6・8番目）は使わない。
CREATE FUNCTION exday_public_id() RETURNS text
    LANGUAGE sql VOLATILE
AS $$
    SELECT string_agg(
               substr('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',
                      get_byte(b, i) % 62 + 1, 1),
               '' ORDER BY i)
      FROM (SELECT uuid_send(gen_random_uuid()) AS b) AS r,
           unnest(ARRAY[0, 1, 2, 3, 4, 5, 7, 9, 10, 11, 12, 13]) AS i
$$;
COMMENT ON FUNCTION exday_public_id() IS '画面・URL・API に出す識別子（英数字12文字）。重なりは各表の一意の制約で拒む';

-- =========================================================
-- User（論理設計 4.1節）
-- =========================================================

CREATE TABLE app_user (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status      text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE app_user IS 'User。user は予約語のため app_user とする。運営かどうかは Cognito のグループで判定し、ここには持たない';

CREATE TABLE user_identity (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES app_user (id),
    issuer            text        NOT NULL,
    subject           text        NOT NULL,
    login_method      text        NOT NULL,
    upstream_subject  text,
    linked_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (issuer, subject)
);
COMMENT ON TABLE user_identity IS 'UserIdentity。認証サービス（Cognito）の識別子。メールアドレス・名前・顔写真は持たない（DEC-0016）';

CREATE TABLE user_profile (
    user_id     bigint      PRIMARY KEY REFERENCES app_user (id),
    nickname    text        NOT NULL CHECK (length(nickname) BETWEEN 1 AND 24),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE user_profile IS 'UserProfile。表示名は、表示のたびにここの今のニックネームを引く（#74）。幅（全角12文字相当）はアプリで確かめる';

CREATE TABLE user_nickname_change (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES app_user (id),
    before_nickname  text        NOT NULL,
    after_nickname   text        NOT NULL,
    changed_at       timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE user_nickname_change IS 'ニックネームの変更履歴（監査）。運営だけが読む（#74）';
CREATE INDEX user_nickname_change_user_idx ON user_nickname_change (user_id, changed_at);

-- =========================================================
-- 判断の記録（論理設計 6.5節）。多くの表から指されるので先に作る。対象・結果は decision_target（後で作る）
-- =========================================================

CREATE TABLE decision (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    kind              text        NOT NULL CHECK (kind IN (
                          'ESTABLISH', 'WITHDRAW_ESTABLISHMENT', 'RESTORE_ESTABLISHMENT',
                          'MERGE', 'UNMERGE', 'DERIVE', 'RELATE', 'UNRELATE', 'REATTACH',
                          'PLACE_ADOPT', 'PLACE_READOPT', 'PLACE_RECONSIDER', 'PLACE_KEEP', 'PLACE_REVOKE',
                          'CANDIDATE_REJECT', 'CANDIDATE_EXPIRE',
                          'ANSWER_ADOPT', 'ANSWER_READOPT', 'ANSWER_RECONSIDER', 'ANSWER_KEEP', 'ANSWER_REVOKE',
                          'QUESTION_MERGE', 'RELATION_CANDIDATE_REJECT',
                          'VISIBILITY_CHANGE', 'REPORT_STATUS_CHANGE', 'REVIEWED',
                          'PLACE_MERGE', 'PLACE_ABOLISH', 'OPERATOR_REGISTER')),
    workflow          text        NOT NULL CHECK (workflow IN (
                          'DISCOVERY_REVIEW', 'REPORT_HANDLING', 'ANALYSIS', 'USER_ACTION', 'OPERATOR_SQL')),
    method            text        NOT NULL CHECK (method IN ('AUTO', 'HUMAN')),
    actor_kind        text        NOT NULL CHECK (actor_kind IN ('SYSTEM', 'OPERATOR', 'USER')),
    actor_user_id     bigint      REFERENCES app_user (id),
    actor_role        text        CHECK (actor_role IN ('QUESTION_OWNER', 'CONVERSATION_STARTER', 'PROJECT_CREATOR')),
    reason_code       text,
    reason_text       text,
    rule_version      text,
    analysis_version  text,
    decided_at        timestamptz NOT NULL DEFAULT now(),
    CHECK ((actor_kind = 'SYSTEM') = (actor_user_id IS NULL)),
    CHECK (actor_kind <> 'SYSTEM' OR method = 'AUTO'),
    CHECK (actor_role IS NULL OR actor_kind = 'USER')
);
COMMENT ON TABLE decision IS '判断の記録。書き換えず、直すときは新しい行を足す。データの変更と同じトランザクションで足す（設計書 2.3節）';
COMMENT ON COLUMN decision.workflow IS '判断を行った業務の流れ。S18 の見直し済みは DISCOVERY_REVIEW の判断だけで導く（設計書 3.9節）。値は F10・F12 で確定する';
COMMENT ON COLUMN decision.reason_code IS '理由の分類。非公開の理由（作った人に出す）もここ。一覧は F10 で決める';

-- =========================================================
-- 場所（論理設計 4.2節）
-- =========================================================

CREATE TABLE place (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    name                     text        NOT NULL,
    spatial_type             text        NOT NULL CHECK (spatial_type IN ('POINT', 'AREA', 'ROUTE')),
    geom                     geography   NOT NULL,
    status                   text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MERGED', 'ABOLISHED')),
    merged_into_place_id     bigint      REFERENCES place (id),
    origin                   text        NOT NULL CHECK (origin IN ('OPEN_DATA', 'CONVERSATION', 'OPERATOR')),
    source_dataset           text,
    source_record_id         text,
    source_license           text,
    imported_at              timestamptz,
    existed_from             date,
    existed_to               date,
    access_station_place_id  bigint      REFERENCES place (id),
    created_decision_id      bigint      REFERENCES decision (id),
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (spatial_type = 'POINT' AND GeometryType(geom) = 'POINT')
     OR (spatial_type = 'AREA'  AND GeometryType(geom) IN ('POLYGON', 'MULTIPOLYGON'))
     OR (spatial_type = 'ROUTE' AND GeometryType(geom) IN ('LINESTRING', 'MULTILINESTRING'))
    ),
    CHECK ((status = 'MERGED') = (merged_into_place_id IS NOT NULL)),
    CHECK (merged_into_place_id <> id),
    CHECK (access_station_place_id <> id),
    CHECK (origin <> 'OPEN_DATA' OR source_dataset IS NOT NULL),
    CHECK (origin <> 'CONVERSATION' OR created_decision_id IS NOT NULL),
    CHECK (existed_to IS NULL OR existed_from IS NULL OR existed_from <= existed_to),
    UNIQUE (source_dataset, source_record_id)
);
COMMENT ON TABLE place IS 'Place。採用されていない場所（新しい場所の案）は入れない（place_candidate に持つ）';
COMMENT ON COLUMN place.geom IS 'WGS84（SRID 4326）';
COMMENT ON COLUMN place.merged_into_place_id IS '統合先。開いたときに案内する。循環は統合の処理で確かめる（設計書 3.12節）';
COMMENT ON COLUMN place.access_station_place_id IS '行き方の代表の駅（寄り道の計算の起点）';
COMMENT ON COLUMN place.created_decision_id IS '会話から育った場所は、作った採用の判断の記録を持つ';
CREATE INDEX place_geom_idx ON place USING gist (geom);
CREATE INDEX place_name_trgm_idx ON place USING gin (name gin_trgm_ops);

CREATE TABLE place_name (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id    bigint      NOT NULL REFERENCES place (id),
    name        text        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('DISPLAY', 'ALIAS', 'FORMER')),
    valid_from  date,
    valid_to    date,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (place_id, name, kind)
);
COMMENT ON TABLE place_name IS '場所の別名・旧称（時代つき）。検索の照合にも使う';
CREATE INDEX place_name_trgm_idx2 ON place_name USING gin (name gin_trgm_ops);

CREATE TABLE place_kind (
    place_id  bigint NOT NULL REFERENCES place (id),
    kind      text   NOT NULL,
    PRIMARY KEY (place_id, kind)
);
COMMENT ON TABLE place_kind IS '場所の種類（城跡・公園・駅・坂等）。1つの場所が複数を持てる。値は CHECK を付けず足せる形にする';
CREATE INDEX place_kind_kind_idx ON place_kind (kind);

CREATE TABLE place_estimate (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id        bigint      NOT NULL REFERENCES place (id),
    metric          text        NOT NULL,
    value           numeric     NOT NULL,
    unit            text        NOT NULL,
    method          text        NOT NULL,
    method_version  text        NOT NULL,
    computed_at     timestamptz NOT NULL,
    UNIQUE (place_id, metric, method, method_version)
);
COMMENT ON TABLE place_estimate IS '場所の推定の値（坂の勾配等）と計算の出どころ・版。番付の最初の並び（F09）の根拠';

CREATE TABLE place_relation (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_place_id  bigint      NOT NULL REFERENCES place (id),
    to_place_id    bigint      NOT NULL REFERENCES place (id),
    kind           text        NOT NULL CHECK (kind IN ('ADMINISTRATIVE', 'CONTAINS', 'AREA', 'FACILITY', 'ROUTE_PART')),
    seq            integer,
    valid_from     date,
    valid_to       date,
    origin         text        NOT NULL CHECK (origin IN ('OPEN_DATA', 'CONVERSATION', 'OPERATOR')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    CHECK (from_place_id <> to_place_id),
    CHECK ((kind = 'ROUTE_PART') = (seq IS NOT NULL))
);
COMMENT ON TABLE place_relation IS 'PlaceRelation。from が上位・全体（行政の上位、包含するエリア、施設、経路）、to が下位・部分。1本の木にしない';
CREATE UNIQUE INDEX place_relation_uniq ON place_relation (from_place_id, to_place_id, kind, coalesce(seq, 0));
CREATE INDEX place_relation_to_idx ON place_relation (to_place_id, kind);

-- =========================================================
-- Subject（論理設計 7.2節）
-- =========================================================

CREATE TABLE subject (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label        text        NOT NULL,
    aliases      text[]      NOT NULL DEFAULT '{}',
    description  text,
    kind         text        NOT NULL CHECK (kind IN ('TARGET', 'ASPECT')),
    granularity  text        CHECK (granularity IN ('SPECIFIC', 'GENERAL')),
    status       text        NOT NULL DEFAULT 'CANDIDATE' CHECK (status IN ('CANDIDATE', 'ADOPTED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CHECK (kind = 'TARGET' OR granularity IS NULL)
);
COMMENT ON TABLE subject IS 'Subject。対象（TARGET）と観点（ASPECT）';

CREATE TABLE user_interest (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES app_user (id),
    place_id    bigint      REFERENCES place (id),
    subject_id  bigint      REFERENCES subject (id),
    origin      text        NOT NULL DEFAULT 'SELF' CHECK (origin IN ('SELF')),
    intent      text        NOT NULL DEFAULT 'INTERESTED' CHECK (intent IN ('INTERESTED', 'NOT_INTERESTED')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(place_id, subject_id) = 1),
    UNIQUE (user_id, place_id),
    UNIQUE (user_id, subject_id)
);
COMMENT ON TABLE user_interest IS 'UserInterest（MVP は自己申告だけ）。興味のある地域は place_id。他の人には表示しない';

-- =========================================================
-- 企画（論理設計 4.6節）。会話の入口から指されるので会話より先に作る
-- =========================================================

CREATE TABLE project (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    creator_kind       text        NOT NULL CHECK (creator_kind IN ('OPERATOR', 'USER')),
    creator_user_id    bigint      NOT NULL REFERENCES app_user (id),
    type               text        NOT NULL CHECK (type IN ('COURSE', 'RANKING', 'COLLECTION', 'MEMORY')),
    title              text        NOT NULL,
    call_text          text        NOT NULL DEFAULT '',
    description        text        NOT NULL DEFAULT '',
    visibility         text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    moderation_status  text        NOT NULL DEFAULT 'PROVISIONAL' CHECK (moderation_status IN ('PROVISIONAL', 'CHECKED')),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE project IS 'Project（企画）。編集できるのは creator_user_id の人だけ（運営が作った企画も、作ったアカウント）。閉じる機能は MVP の対象外';
COMMENT ON COLUMN project.moderation_status IS '暫定公開（PROVISIONAL）／確認済み（CHECKED）。公開状態とは別。値と手順は F10 で決める（設計書 2.6節）';
CREATE INDEX project_creator_idx ON project (creator_user_id, created_at);

-- =========================================================
-- Discovery（論理設計 4.5節）
-- =========================================================

CREATE TABLE discovery (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    title           text        NOT NULL,
    summary         text        NOT NULL DEFAULT '',
    visibility      text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    established_at  timestamptz NOT NULL,
    withdrawn_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (withdrawn_at IS NULL OR withdrawn_at >= established_at)
);
COMMENT ON TABLE discovery IS 'Discovery。成立したときに作り、成立状態を持たない（DEC-0020 決定9）';
COMMENT ON COLUMN discovery.withdrawn_at IS '成立の取り消し。正本は判断の記録（WITHDRAW_ESTABLISHMENT）で、その写しを同じトランザクションで入れる。公開状態とは別（設計書 3.4節）';

-- =========================================================
-- 会話と Post（論理設計 4.3・4.4節）
-- =========================================================

CREATE TABLE conversation (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    entry_place_id         bigint      REFERENCES place (id),
    entry_discovery_id     bigint      REFERENCES discovery (id),
    entry_project_id       bigint      REFERENCES project (id),
    is_project_own         boolean     NOT NULL DEFAULT false,
    starter_user_id        bigint      NOT NULL REFERENCES app_user (id),
    visibility             text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    public_post_count      integer     NOT NULL DEFAULT 0 CHECK (public_post_count >= 0),
    last_public_posted_at  timestamptz,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(entry_place_id, entry_discovery_id, entry_project_id) <= 1),
    CHECK (NOT is_project_own OR entry_project_id IS NOT NULL)
);
COMMENT ON TABLE conversation IS 'Conversation（会話）。最初の Post と同じトランザクションで作る';
COMMENT ON COLUMN conversation.is_project_own IS '企画そのものの会話。部分一意の index で企画ごとに1つに保つ（同時に作ろうとしたら後の方が失敗し、できている会話に Post を足し直す）';
COMMENT ON COLUMN conversation.starter_user_id IS '会話を始めた人（最初の Post の書き手）の写し。変わらない';
COMMENT ON COLUMN conversation.public_post_count IS '公開の Post の数の写し。Post を足す・公開状態を変えるのと同じトランザクションで更新する（設計書 2.4節）';
CREATE UNIQUE INDEX conversation_project_own_uniq ON conversation (entry_project_id) WHERE is_project_own;
CREATE INDEX conversation_entry_place_idx ON conversation (entry_place_id) WHERE entry_place_id IS NOT NULL;
CREATE INDEX conversation_entry_discovery_idx ON conversation (entry_discovery_id) WHERE entry_discovery_id IS NOT NULL;
CREATE INDEX conversation_entry_project_idx ON conversation (entry_project_id) WHERE entry_project_id IS NOT NULL;
CREATE INDEX conversation_starter_idx ON conversation (starter_user_id, created_at);
CREATE INDEX conversation_last_posted_idx ON conversation (last_public_posted_at);

CREATE TABLE post (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    conversation_id    bigint      NOT NULL REFERENCES conversation (id),
    user_id            bigint      NOT NULL REFERENCES app_user (id),
    body               text        NOT NULL CHECK (length(body) > 0),
    visibility         text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    moderation_status  text        NOT NULL DEFAULT 'PROVISIONAL' CHECK (moderation_status IN ('PROVISIONAL', 'CHECKED')),
    posted_at          timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE post IS 'Post（発言）。場所・時期・分類を持たない（論理設計 4.4節）';
CREATE INDEX post_conversation_idx ON post (conversation_id, posted_at);
CREATE INDEX post_user_idx ON post (user_id, posted_at);
CREATE INDEX post_moderation_idx ON post (posted_at) WHERE moderation_status = 'PROVISIONAL';

CREATE TABLE post_reference (
    from_post_id  bigint      NOT NULL REFERENCES post (id),
    to_post_id    bigint      NOT NULL REFERENCES post (id),
    kind          text        NOT NULL DEFAULT 'REPLY' CHECK (kind IN ('REPLY', 'MENTION')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (from_post_id, to_post_id, kind),
    CHECK (from_post_id <> to_post_id)
);
COMMENT ON TABLE post_reference IS 'PostReference。参照元と参照先は同じ会話（アプリで確かめる）';
CREATE INDEX post_reference_to_idx ON post_reference (to_post_id);

-- =========================================================
-- Discovery の中身（論理設計 4.5・6.6・6.7・7.3節）
-- =========================================================

CREATE TABLE discovery_place (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discovery_id         bigint      NOT NULL REFERENCES discovery (id),
    place_id             bigint      NOT NULL REFERENCES place (id),
    role                 text        NOT NULL,
    seq                  integer,
    valid_from           date,
    valid_to             date,
    description          text,
    status               text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_decision_id  bigint      NOT NULL REFERENCES decision (id),
    removed_decision_id  bigint      REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'REMOVED') = (removed_decision_id IS NOT NULL))
);
COMMENT ON TABLE discovery_place IS 'DiscoveryPlace。付け替えでも行を消さず REMOVED にする。場所が0件にならないことはアプリで守る（足してから外す）';
COMMENT ON COLUMN discovery_place.role IS '役割（体験できる場所・始まり・経由・終わり・記憶の場所 等）。足せる形にするため CHECK を付けない。出す位置の役割はアプリの定数';
CREATE UNIQUE INDEX discovery_place_active_uniq ON discovery_place (discovery_id, place_id, role) WHERE status = 'ACTIVE';
CREATE INDEX discovery_place_place_idx ON discovery_place (place_id) WHERE status = 'ACTIVE';

CREATE TABLE discovery_value (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    discovery_id         bigint      NOT NULL REFERENCES discovery (id),
    description          text        NOT NULL,
    status               text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_decision_id  bigint      NOT NULL REFERENCES decision (id),
    removed_decision_id  bigint      REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'REMOVED') = (removed_decision_id IS NOT NULL))
);
COMMENT ON TABLE discovery_value IS 'Value。value は SQL の予約語に近いため discovery_value とする';
CREATE INDEX discovery_value_discovery_idx ON discovery_value (discovery_id) WHERE status = 'ACTIVE';

CREATE TABLE value_condition (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    value_id     bigint NOT NULL REFERENCES discovery_value (id),
    axis         text   NOT NULL CHECK (axis IN ('SEASON')),
    season       text   CHECK (season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER', 'ALL_YEAR')),
    description  text,
    CHECK (axis <> 'SEASON' OR season IS NOT NULL),
    UNIQUE (value_id, axis, season)
);
COMMENT ON TABLE value_condition IS 'Condition。軸の行がないことは、その軸に依存しないこと。通年は ALL_YEAR で、条件なしと区別する';

CREATE TABLE discovery_origin (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discovery_id         bigint      NOT NULL REFERENCES discovery (id),
    kind                 text        NOT NULL CHECK (kind IN ('CONVERSATION', 'POST', 'PROJECT', 'DERIVED', 'OPERATOR')),
    conversation_id      bigint      REFERENCES conversation (id),
    post_id              bigint      REFERENCES post (id),
    project_id           bigint      REFERENCES project (id),
    source_discovery_id  bigint      REFERENCES discovery (id),
    decision_id          bigint      NOT NULL REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(conversation_id, post_id, project_id, source_discovery_id)
           = CASE kind WHEN 'OPERATOR' THEN 0 ELSE 1 END),
    CHECK (kind <> 'CONVERSATION' OR conversation_id IS NOT NULL),
    CHECK (kind <> 'POST' OR post_id IS NOT NULL),
    CHECK (kind <> 'PROJECT' OR project_id IS NOT NULL),
    CHECK (kind <> 'DERIVED' OR source_discovery_id IS NOT NULL),
    CHECK (source_discovery_id <> discovery_id)
);
COMMENT ON TABLE discovery_origin IS 'DiscoveryOrigin（由来）。成立の判断の記録を必ず持つ';
CREATE INDEX discovery_origin_discovery_idx ON discovery_origin (discovery_id);
CREATE INDEX discovery_origin_conversation_idx ON discovery_origin (conversation_id) WHERE conversation_id IS NOT NULL;

CREATE TABLE discovery_relation (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_discovery_id    bigint      NOT NULL REFERENCES discovery (id),
    to_discovery_id      bigint      NOT NULL REFERENCES discovery (id),
    kind                 text        NOT NULL CHECK (kind IN ('DERIVED', 'RELATED', 'MERGED')),
    description          text,
    status               text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_decision_id  bigint      NOT NULL REFERENCES decision (id),
    revoked_decision_id  bigint      REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CHECK (from_discovery_id <> to_discovery_id),
    CHECK ((status = 'REVOKED') = (revoked_decision_id IS NOT NULL))
);
COMMENT ON TABLE discovery_relation IS 'DiscoveryRelation。派生は from が派生元、統合（MERGED）は from が統合された側。統合の循環は統合の処理で確かめる';
CREATE UNIQUE INDEX discovery_relation_merged_uniq ON discovery_relation (from_discovery_id) WHERE kind = 'MERGED' AND status = 'ACTIVE';
CREATE UNIQUE INDEX discovery_relation_active_uniq ON discovery_relation (from_discovery_id, to_discovery_id, kind) WHERE status = 'ACTIVE';
CREATE INDEX discovery_relation_to_idx ON discovery_relation (to_discovery_id) WHERE status = 'ACTIVE';

CREATE TABLE subject_assignment (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id           bigint      NOT NULL REFERENCES subject (id),
    discovery_id         bigint      REFERENCES discovery (id),
    value_id             bigint      REFERENCES discovery_value (id),
    role                 text,
    confidence           numeric     CHECK (confidence BETWEEN 0 AND 1),
    assigned_by          text        NOT NULL CHECK (assigned_by IN ('SYSTEM', 'OPERATOR')),
    created_decision_id  bigint      REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(discovery_id, value_id) = 1),
    UNIQUE (subject_id, discovery_id),
    UNIQUE (subject_id, value_id)
);
COMMENT ON TABLE subject_assignment IS '対象と Subject の関連（Discovery・Value）';

-- =========================================================
-- 企画の項目と変更の履歴（論理設計 4.6.1・4.6.2節）
-- =========================================================

CREATE TABLE project_item (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    project_id    bigint      NOT NULL REFERENCES project (id),
    place_id      bigint      REFERENCES place (id),
    discovery_id  bigint      REFERENCES discovery (id),
    position      integer     NOT NULL CHECK (position >= 1),
    note          text        NOT NULL DEFAULT '',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(place_id, discovery_id) = 1),
    CONSTRAINT project_item_position_uniq UNIQUE (project_id, position) DEFERRABLE INITIALLY DEFERRED
);
COMMENT ON TABLE project_item IS 'ProjectItem。場所 または Discovery を指す。順番の一意は遅延の制約（並べ替えを1つのトランザクションで行う）。外すときは行を消し、project_change_item に残す';
CREATE INDEX project_item_place_idx ON project_item (place_id) WHERE place_id IS NOT NULL;
CREATE INDEX project_item_discovery_idx ON project_item (discovery_id) WHERE discovery_id IS NOT NULL;

CREATE TABLE project_change (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    project_id          bigint      NOT NULL REFERENCES project (id),
    kind                text        NOT NULL CHECK (kind IN ('CREATED', 'UPDATED')),
    changed_by_user_id  bigint      NOT NULL REFERENCES app_user (id),
    fields_changed      text[]      NOT NULL DEFAULT '{}'
                                    CHECK (fields_changed <@ ARRAY['TITLE', 'CALL_TEXT', 'DESCRIPTION']::text[]),
    changed_at          timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE project_change IS '企画の変更の履歴（変更の出来事）。企画そのものの会話のタイムラインに出す。企画に属し、会話には属さない。利用者の文は写さない（設計書 3.5節）';
COMMENT ON COLUMN project_change.fields_changed IS '表題・呼びかけ・説明を変えた事実だけ。中身は写さない';
CREATE UNIQUE INDEX project_change_created_uniq ON project_change (project_id) WHERE kind = 'CREATED';
CREATE INDEX project_change_project_idx ON project_change (project_id, changed_at);

CREATE TABLE project_change_item (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    change_id        bigint  NOT NULL REFERENCES project_change (id),
    place_id         bigint  REFERENCES place (id),
    discovery_id     bigint  REFERENCES discovery (id),
    kind             text    NOT NULL CHECK (kind IN ('ADDED', 'REMOVED', 'MOVED')),
    before_position  integer CHECK (before_position >= 1),
    after_position   integer CHECK (after_position >= 1),
    CHECK (num_nonnulls(place_id, discovery_id) = 1),
    CHECK (
        (kind = 'ADDED'   AND before_position IS NULL     AND after_position IS NOT NULL)
     OR (kind = 'REMOVED' AND before_position IS NOT NULL AND after_position IS NULL)
     OR (kind = 'MOVED'   AND before_position IS NOT NULL AND after_position IS NOT NULL
                          AND before_position <> after_position)
    )
);
COMMENT ON TABLE project_change_item IS '変更で動いた項目ごとの差分。指す先は企画の項目の行ではなく場所・Discovery（項目を外すと行がなくなるため）。作った時点（全部 ADDED）から重ねると過去の並びを再現できる';
CREATE INDEX project_change_item_change_idx ON project_change_item (change_id);

-- =========================================================
-- 見立て（論理設計 5節）。どれも analysis_version を持つ
-- =========================================================

CREATE TABLE claim (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id           bigint      NOT NULL REFERENCES post (id),
    content           text        NOT NULL,
    exclusion_reason  text,
    analysis_version  text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE claim IS 'Claim（主張）。除外しても消さず、exclusion_reason を持つ';
CREATE INDEX claim_post_idx ON claim (post_id);

CREATE TABLE finding (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id               text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    conversation_id         bigint      NOT NULL REFERENCES conversation (id),
    kind                    text        NOT NULL CHECK (kind IN ('THEORY', 'VALUE')),
    current_version         integer     NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    status                  text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MERGED', 'SPLIT', 'EXPIRED')),
    merged_into_finding_id  bigint      REFERENCES finding (id),
    analysis_version        text        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'MERGED') = (merged_into_finding_id IS NOT NULL)),
    CHECK (merged_into_finding_id <> id)
);
COMMENT ON TABLE finding IS 'Finding（わかってきたこと）。中身は finding_version に持つ。ex-day の事実認定ではない';
CREATE INDEX finding_conversation_idx ON finding (conversation_id) WHERE status = 'ACTIVE';

CREATE TABLE finding_version (
    finding_id        bigint      NOT NULL REFERENCES finding (id),
    version           integer     NOT NULL CHECK (version >= 1),
    content           text        NOT NULL,
    backed            boolean     NOT NULL DEFAULT false,
    unconfirmed       boolean     NOT NULL DEFAULT true,
    analysis_version  text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (finding_id, version)
);
COMMENT ON TABLE finding_version IS 'Finding の版。Reaction と判断の記録はこの版を指す';
ALTER TABLE finding
    ADD CONSTRAINT finding_current_version_fkey FOREIGN KEY (id, current_version)
        REFERENCES finding_version (finding_id, version) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE finding_version_claim (
    finding_id  bigint  NOT NULL,
    version     integer NOT NULL,
    claim_id    bigint  NOT NULL REFERENCES claim (id),
    PRIMARY KEY (finding_id, version, claim_id),
    FOREIGN KEY (finding_id, version) REFERENCES finding_version (finding_id, version)
);
COMMENT ON TABLE finding_version_claim IS '版ごとの根拠の主張。根拠の Post が非公開になったとき、claim.post_id から影響を受ける版を引く';
CREATE INDEX finding_version_claim_claim_idx ON finding_version_claim (claim_id);

CREATE TABLE open_question (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    conversation_id          bigint      NOT NULL REFERENCES conversation (id),
    content                  text        NOT NULL,
    kind                     text        NOT NULL CHECK (kind IN ('WHERE', 'WHY', 'WHAT', 'OTHER')),
    asker_user_id            bigint      REFERENCES app_user (id),
    status                   text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MERGED', 'EXPIRED')),
    merged_into_question_id  bigint      REFERENCES open_question (id),
    analysis_version         text        NOT NULL,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'MERGED') = (merged_into_question_id IS NOT NULL)),
    CHECK (merged_into_question_id <> id)
);
COMMENT ON TABLE open_question IS 'OpenQuestion（問い）。回答の状態は持たず、place_adoption・question_answer から導く';
CREATE INDEX open_question_conversation_idx ON open_question (conversation_id);

CREATE TABLE open_question_post (
    question_id  bigint NOT NULL REFERENCES open_question (id),
    post_id      bigint NOT NULL REFERENCES post (id),
    PRIMARY KEY (question_id, post_id)
);
COMMENT ON TABLE open_question_post IS '問いの Post（人が書いたもの）';

CREATE TABLE open_question_setting (
    question_id         bigint      PRIMARY KEY REFERENCES open_question (id),
    keep_open           boolean     NOT NULL,
    updated_by_user_id  bigint      NOT NULL REFERENCES app_user (id),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE open_question_setting IS '「引き続き募集するか」。問いを立てた人の意思なので見立てと分ける（論理設計 5.3節の案A）。統合のときに引き継ぐ';

CREATE TABLE discovery_candidate (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id   bigint      REFERENCES conversation (id),
    post_id           bigint      REFERENCES post (id),
    project_id        bigint      REFERENCES project (id),
    proposal          jsonb       NOT NULL DEFAULT '{}',
    confidence        numeric     CHECK (confidence BETWEEN 0 AND 1),
    status            text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ESTABLISHED', 'EXPIRED')),
    discovery_id      bigint      REFERENCES discovery (id),
    analysis_version  text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(conversation_id, post_id, project_id) = 1),
    CHECK (status <> 'ESTABLISHED' OR discovery_id IS NOT NULL)
);
COMMENT ON TABLE discovery_candidate IS 'DiscoveryCandidate。場所・Subject・価値・時間の候補は見立てなので proposal（jsonb）にまとめる';

CREATE TABLE discovery_candidate_finding (
    candidate_id  bigint NOT NULL REFERENCES discovery_candidate (id),
    finding_id    bigint NOT NULL REFERENCES finding (id),
    PRIMARY KEY (candidate_id, finding_id)
);

CREATE TABLE relation_candidate (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discovery_candidate_id  bigint      REFERENCES discovery_candidate (id),
    from_discovery_id       bigint      REFERENCES discovery (id),
    to_discovery_id         bigint      NOT NULL REFERENCES discovery (id),
    kind                    text        NOT NULL CHECK (kind IN ('SAME', 'RELATED', 'DERIVED', 'COMPLEMENT', 'NEW_VALUE')),
    reason                  text,
    confidence              numeric     CHECK (confidence BETWEEN 0 AND 1),
    status                  text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DECIDED', 'EXPIRED')),
    analysis_version        text        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(discovery_candidate_id, from_discovery_id) = 1),
    CHECK (from_discovery_id <> to_discovery_id)
);
COMMENT ON TABLE relation_candidate IS 'RelationCandidate。却下は判断の記録に残し、同じ組を繰り返し出さない関門にする';

CREATE TABLE search_profile (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id          bigint      UNIQUE REFERENCES place (id),
    discovery_id      bigint      UNIQUE REFERENCES discovery (id),
    project_id        bigint      UNIQUE REFERENCES project (id),
    search_text       text        NOT NULL,
    subjects          text[]      NOT NULL DEFAULT '{}',
    seasons           text[]      NOT NULL DEFAULT '{}',
    area              geography,
    analysis_version  text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(place_id, discovery_id, project_id) = 1)
);
COMMENT ON TABLE search_profile IS 'SearchProfile（作り直せる見立て）。対象ごとに1行。結果は対象を visible_* で引き直して出す。中身と照合は F07 で決める';
CREATE INDEX search_profile_text_trgm_idx ON search_profile USING gin (search_text gin_trgm_ops);
CREATE INDEX search_profile_area_idx ON search_profile USING gist (area);

-- =========================================================
-- 場所の候補と採用、回答の採用（論理設計 6.1・6.2・6.4節）
-- =========================================================

CREATE TABLE place_candidate (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    conversation_id        bigint      REFERENCES conversation (id),
    open_question_id       bigint      REFERENCES open_question (id),
    place_id               bigint      REFERENCES place (id),
    proposed_name          text,
    proposed_spatial_type  text        CHECK (proposed_spatial_type IN ('POINT', 'AREA', 'ROUTE')),
    proposed_geom          geography,
    proposed_kinds         text[]      NOT NULL DEFAULT '{}',
    proposed_existed_from  date,
    proposed_existed_to    date,
    created_place_id       bigint      REFERENCES place (id),
    origin                 text        NOT NULL CHECK (origin IN ('AI', 'HUMAN', 'ENTRY')),
    status                 text        NOT NULL DEFAULT 'CONSIDERING' CHECK (status IN ('CONSIDERING', 'REJECTED', 'EXPIRED')),
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(conversation_id, open_question_id) = 1),
    -- 候補の場所は、既存の場所か新しい場所の案のどちらか一方
    CHECK (
        (place_id IS NOT NULL AND proposed_name IS NULL AND proposed_spatial_type IS NULL AND proposed_geom IS NULL
         AND proposed_kinds = '{}' AND proposed_existed_from IS NULL AND proposed_existed_to IS NULL)
     OR (place_id IS NULL AND proposed_name IS NOT NULL)
    ),
    CHECK ((proposed_spatial_type IS NULL) = (proposed_geom IS NULL)),
    CHECK (created_place_id IS NULL OR place_id IS NULL),
    UNIQUE (conversation_id, place_id),
    UNIQUE (open_question_id, place_id),
    -- place_adoption の複合の外部キーの参照先（対象を候補と食い違わせない）
    UNIQUE (id, conversation_id),
    UNIQUE (id, open_question_id)
);
COMMENT ON TABLE place_candidate IS 'PlaceCandidate（場所の候補）。対象ごとに1か所を正本にする。採用・取り消しは持たず place_adoption から導く';
COMMENT ON COLUMN place_candidate.created_place_id IS '新しい場所の案を採用して作った Place。案の名称・位置は、採用前に場所の検索・地図に混ぜない';
CREATE INDEX place_candidate_considering_geom_idx ON place_candidate USING gist (proposed_geom) WHERE status = 'CONSIDERING';
CREATE INDEX place_candidate_considering_place_idx ON place_candidate (place_id) WHERE status = 'CONSIDERING';

CREATE TABLE place_candidate_basis (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id  bigint      NOT NULL REFERENCES place_candidate (id),
    post_id       bigint      REFERENCES post (id),
    claim_id      bigint      REFERENCES claim (id),
    finding_id    bigint      REFERENCES finding (id),
    origin        text        NOT NULL CHECK (origin IN ('AI', 'HUMAN', 'ENTRY')),
    added_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(post_id, claim_id, finding_id) >= 1)
);
COMMENT ON TABLE place_candidate_basis IS '場所の候補の根拠';
CREATE INDEX place_candidate_basis_candidate_idx ON place_candidate_basis (candidate_id);
CREATE INDEX place_candidate_basis_post_idx ON place_candidate_basis (post_id) WHERE post_id IS NOT NULL;

CREATE TABLE place_candidate_voice (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id  bigint      NOT NULL REFERENCES place_candidate (id),
    user_id       bigint      NOT NULL REFERENCES app_user (id),
    stance        text        NOT NULL CHECK (stance IN ('SUPPORT', 'OPPOSE')),
    post_id       bigint      REFERENCES post (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (candidate_id, user_id)
);
COMMENT ON TABLE place_candidate_voice IS '場所の候補への声（「ここかも」「違うと思う」）。判断の材料であって判断ではない';

CREATE TABLE place_candidate_state_change (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id             bigint      NOT NULL REFERENCES place_candidate (id),
    from_status              text        CHECK (from_status IN ('CONSIDERING', 'REJECTED', 'EXPIRED')),
    to_status                text        NOT NULL CHECK (to_status IN ('CONSIDERING', 'REJECTED', 'EXPIRED')),
    moved_from_candidate_id  bigint      REFERENCES place_candidate (id),
    actor_kind               text        NOT NULL CHECK (actor_kind IN ('SYSTEM', 'OPERATOR', 'USER')),
    actor_user_id            bigint      REFERENCES app_user (id),
    reason                   text,
    decision_id              bigint      REFERENCES decision (id),
    changed_at               timestamptz NOT NULL DEFAULT now(),
    CHECK ((actor_kind = 'SYSTEM') = (actor_user_id IS NULL)),
    CHECK (moved_from_candidate_id <> candidate_id)
);
COMMENT ON TABLE place_candidate_state_change IS '場所の候補の状態の履歴（作ったとき・不採用・失効・問いの統合で移したこと）';
CREATE INDEX place_candidate_state_change_candidate_idx ON place_candidate_state_change (candidate_id, changed_at);

CREATE TABLE place_adoption (
    id                          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                   text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    candidate_id                bigint      NOT NULL REFERENCES place_candidate (id),
    conversation_id             bigint      REFERENCES conversation (id),
    open_question_id            bigint      REFERENCES open_question (id),
    place_id                    bigint      NOT NULL REFERENCES place (id),
    role                        text,
    seq                         integer,
    actor_kind                  text        NOT NULL CHECK (actor_kind IN ('QUESTION_OWNER', 'CONVERSATION_STARTER', 'RULE_OR_OPERATOR')),
    actor_user_id               bigint      REFERENCES app_user (id),
    status                      text        NOT NULL DEFAULT 'ADOPTED' CHECK (status IN ('ADOPTED', 'RECONSIDERING', 'REVOKED')),
    revoke_reason               text        CHECK (revoke_reason IN ('READOPTED', 'WITHDRAWN', 'INHERITED_BY_MERGE')),
    inherited_from_adoption_id  bigint      REFERENCES place_adoption (id),
    owner_agreement             text        CHECK (owner_agreement IN ('AGREED', 'UNCONFIRMED')),
    created_decision_id         bigint      NOT NULL REFERENCES decision (id),
    revoked_decision_id         bigint      REFERENCES decision (id),
    adopted_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    revoked_at                  timestamptz,
    CHECK (num_nonnulls(conversation_id, open_question_id) = 1),
    -- 対象を候補と食い違わせない。対象の列が空の側は検査されず、値のある側は候補と同じでなければ拒まれる
    FOREIGN KEY (candidate_id, conversation_id) REFERENCES place_candidate (id, conversation_id),
    FOREIGN KEY (candidate_id, open_question_id) REFERENCES place_candidate (id, open_question_id),
    CHECK (open_question_id IS NULL OR (role IS NULL AND seq IS NULL)),
    CHECK (actor_kind <> 'CONVERSATION_STARTER' OR conversation_id IS NOT NULL),
    CHECK (actor_kind <> 'QUESTION_OWNER' OR open_question_id IS NOT NULL),
    CHECK (actor_kind = 'RULE_OR_OPERATOR' OR actor_user_id IS NOT NULL),
    CHECK (owner_agreement IS NULL OR open_question_id IS NOT NULL),
    CHECK ((status = 'REVOKED') = (revoke_reason IS NOT NULL AND revoked_at IS NOT NULL AND revoked_decision_id IS NOT NULL)),
    CHECK ((revoke_reason = 'INHERITED_BY_MERGE') IS NOT TRUE OR status = 'REVOKED'),
    CHECK (inherited_from_adoption_id <> id)
);
COMMENT ON TABLE place_adoption IS 'PlaceAdoption（場所の採用の唯一の正本）。行を消さない。採用し直しは、前の採用を REVOKED（READOPTED）にしてから新しい採用を足す。採用した場所が候補の場所（既存の place_id、または created_place_id）と同じことは採用を作る処理で確かめる';
COMMENT ON COLUMN place_adoption.open_question_id IS '対象が問いのとき。その問いへの回答の採用でもある';
-- 対象が問いなら、採用中・再検討中は1件まで
CREATE UNIQUE INDEX place_adoption_question_active_uniq ON place_adoption (open_question_id)
    WHERE status IN ('ADOPTED', 'RECONSIDERING') AND open_question_id IS NOT NULL;
-- 同じ候補への採用中・再検討中は1件まで（対象が会話のときは候補ごとに複数の場所を持てる）
CREATE UNIQUE INDEX place_adoption_candidate_active_uniq ON place_adoption (candidate_id)
    WHERE status IN ('ADOPTED', 'RECONSIDERING');
CREATE INDEX place_adoption_conversation_active_idx ON place_adoption (conversation_id)
    WHERE status IN ('ADOPTED', 'RECONSIDERING') AND conversation_id IS NOT NULL;
CREATE INDEX place_adoption_place_active_idx ON place_adoption (place_id)
    WHERE status IN ('ADOPTED', 'RECONSIDERING');

CREATE TABLE question_answer (
    id                        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                 text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    question_id               bigint      NOT NULL REFERENCES open_question (id),
    answer_finding_id         bigint,
    answer_finding_version    integer,
    answer_post_id            bigint      REFERENCES post (id),
    actor_kind                text        NOT NULL CHECK (actor_kind IN ('QUESTION_OWNER', 'RULE_OR_OPERATOR')),
    actor_user_id             bigint      REFERENCES app_user (id),
    status                    text        NOT NULL DEFAULT 'ADOPTED' CHECK (status IN ('ADOPTED', 'RECONSIDERING', 'REVOKED')),
    revoke_reason             text        CHECK (revoke_reason IN ('READOPTED', 'WITHDRAWN', 'INHERITED_BY_MERGE')),
    inherited_from_answer_id  bigint      REFERENCES question_answer (id),
    owner_agreement           text        CHECK (owner_agreement IN ('AGREED', 'UNCONFIRMED')),
    created_decision_id       bigint      NOT NULL REFERENCES decision (id),
    revoked_decision_id       bigint      REFERENCES decision (id),
    adopted_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    revoked_at                timestamptz,
    FOREIGN KEY (answer_finding_id, answer_finding_version) REFERENCES finding_version (finding_id, version),
    CHECK (num_nonnulls(answer_finding_id, answer_post_id) = 1),
    CHECK ((answer_finding_id IS NULL) = (answer_finding_version IS NULL)),
    CHECK (actor_kind = 'RULE_OR_OPERATOR' OR actor_user_id IS NOT NULL),
    CHECK ((status = 'REVOKED') = (revoke_reason IS NOT NULL AND revoked_at IS NOT NULL AND revoked_decision_id IS NOT NULL)),
    CHECK (inherited_from_answer_id <> id)
);
COMMENT ON TABLE question_answer IS 'QuestionAnswer（場所以外の回答の採用）。場所を探す問いの回答は place_adoption に持つ';
CREATE UNIQUE INDEX question_answer_active_uniq ON question_answer (question_id) WHERE status IN ('ADOPTED', 'RECONSIDERING');

CREATE TABLE location_sharing_prompt (
    post_id            bigint      PRIMARY KEY REFERENCES post (id),
    status             text        NOT NULL DEFAULT 'SHOWN' CHECK (status IN ('SHOWN', 'ANSWERED', 'DECLINED', 'LATER')),
    shown_at           timestamptz NOT NULL DEFAULT now(),
    answered_at        timestamptz,
    place_adoption_id  bigint      UNIQUE REFERENCES place_adoption (id),
    CHECK ((status = 'ANSWERED') = (place_adoption_id IS NOT NULL)),
    CHECK ((status IN ('ANSWERED', 'DECLINED')) = (answered_at IS NOT NULL))
);
COMMENT ON TABLE location_sharing_prompt IS 'LocationSharingPrompt（場所の共有の誘い）。主キーで1つの投稿に1件までを守る（DEC-0023）';
CREATE INDEX location_sharing_prompt_later_idx ON location_sharing_prompt (shown_at) WHERE status = 'LATER';

-- =========================================================
-- Reaction・通報（論理設計 4.7・8.3節）
-- =========================================================

CREATE TABLE reaction (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES app_user (id),
    post_id           bigint      REFERENCES post (id),
    discovery_id      bigint      REFERENCES discovery (id),
    value_id          bigint      REFERENCES discovery_value (id),
    finding_id        bigint,
    finding_version   integer,
    open_question_id  bigint      REFERENCES open_question (id),
    kind              text        NOT NULL CHECK (kind IN ('EMPATHY', 'AGREE', 'WANT_TO_GO', 'MAYBE', 'CURIOUS')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (finding_id, finding_version) REFERENCES finding_version (finding_id, version),
    CHECK (num_nonnulls(post_id, discovery_id, value_id, finding_id, open_question_id) = 1),
    CHECK ((finding_id IS NULL) = (finding_version IS NULL)),
    -- 対象と種類の組み合わせ（設計書 3.10節）。裏付けのない説かどうか（MAYBE）はアプリで確かめる
    CHECK (
        (kind = 'EMPATHY' AND post_id IS NOT NULL)
     OR (kind IN ('AGREE', 'WANT_TO_GO') AND num_nonnulls(discovery_id, value_id, finding_id) = 1)
     OR (kind = 'MAYBE' AND finding_id IS NOT NULL)
     OR (kind = 'CURIOUS' AND open_question_id IS NOT NULL)
    ),
    UNIQUE (user_id, post_id, kind),
    UNIQUE (user_id, discovery_id, kind),
    UNIQUE (user_id, value_id, kind),
    UNIQUE (user_id, finding_id, kind),
    UNIQUE (user_id, open_question_id, kind)
);
COMMENT ON TABLE reaction IS 'Reaction。取り消したら行を消す（人が作ったもので、判断の材料ではないため）。数を事実の信頼度にしない';
CREATE INDEX reaction_post_idx ON reaction (post_id) WHERE post_id IS NOT NULL;
CREATE INDEX reaction_discovery_idx ON reaction (discovery_id) WHERE discovery_id IS NOT NULL;
CREATE INDEX reaction_value_idx ON reaction (value_id) WHERE value_id IS NOT NULL;
CREATE INDEX reaction_finding_idx ON reaction (finding_id) WHERE finding_id IS NOT NULL;
CREATE INDEX reaction_question_idx ON reaction (open_question_id) WHERE open_question_id IS NOT NULL;

CREATE TABLE content_report (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         text        NOT NULL UNIQUE DEFAULT exday_public_id() CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    reporter_user_id  bigint      NOT NULL REFERENCES app_user (id),
    conversation_id   bigint      REFERENCES conversation (id),
    post_id           bigint      REFERENCES post (id),
    discovery_id      bigint      REFERENCES discovery (id),
    project_id        bigint      REFERENCES project (id),
    reason_code       text        NOT NULL CHECK (reason_code IN ('RULE_VIOLATION', 'RIGHTS', 'SAFETY', 'OTHER')),
    description       text        NOT NULL DEFAULT '',
    status            text        NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    received_at       timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    resolved_at       timestamptz,
    CHECK (num_nonnulls(conversation_id, post_id, discovery_id, project_id) = 1),
    CHECK ((status IN ('RESOLVED', 'DISMISSED')) = (resolved_at IS NOT NULL))
);
COMMENT ON TABLE content_report IS 'ContentReport（通報）。内容への異論は含めない。理由・状態の値は案で、F10 で確定する';
CREATE INDEX content_report_open_idx ON content_report (received_at) WHERE status IN ('RECEIVED', 'IN_REVIEW');

-- =========================================================
-- 判断の記録の対象・根拠（論理設計 6.5節）
-- =========================================================

CREATE TABLE decision_target (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    decision_id            bigint NOT NULL REFERENCES decision (id),
    role                   text   NOT NULL CHECK (role IN ('SUBJECT', 'RESULT', 'COMPARED')),
    conversation_id        bigint REFERENCES conversation (id),
    post_id                bigint REFERENCES post (id),
    project_id             bigint REFERENCES project (id),
    discovery_id           bigint REFERENCES discovery (id),
    discovery_place_id     bigint REFERENCES discovery_place (id),
    discovery_value_id     bigint REFERENCES discovery_value (id),
    discovery_relation_id  bigint REFERENCES discovery_relation (id),
    discovery_candidate_id bigint REFERENCES discovery_candidate (id),
    relation_candidate_id  bigint REFERENCES relation_candidate (id),
    place_id               bigint REFERENCES place (id),
    place_candidate_id     bigint REFERENCES place_candidate (id),
    place_adoption_id      bigint REFERENCES place_adoption (id),
    open_question_id       bigint REFERENCES open_question (id),
    question_answer_id     bigint REFERENCES question_answer (id),
    content_report_id      bigint REFERENCES content_report (id),
    before_state           text,
    after_state            text,
    CHECK (num_nonnulls(conversation_id, post_id, project_id, discovery_id, discovery_place_id,
                        discovery_value_id, discovery_relation_id, discovery_candidate_id,
                        relation_candidate_id, place_id, place_candidate_id, place_adoption_id,
                        open_question_id, question_answer_id, content_report_id) = 1),
    CHECK (role <> 'COMPARED' OR discovery_id IS NOT NULL)
);
COMMENT ON TABLE decision_target IS '判断の記録の対象・結果・比べた既存の Discovery と、変える前・後の状態';
CREATE INDEX decision_target_decision_idx ON decision_target (decision_id);
CREATE INDEX decision_target_discovery_idx ON decision_target (discovery_id) WHERE discovery_id IS NOT NULL;
CREATE INDEX decision_target_post_idx ON decision_target (post_id) WHERE post_id IS NOT NULL;
CREATE INDEX decision_target_project_idx ON decision_target (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX decision_target_conversation_idx ON decision_target (conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX decision_target_place_adoption_idx ON decision_target (place_adoption_id) WHERE place_adoption_id IS NOT NULL;

CREATE TABLE decision_basis_post (
    decision_id  bigint NOT NULL REFERENCES decision (id),
    post_id      bigint NOT NULL REFERENCES post (id),
    PRIMARY KEY (decision_id, post_id)
);
COMMENT ON TABLE decision_basis_post IS '判断の記録に固めた根拠の Post（人が作ったものなので参照で固める）';
CREATE INDEX decision_basis_post_post_idx ON decision_basis_post (post_id);

CREATE TABLE decision_basis_snapshot (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    decision_id       bigint      NOT NULL REFERENCES decision (id),
    claim_id          bigint      REFERENCES claim (id),
    finding_id        bigint      REFERENCES finding (id),
    open_question_id  bigint      REFERENCES open_question (id),
    version           integer,
    content           text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(claim_id, finding_id, open_question_id) = 1),
    CHECK ((finding_id IS NULL) = (version IS NULL))
);
COMMENT ON TABLE decision_basis_snapshot IS '判断の記録に固めた見立て（主張・わかってきたこと・問い）の中身の写し。見立てが組み直されても判断の理由を失わない';
CREATE INDEX decision_basis_snapshot_decision_idx ON decision_basis_snapshot (decision_id);

-- =========================================================
-- 運用・画面の都合のデータ（設計書 3.11節）
-- =========================================================

CREATE TABLE user_conversation_read (
    user_id          bigint      NOT NULL REFERENCES app_user (id),
    conversation_id  bigint      NOT NULL REFERENCES conversation (id),
    read_until       timestamptz NOT NULL,
    PRIMARY KEY (user_id, conversation_id)
);
COMMENT ON TABLE user_conversation_read IS '既読（S09 の新しい動き）。論理 Entity にしない';

-- =========================================================
-- 導くもののビュー（設計書 2.4節）
-- =========================================================

-- 会話と場所の採用した紐付き（ConversationPlace）。正本は place_adoption。
-- 対象がその会話の採用と、その会話の問いへの採用（資料の撮影場所は二次開発で足す）
CREATE VIEW conversation_place AS
    SELECT pa.conversation_id, pa.place_id, pa.role, pa.seq, pa.id AS place_adoption_id, pa.status
      FROM place_adoption pa
     WHERE pa.conversation_id IS NOT NULL
       AND pa.status IN ('ADOPTED', 'RECONSIDERING')
    UNION ALL
    SELECT q.conversation_id, pa.place_id, NULL::text AS role, NULL::integer AS seq, pa.id, pa.status
      FROM place_adoption pa
      JOIN open_question q ON q.id = pa.open_question_id
     WHERE pa.status IN ('ADOPTED', 'RECONSIDERING');
COMMENT ON VIEW conversation_place IS 'ConversationPlace（導出）。行がない会話は「場所未定」';

-- =========================================================
-- 一般のユーザー向けの公開のビュー（設計書 2.5節）
-- 一般向けの読み取りはこれを通す。運営・本人の読み取りは表を直接読み、権限を API で確かめる
-- =========================================================

CREATE VIEW visible_conversation AS
    SELECT c.* FROM conversation c WHERE c.visibility = 'PUBLIC';

CREATE VIEW visible_post AS
    SELECT p.*
      FROM post p
      JOIN conversation c ON c.id = p.conversation_id
     WHERE p.visibility = 'PUBLIC' AND c.visibility = 'PUBLIC';

CREATE VIEW visible_discovery AS
    SELECT d.* FROM discovery d WHERE d.visibility = 'PUBLIC' AND d.withdrawn_at IS NULL;

CREATE VIEW visible_project AS
    SELECT pr.* FROM project pr WHERE pr.visibility = 'PUBLIC';

-- 指す先が見えない Discovery の項目を伏せて出すか外すかは S15 で決める。ここでは見えるものだけを返す
CREATE VIEW visible_project_item AS
    SELECT pi.*
      FROM project_item pi
      JOIN project pr ON pr.id = pi.project_id
     WHERE pr.visibility = 'PUBLIC'
       AND (pi.place_id IS NOT NULL
            OR EXISTS (SELECT 1 FROM visible_discovery d WHERE d.id = pi.discovery_id));

-- 変更の出来事は企画の公開状態に従う。動いた項目が指す Discovery の今の状態は、出すときに visible_discovery で確かめる
CREATE VIEW visible_project_change AS
    SELECT ch.*
      FROM project_change ch
      JOIN project pr ON pr.id = ch.project_id
     WHERE pr.visibility = 'PUBLIC';

CREATE VIEW visible_finding AS
    SELECT f.*
      FROM finding f
      JOIN conversation c ON c.id = f.conversation_id
     WHERE c.visibility = 'PUBLIC';

CREATE VIEW visible_open_question AS
    SELECT q.*
      FROM open_question q
      JOIN conversation c ON c.id = q.conversation_id
     WHERE c.visibility = 'PUBLIC';

CREATE VIEW visible_place_candidate AS
    SELECT pc.*
      FROM place_candidate pc
      LEFT JOIN open_question q ON q.id = pc.open_question_id
      JOIN conversation c ON c.id = coalesce(pc.conversation_id, q.conversation_id)
     WHERE c.visibility = 'PUBLIC';

COMMENT ON VIEW visible_post IS '公開の Post（Post と属する会話が公開）';
COMMENT ON VIEW visible_discovery IS '公開の Discovery（公開、かつ成立を取り消していない）';
COMMENT ON VIEW visible_project_item IS '公開の企画の項目のうち、指す先が見えるもの';
