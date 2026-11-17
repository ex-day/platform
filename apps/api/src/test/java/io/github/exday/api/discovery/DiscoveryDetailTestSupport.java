package io.github.exday.api.discovery;

import io.github.exday.api.DbApiTestSupport;
import java.util.Map;
import org.springframework.http.ResponseEntity;

/** S03（Discovery のページ）API のテストの基盤。 */
abstract class DiscoveryDetailTestSupport extends DbApiTestSupport {

    ResponseEntity<Map<String, Object>> get(String discoveryId) {
        return getJson("/discoveries/" + discoveryId);
    }

    Map<String, Object> body(String discoveryId) {
        return okJson("/discoveries/" + discoveryId);
    }
}
