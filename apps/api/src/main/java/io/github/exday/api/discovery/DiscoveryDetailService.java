package io.github.exday.api.discovery;

import io.github.exday.api.generated.model.DiscoveryDetail;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S03 の応答を、1つの読み取りの時点（スナップショット）でそろえて作る。
 *
 * <p>公開のビューで見えた Discovery の識別子を使って、複数の SQL で区画を読む。途中で別の処理が非公開にしても
 * 区画ごとに食い違わないよう、REPEATABLE READ の読み取り専用トランザクションの中で読む（PostgreSQL では、
 * トランザクションの最初の問い合わせの時点のスナップショットを、すべての問い合わせで使う）。
 * 応答は、その時点の公開状態を表す（apps/api/AGENTS.md「読み取りの一貫性」）。
 */
@Service
public class DiscoveryDetailService {

    private final DiscoveryDetailQueries queries;
    private final DiscoveryDetailAssembler assembler;

    public DiscoveryDetailService(DiscoveryDetailQueries queries, DiscoveryDetailAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    /** 一般のユーザーに見えない（ない・非公開・成立を取り消した）Discovery は空。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<DiscoveryDetail> find(String discoveryId) {
        return queries.findDiscovery(discoveryId).map(assembler::assemble);
    }
}
