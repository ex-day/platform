package io.github.exday.api;

import io.github.exday.api.context.ExdayDevProperties;
import io.github.exday.api.discovery.DiscoveryRecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {ExdayDevProperties.class, DiscoveryRecommendationProperties.class})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
