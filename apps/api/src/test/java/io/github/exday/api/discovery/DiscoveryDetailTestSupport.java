package io.github.exday.api.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * S03（Discovery 詳細）API のテスト基盤。
 *
 * <p>可視性（visibility）を書き換える 404 のテストがあるため、
 * {@code SampleDataIntegrationTest} と違い、テストごとにサンプルデータを再投入して分離する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
abstract class DiscoveryDetailTestSupport {
    private static final Path DB_DIR = Path.of("../../db").toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse(new ImageFromDockerfile("exday-discovery-detail-db-test", false)
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

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:" + DB_DIR.resolve("migration"))
            .load().migrate();
    }

    @BeforeEach
    void seed() throws Exception {
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

    ResponseEntity<Map<String, Object>> get(String discoveryId) {
        return rest.exchange("/discoveries/" + discoveryId, HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    Map<String, Object> body(String discoveryId) {
        var response = get(discoveryId);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    ResponseEntity<Map<String, Object>> getPosts(String discoveryId) {
        return rest.exchange("/discoveries/" + discoveryId + "/posts", HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    Map<String, Object> postsBody(String discoveryId) {
        var response = getPosts(discoveryId);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }
}
