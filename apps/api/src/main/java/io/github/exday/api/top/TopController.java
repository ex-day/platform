package io.github.exday.api.top;

import io.github.exday.api.generated.api.S01Api;
import io.github.exday.api.generated.model.TopPage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S01（TOP）のエンドポイントの実装。 */
@RestController
public class TopController implements S01Api {

    private final TopService service;

    public TopController(TopService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<TopPage> getTop(String from) {
        return ResponseEntity.ok(service.find(from));
    }
}
