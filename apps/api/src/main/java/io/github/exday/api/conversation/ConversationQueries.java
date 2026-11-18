package io.github.exday.api.conversation;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S14（会話のページ）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>会話は公開のビュー（{@code visible_conversation}）で見えると確かめてから、中身を読む。会話の中のもの（Post・候補・問い・
 * わかってきたこと）は、会話が公開なら一般のユーザーに出す（親が子を含む関係。DB 設計 2.5節）。
 * 非公開の Post は枠だけを出す（本文・書いた人は組み立ての側で出さない）。会話の外を指すもの（入口・回答の参照先・統合先・
 * 生まれた発見）は、指す先の公開のビューで確かめる。
 *
 * <p>同じ時点のものは識別子（public_id）の文字コードの順に並べる（OpenAPI の「同じなら識別子の順」）。
 */
@Repository
public class ConversationQueries {

    private static final String FIND_CONVERSATION = """
        SELECT c.id, c.public_id, c.created_at, c.public_post_count,
               c.entry_place_id, c.entry_discovery_id, c.entry_project_id, c.is_project_own
          FROM visible_conversation c
         WHERE c.public_id = :publicId
        """;

    private static final String FIND_PLACE_REF = """
        SELECT public_id, name FROM place WHERE id = :id
        """;

    private static final String FIND_PROJECT_REF = """
        SELECT public_id, title FROM visible_project WHERE id = :id
        """;

    private static final String FIND_DISCOVERY_REF = """
        SELECT public_id, title FROM visible_discovery WHERE id = :id
        """;

    // 場所の状態：会話の採用と、会話の問いの回答の採用（conversation_place と同じ範囲）
    private static final String FIND_PLACES = """
        SELECT p.public_id, p.name, pa.role, pa.seq, pa.status, pa.open_question_id IS NOT NULL AS from_question
          FROM place_adoption pa
          JOIN place p ON p.id = pa.place_id
          LEFT JOIN open_question q ON q.id = pa.open_question_id
         WHERE pa.status IN (:activeAdoption)
           AND (pa.conversation_id = :conversationId OR q.conversation_id = :conversationId)
         ORDER BY pa.seq NULLS LAST, pa.adopted_at, pa.id
        """;

    // ---- Post ----

    // Post の中身は PostCards（C26）。ここでは並びのキー（時点・識別子）で選ぶだけ
    private static final String POST_KEYS = """
        SELECT p.id, p.public_id
          FROM post p
        """;

    private static final String POSTS_BEFORE_KEY = """
        AND (p.posted_at < :beforeAt OR (p.posted_at = :beforeAt AND p.public_id COLLATE "C" < :beforePublicId))""";

    private static final String FINDINGS_AFTER_KEY = """
        AND (f.created_at > :afterAt OR (f.created_at = :afterAt AND f.public_id COLLATE "C" > :afterPublicId))""";

    private static final String FIND_FIRST_POST = POST_KEYS + """
         WHERE p.conversation_id = :conversationId
         ORDER BY p.posted_at, p.public_id COLLATE "C"
         LIMIT 1
        """;

    // 最初の Post を除いて、キー（時点・識別子）より前の Post を新しい側から。キーがなければ最新から
    private static final String FIND_POSTS_BEFORE = POST_KEYS + """
         WHERE p.conversation_id = :conversationId
           AND p.id <> :firstPostId
           %s
         ORDER BY p.posted_at DESC, p.public_id COLLATE "C" DESC
         LIMIT :limit
        """;

    private static final String FIND_POST_KEY = """
        SELECT p.posted_at, p.public_id FROM post p WHERE p.public_id = :publicId AND p.conversation_id = :conversationId
        """;

    // ---- 場所の候補と採用（C33） ----

