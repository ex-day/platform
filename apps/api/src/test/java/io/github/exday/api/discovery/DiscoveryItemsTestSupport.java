package io.github.exday.api.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import java.nio.file.Files;
import java.sql.DriverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
abstract class DiscoveryItemsTestSupport {
    private static final Path DB_DIR = Path.of("../../db").toAbsolutePath().normalize();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse(new ImageFromDockerfile("exday-items-db-test", false)
            .withDockerfile(DB_DIR.resolve("Dockerfile")).get())
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcClient jdbc;
    @Autowired DiscoveryCandidateQueries queries;
    @Autowired io.github.exday.api.context.EvaluationContextProvider context;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:" + DB_DIR.resolve("migration"))
            .load().migrate();
    }

    @BeforeEach
    void seed() throws Exception {
        // スキーマや PostGIS を再作成せず、データだけを毎回リセットする。
        // pg_temp の補助関数を使うため、サンプルは専用接続の1トランザクションで流す。
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute(Files.readString(DB_DIR.resolve("sample/R__sample_data.sql")));
            }
            connection.commit();
        }
    }

    ResponseEntity<Map<String, Object>> get(String suffix) {
        return rest.exchange("/discovery-sections/" + suffix, HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items(String suffix) {
        var response = get(suffix);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody().get("items");
    }
}
