package io.github.exday.api.detour;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * {@link PlaceWalkRefresher} を決まった間隔で動かす（{@code exday.detour.walk-refresh.enabled} が true のとき）。
 * 前の回が終わってから {@code interval} 待つ（重ねて動かさない）。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "exday.detour.walk-refresh", name = "enabled", havingValue = "true")
public class PlaceWalkRefreshSchedule {

    private final PlaceWalkRefresher refresher;

    public PlaceWalkRefreshSchedule(PlaceWalkRefresher refresher) {
        this.refresher = refresher;
    }

    @Scheduled(fixedDelayString = "${exday.detour.walk-refresh.interval}")
    void refresh() {
        refresher.refreshPending();
    }
}
