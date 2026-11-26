package io.github.exday.api.detour;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 登録・変更した場所の「駅から場所への歩き」を、版の作り直しを待たずに作り直す（#163 の 2026-11-26 の人の判断：
 * 登録した場所はすぐに寄り道に出す。F06「データの形」8）。
 *
 * <p>場所・種類・到達点・行き方の変更のトリガーが待ち行列（{@code reach.place_walk_job}）に場所を積み、ここが1件ずつ取り出して
 * {@code reach.refresh_place_walk} を呼ぶ。1件ごとに1つのトランザクションにする（取り出した行は終わりまでロックし、ほかの API は飛ばす）。
 * 失敗した場所は、同じトランザクションの中で試した回数を増やして後ろに回し、上限に達したら残す（監視で見る）。
 */
@Service
public class PlaceWalkRefresher {

    private static final Logger log = LoggerFactory.getLogger(PlaceWalkRefresher.class);

    private final PlaceWalkRefreshQueries queries;
    private final PlaceWalkRefreshProperties properties;
    private final TransactionTemplate transaction;
    private final TransactionTemplate savepoint;

    public PlaceWalkRefresher(PlaceWalkRefreshQueries queries, PlaceWalkRefreshProperties properties,
            PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.savepoint = new TransactionTemplate(transactionManager);
        this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    /**
     * 待っている場所を、1回の上限（{@code batch-size}）まで作り直す。作り直した（試した）数を返す。
     * この回を始める前に積まれた場所だけを行う（失敗して後ろに回した場所は、次の回に試す）。
     */
    public int refreshPending() {
        OffsetDateTime runStartedAt = transaction.execute(status -> {
            queries.deleteFailedBuildJobs();
            return queries.now();
        });
        int done = 0;
        while (done < properties.batchSize()) {
            Outcome outcome = refreshNext(runStartedAt);
            if (outcome == Outcome.EMPTY) {
                break;
            }
            done++;
        }
        return done;
    }

    private enum Outcome { DONE, FAILED, EMPTY }

    // 取り出した行のロックを持ったまま、作り直しをセーブポイントの中で行う。失敗したらセーブポイントまで戻し、
    // 同じトランザクションで試した回数を増やす（ロックを離してから別のトランザクションで更新すると、その間に
    // ほかの処理が取り出したり、新しい変更で積み直された行に古い失敗を書いたりしうる。PR #169 のレビュー）
    private Outcome refreshNext(OffsetDateTime runStartedAt) {
        return transaction.execute(status -> {
            var job = queries.lockNextJob(properties.maxAttempts(), runStartedAt);
            if (job.isEmpty()) {
                return Outcome.EMPTY;
            }
            try {
                savepoint.executeWithoutResult(inner -> {
                    queries.refresh(job.get());
                    queries.deleteJob(job.get());
                });
                return Outcome.DONE;
            } catch (DataAccessException e) {
                log.warn("駅から場所への歩きを作り直せなかった（版 {}、場所 {}）", job.get().buildId(), job.get().placeId(), e);
                String message = String.valueOf(e.getMostSpecificCause().getMessage());
                queries.failJob(job.get(), message.length() > 1000 ? message.substring(0, 1000) : message);
                return Outcome.FAILED;
            }
        });
    }
}
