package io.github.exday.api.detour;

import io.github.exday.api.detour.DetourQueries.Build;
import io.github.exday.api.detour.DetourQueries.PlaceQuery;
import io.github.exday.api.detour.DetourQueries.PlaceRow;
import io.github.exday.api.detour.DetourQueries.ReachPlaceRow;
import io.github.exday.api.generated.model.DetourAccess;
import io.github.exday.api.generated.model.DetourBand;
import io.github.exday.api.generated.model.DetourCondition;
import io.github.exday.api.generated.model.DetourEnd;
import io.github.exday.api.generated.model.DetourPlace;
import io.github.exday.api.generated.model.DetourPlacePage;
import io.github.exday.api.generated.model.DetourReach;
import io.github.exday.api.generated.model.MultiPolygonGeometry;
import io.github.exday.api.generated.model.NotEnoughTimeDetourReach;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.PointGeometry;
import io.github.exday.api.generated.model.ReachableDetourReach;
import io.github.exday.api.generated.model.ReturnDetourEnd;
import io.github.exday.api.generated.model.StationDetourEnd;
import io.github.exday.api.generated.model.StopDetourEnd;
import io.github.exday.api.geometry.GeoJsonGeometries;
import io.github.exday.api.place.PlaceNotFoundException;
import io.github.exday.api.station.StationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 寄り道の到達圏（F06。docs/ui/functions/F06-detour-reach.md）。
 *
 * <p>計算の決まり：
 * <ul>
 *   <li>t1（移動を始める駅 → 寄り道先の駅）＝ 鉄道の分 ＋ 乗るたびの待ち。寄り道先の駅が移動を始める駅なら 0</li>
 *   <li>t2（寄り道先の駅 → 終わり）：戻るときは t1 と同じ（辺の上りと下りの分は同じ）、寄り道先で終わるときは 0、
 *       別の駅へ向かうときは到着の駅からの分 ＋ 待ち（寄り道先の駅が到着の駅なら 0）</li>
 *   <li>場所で過ごせる時間 ＝ 使える時間 − 行き（t1 ＋ 歩き）− 帰り（歩き ＋ t2。寄り道先で終わるときは 0）。分は切り上げる</li>
 * </ul>
 * 駅の時間（鉄道の探索）は Java の側で1回だけ求め、寄れる場所と地図の帯の SQL に配列で渡す。
 * 複数の SQL で1つの応答を作るので、1つの読み取りの時点（スナップショット）でそろえる（apps/api/AGENTS.md）。
 */
@Service
public class DetourReachService {

    /** 別の駅へ向かうとき、使える時間の中で着けなければ、この分まで探して寄り道をしないで着く時間を求める。 */
    private static final double DIRECT_SEARCH_LIMIT_MINUTES = 24 * 60;

    /** 使える時間の刻み（分。OpenAPI の multipleOf）。 */
    private static final int MINUTES_STEP = 15;

    private final DetourQueries queries;
    private final StationService stations;
    private final DetourProperties settings;

    public DetourReachService(DetourQueries queries, StationService stations, DetourProperties settings) {
        this.queries = queries;
        this.stations = stations;
        this.settings = settings;
    }

    /** 終わり方。{@code station} は別の駅へ向かうときだけ。 */
    private enum EndKind { RETURN, STOP, STATION }

