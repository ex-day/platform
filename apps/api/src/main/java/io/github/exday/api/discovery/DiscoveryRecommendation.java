package io.github.exday.api.discovery;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryRecommendation {
    // 仮の評価：季節が一致する候補を優先し、その中で距離が近い順にする。
    // SQL の候補取得と分離し、今後の推薦度の評価に差し替えられるようにする。
    // 同点は公開 ID で固定し、limit の境目がリクエストごとに揺れないようにする。
    private static final Comparator<DiscoveryCandidate> ORDER =
        Comparator.comparing(DiscoveryCandidate::seasonal).reversed()
            .thenComparingDouble(DiscoveryCandidate::distanceMeters)
            .thenComparing(DiscoveryCandidate::discoveryId)
            .thenComparing(DiscoveryCandidate::valueId);

    public List<DiscoveryCandidate> select(List<DiscoveryCandidate> candidates, int limit) {
        return candidates.stream().sorted(ORDER).limit(limit).toList();
    }
}