    // 候補ごとの今の採用：採用中・再検討中があればそれ（最新）、なければ最後に取り消された採用。
    // adopted（今の採用がある）は全部、ほかは検討中（採用されたことのないもの）を先に上限まで
    private static final String FIND_CANDIDATES = """
        WITH cand AS (
            SELECT pc.*, q.public_id AS question_public_id
              FROM visible_place_candidate pc
              LEFT JOIN open_question q ON q.id = pc.open_question_id
             WHERE pc.conversation_id = :conversationId OR q.conversation_id = :conversationId
        ), current_adoption AS (
            SELECT DISTINCT ON (pa.candidate_id) pa.candidate_id, pa.status, pa.actor_kind, pa.seq, pa.revoke_reason
              FROM place_adoption pa
             WHERE pa.candidate_id IN (SELECT id FROM cand)
             ORDER BY pa.candidate_id, (pa.status IN (:activeAdoption)) DESC,
                      coalesce(pa.revoked_at, pa.adopted_at) DESC, pa.id DESC
        ), ranked AS (
            SELECT c.*, a.status AS adoption_status, a.actor_kind, a.seq AS adoption_seq, a.revoke_reason,
                   coalesce(a.status IN (:activeAdoption), false) AS adopted,
                   CASE WHEN c.status = :considering AND a.candidate_id IS NULL THEN 0 ELSE 1 END AS other_rank
              FROM cand c
              LEFT JOIN current_adoption a ON a.candidate_id = c.id
        ), numbered AS (
            SELECT r.*,
                   row_number() OVER (PARTITION BY r.adopted
                                      ORDER BY CASE WHEN r.adopted THEN 0 ELSE r.other_rank END,
                                               CASE WHEN r.adopted THEN r.adoption_seq END NULLS LAST,
                                               r.created_at, r.public_id COLLATE "C") AS rn,
                   count(*) OVER (PARTITION BY r.adopted) AS section_total
              FROM ranked r
        )
        SELECT n.id, n.public_id, n.status, n.question_public_id, n.adopted, n.adoption_status, n.actor_kind,
               n.revoke_reason, n.section_total,
               coalesce(p.public_id, cp.public_id) AS place_public_id, coalesce(p.name, cp.name) AS place_name,
               n.proposed_name, n.proposed_spatial_type,
               ST_AsGeoJSON(n.proposed_geom::geometry, :decimals) AS proposed_geojson,
               n.proposed_kinds, n.proposed_existed_from, n.proposed_existed_to,
               (SELECT array_agg(bp.public_id ORDER BY b.added_at, b.id)
                  FROM place_candidate_basis b
                  JOIN post bp ON bp.id = b.post_id
                  JOIN conversation bc ON bc.id = bp.conversation_id AND bc.visibility = 'PUBLIC'
                 WHERE b.candidate_id = n.id) AS basis_post_ids,
               (SELECT count(*) FROM place_candidate_voice v WHERE v.candidate_id = n.id AND v.stance = 'SUPPORT') AS support,
               (SELECT count(*) FROM place_candidate_voice v WHERE v.candidate_id = n.id AND v.stance = 'OPPOSE') AS oppose
          FROM numbered n
          LEFT JOIN place p ON p.id = n.place_id
          LEFT JOIN place cp ON cp.id = n.created_place_id
         WHERE n.adopted OR n.rn <= :othersLimit
         ORDER BY n.adopted DESC, n.rn
        """;

    // ---- 問いと回答の採用（C34） ----

