package io.github.exday.api;

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
import org.testcontainers.utility.DockerImageName;

/**
 * DB を使う API のテストの基盤（S03・S13 等で共用する）。
 *
 * <p>公開状態や統合を書き換えるテストがあるため、
 * {@code SampleDataIntegrationTest} と違い、テストごとにサンプルデータを再投入して分離する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class DbApiTestSupport {
    private static final Path DB_DIR = Path.of("../../db").toAbsolutePath().normalize();

    /**
     * テストのクラスをまたいで1つの DB を使う（Spring のテストのコンテキストはクラスをまたいで再利用されるため、
     * クラスごとに DB を作り直すと、再利用されたコンテキストが止まった DB を指してしまう）。
     * 止めるのは Testcontainers（Ryuk）に任せる。
     */
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse(new ImageFromDockerfile("exday-api-db-test", false)
            .withDockerfile(DB_DIR.resolve("Dockerfile")).get())
            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected JdbcClient jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:" + DB_DIR.resolve("migration"))
            .load().migrate();
    }

    @BeforeEach
    protected void seed() throws Exception {
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

    /** GET して JSON を Map で受け取る。 */
    protected ResponseEntity<Map<String, Object>> getJson(String path) {
        return rest.exchange(path, HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /** GET して 200 であることを確かめ、JSON を返す。 */
    protected Map<String, Object> okJson(String path) {
        var response = getJson(path);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    /** サンプルデータの public_id（db/sample/R__sample_data.sql の pg_temp.sid と同じ作り方）。 */
    protected static String sid(String key) {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5")
                .digest(("ex-day-sample:" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** テストのデータを足すときの判断の記録（運営の SQL での補い）。 */
    protected long operatorDecision(String kind) {
        return jdbc.sql("""
                INSERT INTO decision (kind, workflow, method, actor_kind, actor_user_id)
                SELECT :kind, 'OPERATOR_SQL', 'HUMAN', 'OPERATOR', user_id FROM user_profile WHERE nickname = 'ex-day 運営'
                RETURNING id
                """)
            .param("kind", kind)
            .query(Long.class).single();
    }

    /** from の Discovery を to に統合する（統合の関係を足す）。 */
    protected void merge(String fromKey, String toKey) {
        long decision = operatorDecision("MERGE");
        jdbc.sql("""
                INSERT INTO discovery_relation (from_discovery_id, to_discovery_id, kind, created_decision_id)
                SELECT f.id, t.id, 'MERGED', :decision FROM discovery f, discovery t
                 WHERE f.public_id = :from AND t.public_id = :to
                """)
            .param("decision", decision).param("from", sid(fromKey)).param("to", sid(toKey))
            .update();
    }
}
