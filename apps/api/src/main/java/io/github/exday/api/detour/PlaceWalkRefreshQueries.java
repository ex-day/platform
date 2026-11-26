package io.github.exday.api.detour;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 登録・変更した場所の「駅から場所への歩き」を作り直す（DB V5 の待ち行列 {@code reach.place_walk_job} と
 * {@code reach.refresh_place_walk}）ための SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 */
@Repository
public class PlaceWalkRefreshQueries {

    // 1回の中では、その回を始める前に積まれた行だけを取り出す（失敗して後ろに回した行を、同じ回ですぐに試し直さない）。
    // 作っている途中（BUILDING）の版の分は、READY になるまで取り出さない。使っている版（ACTIVE）を先に行う。
    // ほかの処理が取り出し中の行は飛ばす（API を複数動かしても同じ行を二重に行わない）
    private static final String NEXT_JOB = """
        SELECT j.build_id, j.place_id
          FROM reach.place_walk_job j
          JOIN reach.build b ON b.id = j.build_id
         WHERE b.status IN ('READY', 'ACTIVE', 'RETIRED')
           AND j.attempts < :maxAttempts
           AND j.requested_at <= :runStartedAt
         ORDER BY (b.status = 'ACTIVE') DESC, j.requested_at, j.build_id, j.place_id
         LIMIT 1
           FOR UPDATE OF j SKIP LOCKED
        """;

    private static final String NOW = """
        SELECT clock_timestamp()
        """;

    private static final String REFRESH = """
        SELECT reach.refresh_place_walk(:buildId, :placeId)
        """;

    // 取り出している間に同じ場所が変わったときは、変更のトリガーがこの行の削除を待ってから積み直す（もう一度作り直す）
    private static final String DELETE_JOB = """
        DELETE FROM reach.place_walk_job WHERE build_id = :buildId AND place_id = :placeId
        """;

    private static final String FAIL_JOB = """
        UPDATE reach.place_walk_job
           SET attempts = attempts + 1, last_error = :error, requested_at = now()
         WHERE build_id = :buildId AND place_id = :placeId
        """;

    // 失敗した版（FAILED）は使わないので、その分は捨てる
    private static final String DELETE_FAILED_BUILD_JOBS = """
        DELETE FROM reach.place_walk_job j
         USING reach.build b
         WHERE b.id = j.build_id AND b.status = 'FAILED'
        """;

    /** 作り直す場所（版ごと）。 */
    record Job(long buildId, long placeId) {}

    private final JdbcClient jdbcClient;

    public PlaceWalkRefreshQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** DB の今の時刻（1回を始めた時刻にする。アプリと DB の時計のずれに頼らない）。 */
    OffsetDateTime now() {
        return jdbcClient.sql(NOW).query(OffsetDateTime.class).single();
    }

    /** 次に作り直す場所（{@code runStartedAt} までに積まれたもの）を取り出し、トランザクションの終わりまで行をロックする。 */
    Optional<Job> lockNextJob(int maxAttempts, OffsetDateTime runStartedAt) {
        return jdbcClient.sql(NEXT_JOB)
            .param("maxAttempts", maxAttempts)
            .param("runStartedAt", runStartedAt)
            .query((rs, rowNum) -> new Job(rs.getLong("build_id"), rs.getLong("place_id")))
            .optional();
    }

    /** 作った行の数。歩きのグラフの無い版（サンプルの手で作った版）では何もせず空。 */
    Optional<Integer> refresh(Job job) {
        var rows = jdbcClient.sql(REFRESH)
            .param("buildId", job.buildId())
            .param("placeId", job.placeId())
            .query((rs, rowNum) -> rs.getObject(1, Integer.class))
            .list();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    void deleteJob(Job job) {
        jdbcClient.sql(DELETE_JOB)
            .param("buildId", job.buildId())
            .param("placeId", job.placeId())
            .update();
    }

    void failJob(Job job, String error) {
        jdbcClient.sql(FAIL_JOB)
            .param("buildId", job.buildId())
            .param("placeId", job.placeId())
            .param("error", error)
            .update();
    }

    int deleteFailedBuildJobs() {
        return jdbcClient.sql(DELETE_FAILED_BUILD_JOBS).update();
    }
}
