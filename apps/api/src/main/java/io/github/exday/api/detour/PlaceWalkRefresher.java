package io.github.exday.api.detour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 登録・変更した場所の「駅から場所への歩き」を、版の作り直しを待たずに作り直す（#163 の 2026-11-26 の人の判断：
 * 登録した場所はすぐに寄り道に出す。F06「データの形」8）。
 *
 * <p>場所・種類・到達点・行き方の変更のトリガーが待ち行列（{@code reach.place_walk_job}）に場所を積み、ここが1件ずつ取り出して
 * {@code reach.refresh_place_walk} を呼ぶ。1件ごとに1つのトランザクションにする（取り出した行は終わりまでロックし、ほかの API は飛ばす）。
 * 失敗した場所は試した回数を増やして後ろに回し、上限に達したら残す（監視で見る）。
 */
@Service
public class PlaceWalkRefresher {

    private static final Logger log = LoggerFactory.getLogger(PlaceWalkRefresher.class);

    private final PlaceWalkRefreshQueries queries;
    private final PlaceWalkRefreshProperties properties;
    private final TransactionTemplate transaction;

    public PlaceWalkRefresher(PlaceWalkRefreshQueries queries, PlaceWalkRefreshProperties properties,
            PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 待っている場所を、1回の上限（{@code batch-size}）まで作り直す。作り直した数を返す。 */
    public int refreshPending() {
        transaction.executeWithoutResult(status -> queries.deleteFailedBuildJobs());
        int done = 0;
        while (done < properties.batchSize()) {
            Outcome outcome = refreshNext();
            if (outcome == Outcome.EMPTY) {
                break;
            }
            done++;
        }
        return done;
    }

    private enum Outcome { DONE, FAILED, EMPTY }

    private Outcome refreshNext() {
        PlaceWalkRefreshQueries.Job[] taken = new PlaceWalkRefreshQueries.Job[1];
        try {
            return transaction.execute(status -> {
                var job = queries.lockNextJob(properties.maxAttempts());
                if (job.isEmpty()) {
                    return Outcome.EMPTY;
                }
                taken[0] = job.get();
                queries.refresh(job.get());
                queries.deleteJob(job.get());
                return Outcome.DONE;
            });
        } catch (RuntimeException e) {
            if (taken[0] == null) {
                throw e;
            }
            log.warn("駅から場所への歩きを作り直せなかった（版 {}、場所 {}）", taken[0].buildId(), taken[0].placeId(), e);
            String message = String.valueOf(e.getMessage());
            transaction.executeWithoutResult(status ->
                queries.failJob(taken[0], message.length() > 1000 ? message.substring(0, 1000) : message));
            return Outcome.FAILED;
        }
    }
}
