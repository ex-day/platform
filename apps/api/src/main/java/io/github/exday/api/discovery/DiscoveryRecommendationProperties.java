package io.github.exday.api.discovery;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "exday.recommendation")
public record DiscoveryRecommendationProperties(
    @DefaultValue("3000") @Positive double nearbyRadiusMeters) {}