    // 回答：場所の採用（採用中・再検討中の最新）、なければ回答の採用（同じ）。answered（回答がある）は全部、ほかは有効を先に上限まで
    private static final String FIND_QUESTIONS = """
        WITH qs AS (
            SELECT q.* FROM visible_open_question q
             WHERE q.conversation_id = :conversationId AND q.status IN (:questionStatuses)
        ), place_answer AS (
            SELECT DISTINCT ON (pa.open_question_id) pa.open_question_id, pa.status, pa.actor_kind,
                   p.public_id AS place_public_id, p.name AS place_name
              FROM place_adoption pa
              JOIN place p ON p.id = pa.place_id
             WHERE pa.open_question_id IN (SELECT id FROM qs) AND pa.status IN (:activeAdoption)
             ORDER BY pa.open_question_id, pa.adopted_at DESC, pa.id DESC
        ), other_answer AS (
            SELECT DISTINCT ON (a.question_id) a.question_id, a.status, a.actor_kind,
                   a.answer_post_id, a.answer_finding_id, a.answer_finding_version
              FROM question_answer a
             WHERE a.question_id IN (SELECT id FROM qs) AND a.status IN (:activeAdoption)
             ORDER BY a.question_id, a.adopted_at DESC, a.id DESC
        ), ranked AS (
            SELECT q.id, q.public_id, q.content, q.kind, q.status, q.created_at,
                   q.asker_user_id IS NOT NULL AS asked_by_person, s.keep_open,
                   (SELECT count(*) FROM reaction r WHERE r.open_question_id = q.id AND r.kind = 'CURIOUS') AS curious,
                   pa.status AS place_answer_status, pa.actor_kind AS place_answer_actor,
                   pa.place_public_id, pa.place_name,
                   oa.status AS other_answer_status, oa.actor_kind AS other_answer_actor,
                   oa.answer_post_id, oa.answer_finding_id, oa.answer_finding_version,
                   (pa.open_question_id IS NOT NULL OR oa.question_id IS NOT NULL) AS answered
              FROM qs q
              LEFT JOIN open_question_setting s ON s.question_id = q.id
              LEFT JOIN place_answer pa ON pa.open_question_id = q.id
              LEFT JOIN other_answer oa ON oa.question_id = q.id
        ), numbered AS (
            SELECT r.*,
                   row_number() OVER (PARTITION BY r.answered
                                      ORDER BY CASE WHEN r.answered OR r.status = :active THEN 0 ELSE 1 END,
                                               r.created_at, r.public_id COLLATE "C") AS rn,
                   count(*) OVER (PARTITION BY r.answered) AS section_total
              FROM ranked r
        )
        SELECT * FROM numbered n
         WHERE n.answered OR n.rn <= :othersLimit
         ORDER BY n.answered DESC, n.rn
        """;

    private static final String FIND_ANSWER_POSTS = """
        SELECT p.id, p.public_id, c.public_id AS conversation_public_id
          FROM post p
          JOIN visible_conversation c ON c.id = p.conversation_id
         WHERE p.id IN (:ids)
        """;

    // わかってきたことの公開（今は属する会話の公開から決まる）と、属する会話
    private static final String FIND_VISIBLE_FINDINGS = """
        SELECT f.id, f.public_id, c.public_id AS conversation_public_id, c.id AS conversation_id
          FROM visible_finding f
          JOIN conversation c ON c.id = f.conversation_id
         WHERE f.id IN (:ids)
        """;

    private static final String FIND_FINDING_VERSIONS = """
        SELECT finding_id, version, content FROM finding_version WHERE (finding_id, version) IN (:pairs)
        """;

    private static final String FIND_VISIBLE_QUESTIONS = """
        SELECT q.id, q.public_id, c.public_id AS conversation_public_id, c.id AS conversation_id
          FROM visible_open_question q
          JOIN conversation c ON c.id = q.conversation_id
         WHERE q.id IN (:ids)
        """;

    private static final String FIND_MERGED_QUESTIONS = """
        SELECT q.id, q.public_id FROM visible_open_question q
         WHERE q.conversation_id = :conversationId AND q.status = 'MERGED'
         ORDER BY q.created_at, q.public_id COLLATE "C"
        """;

    // ---- わかってきたこと（C25） ----

    private static final String FIND_FINDING_IDS = """
        SELECT f.id, f.public_id
          FROM visible_finding f
         WHERE f.conversation_id = :conversationId AND f.status = :active
           %s
         ORDER BY f.created_at, f.public_id COLLATE "C"
         LIMIT :limit
        """;

    private static final String COUNT_FINDINGS = """
        SELECT count(*) FROM visible_finding f WHERE f.conversation_id = :conversationId AND f.status = :active
        """;

