package io.github.exday.api.place;

import io.github.exday.api.generated.api.S13Api;
import io.github.exday.api.generated.model.PlaceDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S13（場所のページ）のエンドポイントの実装。 */
@RestController
public class PlaceController implements S13Api {

    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    /** ない場所は 404。 */
    @Override
    public ResponseEntity<PlaceDetail> getPlace(String placeId) {
        return ResponseEntity.ok(service.find(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId)));
    }
}
