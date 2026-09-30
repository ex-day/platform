package io.github.exday.api.discovery;

import io.github.exday.api.generated.api.S03Api;
import io.github.exday.api.generated.model.DiscoveryDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S03（Discovery 詳細）のエンドポイントの実装。 */
@RestController
public class DiscoveryDetailController implements S03Api {

    private final DiscoveryDetailQueries queries;
    private final DiscoveryDetailAssembler assembler;

    public DiscoveryDetailController(DiscoveryDetailQueries queries, DiscoveryDetailAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    @Override
    public ResponseEntity<DiscoveryDetail> getDiscovery(String discoveryId) {
        var summary = queries.findSummary(discoveryId)
            .orElseThrow(() -> new DiscoveryNotFoundException(discoveryId));
        return ResponseEntity.ok(assembler.assemble(summary));
    }
}
