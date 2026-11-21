package io.github.exday.api.station;

import io.github.exday.api.generated.model.NearbyStation;
import io.github.exday.api.generated.model.NearbyStationList;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.Station;
import io.github.exday.api.generated.model.StationLine;
import io.github.exday.api.generated.model.StationList;
import io.github.exday.api.merge.MergeChains;
import io.github.exday.api.place.PlaceNotFoundException;
import io.github.exday.api.station.StationQueries.LineRow;
import io.github.exday.api.station.StationQueries.StationRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 寄り道の移動を始める駅・到着の駅を選ぶための候補（F06「駅の選び方」。画面の補助で、F06 の入力の決まりではない）。
 *
 * <p>距離の順は候補の表示だけに使い、自動で決めたり「おすすめ」としたりしない。駅と路線は2つの SQL で読むので、
 * 1つの読み取りの時点（スナップショット）でそろえる（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 */
@Service
public class StationService {

    /** 1つの駅に出す路線の上限（OpenAPI の Station.lines の maxItems）。 */
    static final int MAX_LINES = 30;

    private final StationQueries queries;
    private final MergeChains mergeChains;

    public StationService(StationQueries queries, MergeChains mergeChains) {
        this.queries = queries;
        this.mergeChains = mergeChains;
    }

    /** 駅の名前で探す。{@code q} は前後の空白と末尾の「駅」を除いて比べ、空になれば {@link InvalidStationQueryException}。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StationList search(String q, int limit) {
        String name = normalize(q);
        return new StationList().items(stations(queries.searchByName(name, limit)));
    }

    /**
     * 場所のまわりの駅。ない場所は {@link PlaceNotFoundException}。統合された場所は最後の統合先のまわりを返す
     * （最後の統合先を決められない循環のときは、その場所のまわり）。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NearbyStationList nearPlace(String placeId, int limit) {
        long id = queries.placeId(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        var resolution = mergeChains.places(List.of(id)).get(id);
        long target = resolution != null && resolution.resolved() ? resolution.finalId() : id;
        return nearby(queries.nearPlace(target, limit));
    }

    /** 現在地のまわりの駅。座標は保存せず、ログにも出さない。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NearbyStationList nearLocation(double latitude, double longitude, int limit) {
        return nearby(queries.nearLocation(latitude, longitude, limit));
    }

    /** 前後の空白を除き、末尾の「駅」を除く（「駅」だけなら「駅」のまま）。空なら 400。 */
    static String normalize(String q) {
        String trimmed = q == null ? "" : q.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidStationQueryException();
        }
        if (trimmed.length() > 1 && trimmed.endsWith("駅")) {
            return trimmed.substring(0, trimmed.length() - 1).strip();
        }
        return trimmed;
    }

    private NearbyStationList nearby(List<StationRow> rows) {
        var stations = stations(rows);
        var items = new ArrayList<NearbyStation>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            items.add(new NearbyStation().station(stations.get(i)).distanceMeters(rows.get(i).distanceMeters()));
        }
        return new NearbyStationList().items(items);
    }

    private List<Station> stations(List<StationRow> rows) {
        Map<Long, List<StationLine>> lines = queries.lines(rows.stream().map(StationRow::id).toList()).stream()
            .collect(Collectors.groupingBy(LineRow::placeId,
                Collectors.mapping(line -> new StationLine().operator(line.operator()).name(line.name()),
                    Collectors.toList())));
        return rows.stream()
            .map(row -> new Station()
                .place(new PlaceRef().id(row.publicId()).name(row.name()))
                .lines(lines.getOrDefault(row.id(), List.of()).stream().limit(MAX_LINES).toList()))
            .toList();
    }
}
