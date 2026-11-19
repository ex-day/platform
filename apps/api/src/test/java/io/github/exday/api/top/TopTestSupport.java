package io.github.exday.api.top;

import io.github.exday.api.DbApiTestSupport;
import java.util.List;
import java.util.Map;

/** S01（GET /top）のテストの部品。 */
@SuppressWarnings("unchecked")
abstract class TopTestSupport extends DbApiTestSupport {

    static List<Map<String, Object>> sections(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("sections");
    }

    static List<Object> kinds(Map<String, Object> body) {
        return sections(body).stream().map(section -> section.get("kind")).toList();
    }

    static Map<String, Object> section(Map<String, Object> body, String kind) {
        return sections(body).stream().filter(section -> kind.equals(section.get("kind"))).findFirst().orElseThrow();
    }

    static List<Map<String, Object>> items(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("items");
    }

    /** TOP の Discovery のカードの Discovery の識別子。 */
    static Object discoveryId(Map<String, Object> card) {
        return ((Map<String, Object>) card.get("discovery")).get("id");
    }

    /** 枠の Discovery の識別子（いま・この時期は card の中）。 */
    static List<Object> discoveryIds(Map<String, Object> section) {
        return items(section).stream()
            .map(item -> item.containsKey("card") ? (Map<String, Object>) item.get("card") : item)
            .map(TopTestSupport::discoveryId)
            .toList();
    }

    /** 場所の枠（声を待っている）の場所の識別子。 */
    static List<Object> placeIds(Map<String, Object> section) {
        return items(section).stream().map(item -> ((Map<String, Object>) item.get("place")).get("id")).toList();
    }

    static Map<String, Object> ref(String key, String name) {
        return Map.of("id", sid(key), "name", name);
    }
}