    private record Calculation(Build build, PlaceRow from, EndKind end, PlaceRow to, int minutes, String toParam,
                               Integer directMinutes, boolean enoughTime, long[] stationIds, double[] t1, double[] t2) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DetourReach reach(String fromId, int minutes, String toParam, int limit) {
        var calc = calculate(fromId, minutes, toParam);
        var condition = condition(calc);
        if (!calc.enoughTime()) {
            return new NotEnoughTimeDetourReach()
                .kind(NotEnoughTimeDetourReach.KindEnum.NOT_ENOUGH_TIME)
                .condition(condition)
                .directMinutes(calc.directMinutes());
        }
        return new ReachableDetourReach()
            .kind(ReachableDetourReach.KindEnum.REACHABLE)
            .condition(condition)
            .directMinutes(calc.directMinutes())
            .bands(bands(calc))
            .places(page(calc, null, limit));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DetourPlacePage places(String fromId, int minutes, String toParam, String after, int limit) {
        var cursor = DetourCursor.decode(after);
        var calc = calculate(fromId, minutes, toParam);
        if (!calc.enoughTime()
            || !cursor.build().equals(calc.build().version())
            || !cursor.settings().equals(settings.digest())
            || !cursor.from().equals(fromId)
            || cursor.minutes() != minutes
            || !cursor.to().equals(toParam == null ? "" : toParam)) {
            throw new InvalidDetourCursorException();
        }
        return page(calc, cursor, limit);
    }

    private Calculation calculate(String fromId, int minutes, String toParam) {
        if (minutes % MINUTES_STEP != 0) {
            throw new InvalidDetourMinutesException();
        }
        var from = station(fromId, "移動を始める駅");
        EndKind end;
        PlaceRow to = null;
        if (toParam == null) {
            end = EndKind.RETURN;
        } else if ("none".equals(toParam)) {
            end = EndKind.STOP;
        } else {
            end = EndKind.STATION;
            to = station(toParam, "到着の駅");
        }
        var build = queries.activeBuild()
            .orElseThrow(() -> new IllegalStateException("到達圏のデータの版（ACTIVE）がない"));
        int wait = settings.waitMinutes();

        Map<Long, Double> fromTimes = queries.stationTimes(build.id(), from.id(), minutes);
        Map<Long, Double> toTimes = end == EndKind.STATION ? queries.stationTimes(build.id(), to.id(), minutes) : Map.of();

        int direct = 0;
        if (end == EndKind.STATION && to.id() != from.id()) {
            Double rail = toTimes.get(from.id());
            if (rail == null) {
                rail = queries.stationTimes(build.id(), to.id(), DIRECT_SEARCH_LIMIT_MINUTES).get(from.id());
            }
            if (rail == null) {
                throw new InvalidDetourStationException("到着の駅へ、移動を始める駅から鉄道で着けない。駅を選び直す。");
            }
            direct = (int) Math.ceil(rail + wait - 1e-9);
        }
        boolean enough = direct <= minutes;

        var ids = new ArrayList<Long>();
        var t1s = new ArrayList<Double>();
        var t2s = new ArrayList<Double>();
        for (var entry : fromTimes.entrySet()) {
            long placeId = entry.getKey();
            double t1 = placeId == from.id() ? 0 : entry.getValue() + wait;
            Double t2;
            if (end == EndKind.RETURN) {
                t2 = t1;
            } else if (end == EndKind.STOP || placeId == to.id()) {
                t2 = 0.0;
            } else {
                Double rail = toTimes.get(placeId);
                t2 = rail == null ? null : rail + wait;
            }
            // 歩きが0分でも寄れる時間の下限に届かない駅は、寄れる場所も帯も作らない
            if (t2 != null && t1 + t2 <= minutes - settings.minStayMinutes()) {
                ids.add(placeId);
                t1s.add(t1);
                t2s.add(t2);
            }
        }
        return new Calculation(build, from, end, to, minutes, toParam, direct, enough,
            ids.stream().mapToLong(Long::longValue).toArray(),
            t1s.stream().mapToDouble(Double::doubleValue).toArray(),
            t2s.stream().mapToDouble(Double::doubleValue).toArray());
    }

    /** 駅の場所を読む。ない場所は 404、駅でない・ACTIVE でない場所は 400。 */
    private PlaceRow station(String publicId, String label) {
        var place = queries.place(publicId).orElseThrow(() -> new PlaceNotFoundException(publicId));
        if (!place.station() || !"ACTIVE".equals(place.status())) {
            throw new InvalidDetourStationException(label + "が駅の場所でない。駅を選び直す。");
        }
        return place;
    }

    private DetourCondition condition(Calculation calc) {
        DetourEnd end = switch (calc.end()) {
            case RETURN -> new ReturnDetourEnd().kind(ReturnDetourEnd.KindEnum.RETURN);
            case STOP -> new StopDetourEnd().kind(StopDetourEnd.KindEnum.STOP);
            case STATION -> new StationDetourEnd()
                .kind(StationDetourEnd.KindEnum.STATION)
                .station(stations.station(calc.to().id(), calc.to().publicId(), calc.to().name()));
        };
        return new DetourCondition()
            .from(stations.station(calc.from().id(), calc.from().publicId(), calc.from().name()))
            .minutes(calc.minutes())
            .end(end);
    }

    private List<DetourBand> bands(Calculation calc) {
        var slack = new double[calc.stationIds().length];
        for (int i = 0; i < slack.length; i++) {
            slack[i] = calc.minutes() - calc.t1()[i] - calc.t2()[i];
        }
        return queries.bands(calc.stationIds(), slack, calc.end() == EndKind.STOP, settings).stream()
            .map(row -> new DetourBand()
                .minStayMinutes(row.minStay())
                .geometry((MultiPolygonGeometry) GeoJsonGeometries.parse(row.geometry())))
            .toList();
    }

    private DetourPlacePage page(Calculation calc, DetourCursor after, int limit) {
        var rows = queries.places(new PlaceQuery(calc.build().id(), calc.stationIds(), calc.t1(), calc.t2(),
            calc.end() == EndKind.STOP, calc.minutes(), settings.walkCapMinutes(), settings.minStayMinutes(),
            after == null ? null : after.stay(), after == null ? null : after.placeId(), limit + 1));
        boolean more = rows.size() > limit;
        var items = rows.stream().limit(limit).map(DetourReachService::place).toList();
        var page = new DetourPlacePage().items(items);
        if (more) {
            var last = rows.get(limit - 1);
            page.nextCursor(new DetourCursor(calc.build().version(), settings.digest(), calc.from().publicId(),
                calc.minutes(), calc.toParam() == null ? "" : calc.toParam(), last.stay(), last.publicId()).encode());
        }
        return page;
    }

    private static DetourPlace place(ReachPlaceRow row) {
        return new DetourPlace()
            .place(new PlaceRef().id(row.publicId()).name(row.name()))
            .kinds(row.kinds())
            .position((PointGeometry) GeoJsonGeometries.parse(row.position()))
            .stayMinutes(row.stay())
            .outboundMinutes(row.outbound())
            .returnMinutes(row.ret())
            .access(new DetourAccess()
                .station(new PlaceRef().id(row.stationPublicId()).name(row.stationName()))
                .mode(DetourAccess.ModeEnum.WALK)
                .minutes(row.walkMinutes())
                .estimated(row.estimated()));
    }
}
