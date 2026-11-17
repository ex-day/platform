package io.github.exday.api.discovery;

import io.github.exday.api.generated.api.S03Api;
import io.github.exday.api.generated.model.DiscoveryDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S03（Discovery のページ）のエンドポイントの実装。 */
@RestController
public class DiscoveryDetailController implements S03Api {

    private final DiscoveryDetailService service;

    public DiscoveryDetailController(DiscoveryDetailService service) {
        this.service = service;
    }

    /** 一般のユーザーに見えない（ない・非公開・成立を取り消した）Discovery は 404。 */
    @Override
    public ResponseEntity<DiscoveryDetail> getDiscovery(String discoveryId) {
        return ResponseEntity.ok(service.find(discoveryId)
            .orElseThrow(() -> new DiscoveryNotFoundException(discoveryId)));
    }
}