    // カーソルのわかってきたことは、統合・失効していても並びのキーとして使う（この会話のものに限る）
    private static final String FIND_FINDING_KEY = """
        SELECT f.created_at, f.public_id FROM finding f WHERE f.public_id = :publicId AND f.conversation_id = :conversationId
        """;

    private static final String FIND_MERGED_FINDINGS = """
        SELECT f.id, f.public_id FROM visible_finding f
         WHERE f.conversation_id = :conversationId AND f.status = 'MERGED'
         ORDER BY f.created_at, f.public_id COLLATE "C"
        """;

    // ---- この会話から生まれた発見（C03） ----

    private static final String FIND_ORIGIN_DISCOVERY_IDS = """
        SELECT DISTINCT o.discovery_id FROM discovery_origin o WHERE o.conversation_id = :conversationId
        """;

    // カードにできる Discovery：公開で、関わる場所がある（DiscoveryCards の出す位置の場所が1件以上になる条件）
    private static final String FIND_VISIBLE_DISCOVERIES = """
        SELECT d.id, d.public_id, d.title, d.summary
          FROM visible_discovery d
         WHERE d.id IN (:ids)
           AND EXISTS (SELECT 1 FROM discovery_place dp WHERE dp.discovery_id = d.id AND dp.status = 'ACTIVE')
         ORDER BY d.established_at DESC, d.public_id COLLATE "C"
        """;

    // ---- この会話が届くところ ----

    // S13 の「このあたりで探している人がいます」（PlaceQueries.FIND_SEEKING）と同じ当てはめを、会話の側から引く：
    // 検討中の候補で、対象の会話に場所が決まっていない、または対象の問いが有効で回答が採用されていないもの。
    // 既存の場所の候補はその場所、新しい場所の案は、案の位置を含むエリアの場所
    private static final String FIND_REACH = """
        WITH matched AS (
            SELECT pc.*
              FROM visible_place_candidate pc
              LEFT JOIN open_question q ON q.id = pc.open_question_id
             WHERE pc.status = :considering
               AND (pc.conversation_id = :conversationId OR q.conversation_id = :conversationId)
               AND (
                    (pc.conversation_id IS NOT NULL
                     AND NOT EXISTS (SELECT 1 FROM conversation_place cp WHERE cp.conversation_id = pc.conversation_id))
                 OR (pc.open_question_id IS NOT NULL AND q.status = :active
                     AND NOT EXISTS (SELECT 1 FROM place_adoption a
                                      WHERE a.open_question_id = pc.open_question_id AND a.status IN (:activeAdoption)))
               )
        ), hits AS (
            SELECT m.place_id, 0 AS rank, 0::float8 AS area FROM matched m WHERE m.place_id IS NOT NULL
            UNION ALL
            SELECT p.id, 1, ST_Area(p.geom)
              FROM matched m
              JOIN place p ON p.spatial_type = 'AREA' AND ST_Covers(p.geom, m.proposed_geom)
             WHERE m.place_id IS NULL AND m.proposed_geom IS NOT NULL
        ), best AS (
            SELECT DISTINCT ON (place_id) place_id, rank, area FROM hits ORDER BY place_id, rank, area
        )
        SELECT p.public_id, p.name, b.rank, count(*) OVER () AS total
          FROM best b
          JOIN place p ON p.id = b.place_id
         ORDER BY b.rank, b.area, p.public_id COLLATE "C"
         LIMIT :limit
        """;

    /** 上限（OpenAPI の maxItems。固定）。 */
    static final int OTHER_CANDIDATES_LIMIT = 50;
    static final int OTHER_QUESTIONS_LIMIT = 20;
    static final int MOVED_QUESTIONS_LIMIT = 20;
    static final int MOVED_FINDINGS_LIMIT = 50;
    static final int DISCOVERY_LIMIT = 10;
    static final int REACH_LIMIT = 20;

    private static final int GEOJSON_DECIMALS = 7;
    private static final String ACTIVE = "ACTIVE";
    private static final List<String> ACTIVE_ADOPTION = List.of("ADOPTED", "RECONSIDERING");

