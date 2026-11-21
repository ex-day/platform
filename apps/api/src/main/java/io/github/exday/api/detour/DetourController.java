package io.github.exday.api.detour;

import io.github.exday.api.generated.api.F06Api;
import io.github.exday.api.generated.model.NearbyStationList;
import io.github.exday.api.generated.model.StationList;
import io.github.exday.api.generated.model.StationsNearLocationRequest;
import io.github.exday.api.station.StationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * F06（寄り道の到達圏）のタグのエンドポイントの実装。
 *
 * <p>今は駅の選び方の3つだけを実装する。到達圏の取得（getDetourReach・getDetourReachPlaces）は、歩きのデータを #163 で
 * 決めてから実装し、それまでは生成した interface の既定（501 Not Implemented）のまま。
 * 生成した interface は1つのコントローラーだけが実装する（2つで実装すると同じ URL の割り当てが重なる）。
 */
@RestController
public class DetourController implements F06Api {

    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int DEFAULT_NEARBY_LIMIT = 5;

    private final StationService stations;

    public DetourController(StationService stations) {
        this.stations = stations;
    }

    @Override
    public ResponseEntity<StationList> searchStations(String q, Integer limit) {
        return ResponseEntity.ok(stations.search(q, limit == null ? DEFAULT_SEARCH_LIMIT : limit));
    }

    @Override
    public ResponseEntity<NearbyStationList> getPlaceNearbyStations(String placeId, Integer limit) {
        return ResponseEntity.ok(stations.nearPlace(placeId, limit == null ? DEFAULT_NEARBY_LIMIT : limit));
    }

    /** 現在地は本文で受ける（座標を URL に出さない）。保存せず、ログに出さない。 */
    @Override
    public ResponseEntity<NearbyStationList> findStationsNearLocation(StationsNearLocationRequest request) {
        Integer limit = request.getLimit();
        return ResponseEntity.ok(stations.nearLocation(request.getLatitude().doubleValue(),
            request.getLongitude().doubleValue(), limit == null ? DEFAULT_NEARBY_LIMIT : limit));
    }
}
