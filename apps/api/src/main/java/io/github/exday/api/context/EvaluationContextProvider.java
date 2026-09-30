package io.github.exday.api.context;

import io.github.exday.api.generated.model.EvaluationContext;
import io.github.exday.api.generated.model.EvaluationContext.ClockSourceEnum;
import io.github.exday.api.generated.model.EvaluationContext.LocationSourceEnum;
import io.github.exday.api.generated.model.Location;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * OpenAPI の {@link EvaluationContext}（レスポンスの {@code context}）を組み立てる。
 *
 * <p>「今」は {@link Clock} Bean 経由で取り、テストで固定できるようにする。
 * 「今」と「いる場所」を固定したかどうかは {@link ExdayDevProperties} から決める。
 */
@Component
public class EvaluationContextProvider {

    private final Clock clock;
    private final ExdayDevProperties properties;

    public EvaluationContextProvider(Clock clock, ExdayDevProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public EvaluationContext current() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ClockSourceEnum clockSource =
            (properties.clock() != null && properties.clock().isFixed())
                ? ClockSourceEnum.FIXED
                : ClockSourceEnum.SYSTEM;

        boolean locationFixed =
            properties.location() != null && properties.location().isComplete();
        ExdayDevProperties.LocationProperties loc =
            locationFixed ? properties.location() : properties.defaultLocation();
        Location location = new Location(loc.name(), loc.lat(), loc.lng());
        LocationSourceEnum locationSource =
            locationFixed ? LocationSourceEnum.FIXED : LocationSourceEnum.DEFAULT;

        return new EvaluationContext(
            now,
            Seasons.of(now.getMonth()),
            location,
            clockSource,
            locationSource);
    }
}
