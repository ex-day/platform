package io.github.exday.api.card.discovery;

import io.github.exday.api.generated.model.DiscoverySummary;
import io.github.exday.api.generated.model.PlaceRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Discovery Card（C03。OpenAPI の DiscoverySummary）を作る。どの画面の一覧でも同じ見せ方にする
 * （使う画面はコンポーネント一覧の C03）。
 *
 * <p>カードの見出しにする場所（出す位置の場所。DEC-0022）の決め方もここが持つ。
 * どの Discovery を集めるか（公開のものだけを選ぶこと）は呼び出し側が決め、ここでは選んだ Discovery の場所を
 * 1つの SQL でまとめて読む（Discovery ごとに問い合わせない）。
 */
@Component
public class DiscoveryCards {

    /** 出す位置の場所の役割（論理設計 4.5節、OpenAPI の DiscoveryPlaceRole）。 */
    public static final List<String> DISPLAY_ROLES = List.of("EXPERIENCE", "START");

    private static final String FIND_PLACES = """
        SELECT dp.discovery_id, p.public_id, p.name, dp.role
          FROM discovery_place dp
          JOIN place p ON p.id = dp.place_id
         WHERE dp.discovery_id IN (:discoveryIds) AND dp.status = :active
         ORDER BY dp.discovery_id, dp.seq NULLS LAST, dp.id
        """;

    /** カードの材料（呼び出し側が公開と確かめた Discovery）。 */
    public record DiscoveryRow(long id, String publicId, String title, String summary) {}

    /** Discovery に関わる場所（関わる場所の順番、識別子の順に並べて渡す）。 */
    public record RolePlace(String publicId, String name, String role) {}

    private final JdbcClient jdbcClient;

    public DiscoveryCards(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Discovery Card を Discovery の識別子ごとに返す。関わる場所が1件もない Discovery は、カードの約束
     * （出す位置の場所が1件以上）を守れないので返さない（場所のない Discovery は作らないが、念のため）。
     */
    public Map<Long, DiscoverySummary> cards(Collection<DiscoveryRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        var places = findPlaces(rows.stream().map(DiscoveryRow::id).distinct().toList());
        var cards = new HashMap<Long, DiscoverySummary>();
        for (var row : rows) {
            var display = displayPlaces(places.getOrDefault(row.id(), List.of()));
            if (display.isEmpty()) {
                continue;
            }
            cards.put(row.id(), new DiscoverySummary()
                .id(row.publicId())
                .title(row.title())
                .summary(row.summary())
                .displayPlaces(display));
        }
        return cards;
    }

    /** 出す位置の場所：役割が体験できる場所・始まりの場所。なければ関わる場所の最初のもの。 */
    public static List<PlaceRef> displayPlaces(List<RolePlace> places) {
        var display = places.stream()
            .filter(place -> DISPLAY_ROLES.contains(place.role()))
            .map(DiscoveryCards::toRef)
            .distinct()
            .toList();
        if (!display.isEmpty() || places.isEmpty()) {
            return display;
        }
        return List.of(toRef(places.get(0)));
    }

    private Map<Long, List<RolePlace>> findPlaces(List<Long> discoveryIds) {
        return jdbcClient.sql(FIND_PLACES)
            .param("discoveryIds", discoveryIds)
            .param("active", "ACTIVE")
            .query((rs, rowNum) -> Map.entry(rs.getLong("discovery_id"),
                new RolePlace(rs.getString("public_id"), rs.getString("name"), rs.getString("role"))))
            .list().stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static PlaceRef toRef(RolePlace place) {
        return new PlaceRef().id(place.publicId()).name(place.name());
    }
}