    record ConversationRow(long id, String publicId, OffsetDateTime createdAt, int publicPostCount,
            Long entryPlaceId, Long entryDiscoveryId, Long entryProjectId, boolean projectOwn) {}

    record Ref(String publicId, String name) {}

    record PlaceRow(String publicId, String name, String role, Integer seq, String status, boolean fromQuestion) {}

    record Key(OffsetDateTime at, String publicId) {}

    record CandidateRow(String publicId, String status, String questionPublicId, boolean adopted,
            String adoptionStatus, String actorKind, String revokeReason, int sectionTotal,
            String placePublicId, String placeName, String proposedName, String proposedSpatialType,
            String proposedGeoJson, List<String> proposedKinds, LocalDate proposedExistedFrom,
            LocalDate proposedExistedTo, List<String> basisPostIds, int support, int oppose) {}

    record QuestionRow(String publicId, String content, String kind, String status, boolean askedByPerson,
            Boolean keepOpen, int curious, String placeAnswerStatus, String placeAnswerActor, String placePublicId,
            String placeName, String otherAnswerStatus, String otherAnswerActor, Long answerPostId,
            Long answerFindingId, Integer answerFindingVersion, boolean answered, int sectionTotal) {}

    record IdRef(long id, String publicId) {}

    record OwnedRef(long id, String publicId, long conversationId, String conversationPublicId) {}

    /** 回答として採用したわかってきたことの版（わかってきたことと版の番号の組）。 */
    record FindingVersion(long findingId, int version) {}

    record VersionRow(long findingId, int version, String content) {}

    record DiscoveryRow(long id, String publicId, String title, String summary) {}

    record ReachRow(String publicId, String name, boolean candidate, int total) {}

    private final JdbcClient jdbcClient;

    public ConversationQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ConversationRow> findConversation(String publicId) {
        return jdbcClient.sql(FIND_CONVERSATION)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new ConversationRow(rs.getLong("id"), rs.getString("public_id"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getInt("public_post_count"),
                nullableLong(rs, "entry_place_id"), nullableLong(rs, "entry_discovery_id"),
                nullableLong(rs, "entry_project_id"), rs.getBoolean("is_project_own")))
            .optional();
    }

    public Optional<Ref> findPlaceRef(long placeId) {
        return ref(FIND_PLACE_REF, placeId, "name");
    }

    public Optional<Ref> findProjectRef(long projectId) {
        return ref(FIND_PROJECT_REF, projectId, "title");
    }

    public Optional<Ref> findDiscoveryRef(long discoveryId) {
        return ref(FIND_DISCOVERY_REF, discoveryId, "title");
    }

    private Optional<Ref> ref(String sql, long id, String nameColumn) {
        return jdbcClient.sql(sql)
            .param("id", id)
            .query((rs, rowNum) -> new Ref(rs.getString("public_id"), rs.getString(nameColumn)))
            .optional();
    }

    public List<PlaceRow> findPlaces(long conversationId) {
        return jdbcClient.sql(FIND_PLACES)
            .param("conversationId", conversationId)
            .param("activeAdoption", ACTIVE_ADOPTION)
            .query((rs, rowNum) -> new PlaceRow(rs.getString("public_id"), rs.getString("name"), rs.getString("role"),
                nullableInt(rs, "seq"), rs.getString("status"), rs.getBoolean("from_question")))
            .list();
    }

    public Optional<IdRef> findFirstPost(long conversationId) {
        return jdbcClient.sql(FIND_FIRST_POST)
            .param("conversationId", conversationId)
            .query((rs, rowNum) -> new IdRef(rs.getLong("id"), rs.getString("public_id")))
            .optional();
    }

