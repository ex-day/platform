-- ex-day：閲覧用（S01・S03）のテーブル（Issue #89）
--
-- 論理 Entity の設計（docs/ex-day_logical_entity_design.md）のうち、S01・S03 の取得用 API
-- （docs/api/openapi.yaml）が読むものだけを物理テーブルにする。対応と省いたものは
-- docs/ex-day_db_design.md を参照。
--
-- 方針
-- - 主キーは内部の連番（bigint）。画面・URL・API には出さない。
-- - API に出す識別子は public_id（推測できないランダムな文字列）。
-- - 区分値は PostgreSQL の enum ではなく text と CHECK 制約で持つ（値を足しやすくするため）。
-- - 日時は timestamptz。

CREATE EXTENSION IF NOT EXISTS postgis;

-- =========================================================
-- User（4.1）
-- =========================================================

CREATE TABLE app_user (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status      text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE app_user IS 'User。user は予約語のため app_user とする';

CREATE TABLE user_profile (
    user_id     bigint      PRIMARY KEY REFERENCES app_user (id),
    nickname    text        NOT NULL CHECK (length(nickname) BETWEEN 1 AND 50),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE user_profile IS 'UserProfile。声の表示名は、表示のたびにここの今のニックネームを引く（Issue #74）';

-- =========================================================
-- Discovery（4.2）
-- =========================================================

CREATE TABLE discovery (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id             text        NOT NULL UNIQUE CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    title                 text        NOT NULL,
    summary               text        NOT NULL DEFAULT '',
    establishment_status  text        NOT NULL DEFAULT 'UNESTABLISHED'
                                      CHECK (establishment_status IN ('UNESTABLISHED', 'ESTABLISHED')),
    visibility            text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    established_at        timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CHECK ((establishment_status = 'ESTABLISHED') = (established_at IS NOT NULL))
);
COMMENT ON COLUMN discovery.public_id IS 'API・URL に出す識別子。英数字12文字のランダムな文字列';
COMMENT ON COLUMN discovery.summary IS 'S03 の本文（現在の要約）';

CREATE TABLE discovery_operator_tag (
    discovery_id  bigint NOT NULL REFERENCES discovery (id),
    label         text   NOT NULL,
    PRIMARY KEY (discovery_id, label)
);
COMMENT ON TABLE discovery_operator_tag IS '運営が付けたタグ（Issue #57、DEC-0009 決定10）。Discovery のタグは、これと属する声のタグを集めて求める';

-- =========================================================
-- Place（7.1）
-- =========================================================

CREATE TABLE place (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text      NOT NULL,
    spatial_type  text      NOT NULL CHECK (spatial_type IN ('POINT', 'AREA', 'ROUTE')),
    geom          geography NOT NULL,
    CHECK (
        (spatial_type = 'POINT' AND GeometryType(geom) = 'POINT')
     OR (spatial_type = 'AREA'  AND GeometryType(geom) IN ('POLYGON', 'MULTIPOLYGON'))
     OR (spatial_type = 'ROUTE' AND GeometryType(geom) = 'LINESTRING')
    )
);
COMMENT ON COLUMN place.geom IS 'WGS84（SRID 4326）。「この近く」の絞り込みに使う';
CREATE INDEX place_geom_idx ON place USING gist (geom);

CREATE TABLE discovery_place (
    discovery_id  bigint  NOT NULL REFERENCES discovery (id),
    place_id      bigint  NOT NULL REFERENCES place (id),
    is_primary    boolean NOT NULL DEFAULT false,
    PRIMARY KEY (discovery_id, place_id)
);
COMMENT ON COLUMN discovery_place.is_primary IS 'カード・S03 に表示する代表の場所';
CREATE UNIQUE INDEX discovery_place_primary_idx ON discovery_place (discovery_id) WHERE is_primary;

-- =========================================================
-- Post（4.3）
-- =========================================================

CREATE TABLE post (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     text        NOT NULL UNIQUE CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    discovery_id  bigint      NOT NULL REFERENCES discovery (id),
    user_id       bigint      NOT NULL REFERENCES app_user (id),
    body          text        NOT NULL DEFAULT '',
    visibility    text        NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    posted_at     timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN post.discovery_id IS '投稿先（器）の Discovery。1つだけ（DEC-0009）';
COMMENT ON COLUMN post.body IS '本文。#タグを含め、ユーザーが書いたまま';
CREATE INDEX post_discovery_idx ON post (discovery_id, posted_at);

CREATE TABLE post_reference (
    from_post_id  bigint      NOT NULL REFERENCES post (id),
    to_post_id    bigint      NOT NULL REFERENCES post (id),
    kind          text        NOT NULL DEFAULT 'REPLY' CHECK (kind IN ('REPLY')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (from_post_id, to_post_id),
    CHECK (from_post_id <> to_post_id)
);
COMMENT ON TABLE post_reference IS 'PostReference（4.3.1）。今は返信だけ';

CREATE TABLE post_tag (
    post_id  bigint NOT NULL REFERENCES post (id),
    label    text   NOT NULL,
    PRIMARY KEY (post_id, label)
);
COMMENT ON TABLE post_tag IS 'PostTag（4.3.2）。本文中の #タグを、書いたままの表記で持つ';

-- =========================================================
-- Media（8.1）
-- =========================================================

CREATE TABLE media (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      text        NOT NULL UNIQUE CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    discovery_id   bigint      NOT NULL REFERENCES discovery (id),
    post_id        bigint      REFERENCES post (id),
    kind           text        NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO', 'PDF', 'OTHER')),
    name           text        NOT NULL,
    url            text        NOT NULL,
    alt            text        NOT NULL DEFAULT '',
    source_status  text        NOT NULL DEFAULT 'UNSET' CHECK (source_status IN ('UNSET', 'SELF', 'REGISTERED', 'UNKNOWN')),
    source_type    text        CHECK (source_type IN ('WEBSITE', 'BOOK', 'DOCUMENT', 'PROVIDED', 'OTHER')),
    source_name    text,
    created_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN media.discovery_id IS '提供先の Discovery（DEC-0005）';
COMMENT ON COLUMN media.post_id IS 'ともに提供された声（任意）';

-- =========================================================
-- Value・Condition（4.2.1・4.2.2）
-- =========================================================

CREATE TABLE discovery_value (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          text        NOT NULL UNIQUE CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    discovery_id       bigint      NOT NULL REFERENCES discovery (id),
    subject_label      text        NOT NULL,
    summary            text        NOT NULL,
    body               text        NOT NULL DEFAULT '',
    picture_media_id   bigint      REFERENCES media (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE discovery_value IS 'Value。value は SQL の予約語に近いため discovery_value とする';
COMMENT ON COLUMN discovery_value.subject_label IS '何についての価値か（例：桜並木）。Subject との対応は後続設計（4.2.1 要検討）';
COMMENT ON COLUMN discovery_value.summary IS 'カードに出す短い説明';

CREATE TABLE value_condition (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    value_id    bigint NOT NULL REFERENCES discovery_value (id),
    axis        text   NOT NULL CHECK (axis IN ('SEASON')),
    season      text   CHECK (season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER', 'ALL_YEAR')),
    CHECK (axis <> 'SEASON' OR season IS NOT NULL),
    UNIQUE (value_id, axis, season)
);
COMMENT ON TABLE value_condition IS 'Condition。今は季節の軸だけ（DEC-0002）。行がない軸は、その軸に依存しない（4.2.2）';

CREATE TABLE value_presentation (
    value_id               bigint      PRIMARY KEY REFERENCES discovery_value (id),
    message                text        NOT NULL,
    participation_message  text,
    generated_by           text        NOT NULL CHECK (generated_by IN ('SAMPLE', 'AI')),
    generated_at           timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE value_presentation IS
    'Hero（C19）のメッセージ。価値の組み合わせと会話の文脈を AI で解析した結果の文言を想定する見立て。サンプルでは用意した文言（SAMPLE）';

-- =========================================================
-- Finding（6A.3）
-- =========================================================

CREATE TABLE finding (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     text        NOT NULL UNIQUE CHECK (public_id ~ '^[0-9A-Za-z]{12}$'),
    discovery_id  bigint      NOT NULL REFERENCES discovery (id),
    kind          text        NOT NULL CHECK (kind IN ('VALUE', 'THEORY')),
    text          text        NOT NULL,
    backed_by     text,
    status        text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MERGED', 'SPLIT', 'EXPIRED')),
    display_order integer     NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CHECK (backed_by IS NULL OR kind = 'THEORY')
);
COMMENT ON COLUMN finding.discovery_id IS
    'S03 で表示する Discovery。論理設計では話題（Topic）・発見の仮説を介してつながるが、今回はそれらを省き直接持つ';
COMMENT ON COLUMN finding.backed_by IS '裏付けのある説の出典名。説だけが持つ。ある説にはリアクションを付けない（DEC-0009 決定9）';

CREATE TABLE finding_source_post (
    finding_id  bigint NOT NULL REFERENCES finding (id),
    post_id     bigint NOT NULL REFERENCES post (id),
    PRIMARY KEY (finding_id, post_id)
);
COMMENT ON TABLE finding_source_post IS 'Finding の出所の声（寄与ではなく出所。DEC-0009）';

-- =========================================================
-- DiscoveryRelation（6.3）と、派生の見立て
-- =========================================================

CREATE TABLE discovery_relation (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_discovery_id  bigint      NOT NULL REFERENCES discovery (id),
    to_discovery_id    bigint      NOT NULL REFERENCES discovery (id),
    relation_type      text        NOT NULL CHECK (relation_type IN ('DERIVED', 'RELATED')),
    description        text,
    origin_post_id     bigint      REFERENCES post (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (from_discovery_id, to_discovery_id, relation_type),
    CHECK (from_discovery_id <> to_discovery_id),
    CHECK (origin_post_id IS NULL OR relation_type = 'DERIVED')
);
COMMENT ON COLUMN discovery_relation.from_discovery_id IS 'DERIVED のときは派生元。RELATED のときは向きを持たない';
COMMENT ON COLUMN discovery_relation.origin_post_id IS
    '派生が確定した位置。この声の直後に C26 の派生の目印を挟む（一度出したら動かさない）';

CREATE TABLE post_continuation (
    post_id       bigint NOT NULL PRIMARY KEY REFERENCES post (id),
    discovery_id  bigint NOT NULL REFERENCES discovery (id)
);
COMMENT ON TABLE post_continuation IS
    '派生した話の続きと見立てた声と、その派生先（C26「『〇〇』で続いています」）。F02 の見立てのため、再解析で付け直してよい';

-- =========================================================
-- Reaction（4.4）
-- 対象ごとにテーブルを分け、外部キーで対象を守る
-- =========================================================

CREATE TABLE discovery_reaction (
    discovery_id   bigint      NOT NULL REFERENCES discovery (id),
    user_id        bigint      NOT NULL REFERENCES app_user (id),
    reaction_type  text        NOT NULL CHECK (reaction_type IN ('LIKE', 'SURPRISED', 'LOVE')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (discovery_id, user_id, reaction_type)
);

CREATE TABLE post_reaction (
    post_id        bigint      NOT NULL REFERENCES post (id),
    user_id        bigint      NOT NULL REFERENCES app_user (id),
    reaction_type  text        NOT NULL DEFAULT 'EMPATHY' CHECK (reaction_type IN ('EMPATHY')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id, reaction_type)
);
COMMENT ON TABLE post_reaction IS '声への共感。問いを投げた声への「自分も気になる」も含む';

CREATE TABLE finding_reaction (
    finding_id     bigint      NOT NULL REFERENCES finding (id),
    user_id        bigint      NOT NULL REFERENCES app_user (id),
    reaction_type  text        NOT NULL CHECK (reaction_type IN ('UNDERSTAND', 'WANT_TO_GO', 'MAYBE')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (finding_id, user_id, reaction_type)
);
COMMENT ON TABLE finding_reaction IS
    '価値は UNDERSTAND・WANT_TO_GO、裏付けのない説は MAYBE。裏付けのある説には付けない（アプリで守る）。対象の版は後続設計';
