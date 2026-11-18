package io.github.exday.api.estimate;

import io.github.exday.api.generated.model.PlaceEstimate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 場所の推定の値（OpenAPI の PlaceEstimate）。どの画面でも同じ選び方にする（S13 の「この場所のデータ」、S15 の項目の根拠）。
 *
 * <p>選び方：指標と出どころ（計算の方法）ごとに、いちばん新しく計算したもの（同じ時点なら新しい行）。
 * 並びは指標、出どころの順。複数の場所をまとめて1つの SQL で読む。
 */
@Component
public class PlaceEstimates {

    private static final String FIND_ESTIMATES = """
        SELECT DISTINCT ON (e.place_id, e.metric, e.method)
               e.place_id, e.metric, e.value, e.unit, e.method, e.method_version, e.computed_at
          FROM place_estimate e
         WHERE e.place_id IN (:placeIds)
         ORDER BY e.place_id, e.metric, e.method, e.computed_at DESC, e.id DESC
        """;

    private final JdbcClient jdbcClient;

    public PlaceEstimates(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 場所（DB の id）ごとの推定の値。値のない場所は入らない。 */
    public Map<Long, List<PlaceEstimate>> byPlace(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_ESTIMATES)
            .param("placeIds", List.copyOf(placeIds))
            .query((rs, rowNum) -> {
                var estimate = new PlaceEstimate()
                    .metric(rs.getString("metric"))
                    .value(rs.getDouble("value"))
                    .unit(rs.getString("unit"))
                    .method(rs.getString("method"))
                    .computedAt(rs.getObject("computed_at", OffsetDateTime.class));
                estimate.setMethodVersion(rs.getString("method_version"));
                return Map.entry(rs.getLong("place_id"), estimate);
            })
            .list().stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
}