    /** キーより前の Post を新しい側から最大 {@code limit} 件（キーが null なら最新から）。最初の Post は含めない。 */
    public List<IdRef> findPostsBefore(long conversationId, long firstPostId, Key before, int limit) {
        var spec = jdbcClient.sql(FIND_POSTS_BEFORE.formatted(before == null ? "" : POSTS_BEFORE_KEY))
            .param("conversationId", conversationId)
            .param("firstPostId", firstPostId)
            .param("limit", limit);
        if (before != null) {
            spec = spec.param("beforeAt", before.at()).param("beforePublicId", before.publicId());
        }
        return spec.query((rs, rowNum) -> new IdRef(rs.getLong("id"), rs.getString("public_id"))).list();
    }

    public Optional<Key> findPostKey(long conversationId, String publicId) {
        return jdbcClient.sql(FIND_POST_KEY)
            .param("conversationId", conversationId)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new Key(rs.getObject("posted_at", OffsetDateTime.class), rs.getString("public_id")))
            .optional();
    }

    public List<CandidateRow> findCandidates(long conversationId) {
        return jdbcClient.sql(FIND_CANDIDATES)
            .param("conversationId", conversationId)
            .param("activeAdoption", ACTIVE_ADOPTION)
            .param("considering", "CONSIDERING")
            .param("othersLimit", OTHER_CANDIDATES_LIMIT)
            .param("decimals", GEOJSON_DECIMALS)
            .query((rs, rowNum) -> new CandidateRow(rs.getString("public_id"), rs.getString("status"),
                rs.getString("question_public_id"), rs.getBoolean("adopted"), rs.getString("adoption_status"),
                rs.getString("actor_kind"), rs.getString("revoke_reason"), rs.getInt("section_total"),
                rs.getString("place_public_id"), rs.getString("place_name"), rs.getString("proposed_name"),
                rs.getString("proposed_spatial_type"), rs.getString("proposed_geojson"),
                strings(rs.getArray("proposed_kinds")), rs.getObject("proposed_existed_from", LocalDate.class),
                rs.getObject("proposed_existed_to", LocalDate.class), strings(rs.getArray("basis_post_ids")),
                rs.getInt("support"), rs.getInt("oppose")))
            .list();
    }

    public List<QuestionRow> findQuestions(long conversationId) {
        return jdbcClient.sql(FIND_QUESTIONS)
            .param("conversationId", conversationId)
            .param("questionStatuses", List.of(ACTIVE, "EXPIRED"))
            .param("activeAdoption", ACTIVE_ADOPTION)
            .param("active", ACTIVE)
            .param("othersLimit", OTHER_QUESTIONS_LIMIT)
            .query((rs, rowNum) -> new QuestionRow(rs.getString("public_id"), rs.getString("content"),
                rs.getString("kind"), rs.getString("status"), rs.getBoolean("asked_by_person"),
                (Boolean) rs.getObject("keep_open"), rs.getInt("curious"), rs.getString("place_answer_status"),
                rs.getString("place_answer_actor"), rs.getString("place_public_id"), rs.getString("place_name"),
                rs.getString("other_answer_status"), rs.getString("other_answer_actor"),
                nullableLong(rs, "answer_post_id"), nullableLong(rs, "answer_finding_id"),
                nullableInt(rs, "answer_finding_version"), rs.getBoolean("answered"), rs.getInt("section_total")))
            .list();
    }

    /** 属する会話が公開の Post（Post 自身が非公開でも返す）。 */
    public List<OwnedRef> findAnswerPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(FIND_ANSWER_POSTS)
            .param("ids", List.copyOf(postIds))
            .query((rs, rowNum) -> new OwnedRef(rs.getLong("id"), rs.getString("public_id"), 0,
                rs.getString("conversation_public_id")))
            .list();
    }

    /** 一般のユーザーに公開できるわかってきたこと（状態は問わない）。 */
    public List<OwnedRef> findVisibleFindings(Collection<Long> findingIds) {
        return owned(FIND_VISIBLE_FINDINGS, findingIds);
    }

    /** 一般のユーザーに公開できる問い（状態は問わない）。 */
    public List<OwnedRef> findVisibleQuestions(Collection<Long> questionIds) {
        return owned(FIND_VISIBLE_QUESTIONS, questionIds);
    }

    private List<OwnedRef> owned(String sql, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(sql)
            .param("ids", List.copyOf(ids))
            .query((rs, rowNum) -> new OwnedRef(rs.getLong("id"), rs.getString("public_id"),
                rs.getLong("conversation_id"), rs.getString("conversation_public_id")))
            .list();
    }

    /** 回答として採用された（わかってきたこと, 版）の組の内容だけを、まとめて読む。 */
    public List<VersionRow> findFindingVersions(Collection<FindingVersion> adopted) {
        if (adopted.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(FIND_FINDING_VERSIONS)
            .param("pairs", adopted.stream().map(v -> new Object[] {v.findingId(), v.version()}).toList())
            .query((rs, rowNum) -> new VersionRow(rs.getLong("finding_id"), rs.getInt("version"), rs.getString("content")))
            .list();
    }

    public List<IdRef> findMergedQuestions(long conversationId) {
        return idRefs(FIND_MERGED_QUESTIONS, conversationId);
    }

    public List<IdRef> findMergedFindings(long conversationId) {
        return idRefs(FIND_MERGED_FINDINGS, conversationId);
    }

    private List<IdRef> idRefs(String sql, long conversationId) {
        return jdbcClient.sql(sql)
            .param("conversationId", conversationId)
            .query((rs, rowNum) -> new IdRef(rs.getLong("id"), rs.getString("public_id")))
            .list();
    }

    /** キーより後の有効なわかってきたことを最大 {@code limit} 件（キーが null なら最初から）。 */
    public List<IdRef> findFindingIds(long conversationId, Key after, int limit) {
        var spec = jdbcClient.sql(FIND_FINDING_IDS.formatted(after == null ? "" : FINDINGS_AFTER_KEY))
            .param("conversationId", conversationId)
            .param("active", ACTIVE)
            .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterAt", after.at()).param("afterPublicId", after.publicId());
        }
        return spec.query((rs, rowNum) -> new IdRef(rs.getLong("id"), rs.getString("public_id"))).list();
    }

    public int countFindings(long conversationId) {
        return jdbcClient.sql(COUNT_FINDINGS)
            .param("conversationId", conversationId)
            .param("active", ACTIVE)
            .query(Integer.class)
            .single();
    }

    public Optional<Key> findFindingKey(long conversationId, String publicId) {
        return jdbcClient.sql(FIND_FINDING_KEY)
            .param("conversationId", conversationId)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new Key(rs.getObject("created_at", OffsetDateTime.class), rs.getString("public_id")))
            .optional();
    }

    public List<Long> findOriginDiscoveryIds(long conversationId) {
        return jdbcClient.sql(FIND_ORIGIN_DISCOVERY_IDS)
            .param("conversationId", conversationId)
            .query(Long.class)
            .list();
    }

    /** 公開の Discovery を、成立の新しい順に。 */
    public List<DiscoveryRow> findVisibleDiscoveries(Collection<Long> discoveryIds) {
        if (discoveryIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(FIND_VISIBLE_DISCOVERIES)
            .param("ids", List.copyOf(discoveryIds))
            .query((rs, rowNum) -> new DiscoveryRow(rs.getLong("id"), rs.getString("public_id"),
                rs.getString("title"), rs.getString("summary")))
            .list();
    }

    public List<ReachRow> findReach(long conversationId) {
        return jdbcClient.sql(FIND_REACH)
            .param("conversationId", conversationId)
            .param("considering", "CONSIDERING")
            .param("active", ACTIVE)
            .param("activeAdoption", ACTIVE_ADOPTION)
            .param("limit", REACH_LIMIT)
            .query((rs, rowNum) -> new ReachRow(rs.getString("public_id"), rs.getString("name"),
                rs.getInt("rank") == 0, rs.getInt("total")))
            .list();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        try {
            return List.of((String[]) array.getArray());
        } finally {
            array.free();
        }
    }
}
