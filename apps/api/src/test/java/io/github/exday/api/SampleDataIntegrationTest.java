package io.github.exday.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * DB につないだ土台の確認。
 *
 * <p>{@code db/Dockerfile} と同じ構成（PostgreSQL 18 ＋ PostGIS）の DB を Testcontainers で立て、
 * {@code db/migration} と {@code db/sample} を Flyway で流したうえで、JdbcClient でサンプルデータを
 * 読めることと、PostGIS の関数が使えることを確かめる（Issue #89）。
 */
@SpringBootTest
@Testcontainers
class SampleDataIntegrationTest {

    /** リポジトリの db/ ディレクトリ。テストは apps/api で実行される想定。 */
    private static final Path DB_DIR = Path.of("..", "..", "db").toAbsolutePath().normalize();

    /** {@code db/Dockerfile} からビルドしたイメージ名。PostgreSQL 18 ＋ PostGIS ＋ pgvector を含む。 */
    private static final String DB_IMAGE_NAME = new ImageFromDockerfile("exday-db-test", false)
            .withDockerfile(DB_DIR.resolve("Dockerfile"))
            .get();

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(DB_IMAGE_NAME).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("exday")
            .withUsername("exday")
            .withPassword("exday");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void migrateAndSeed() {
        // 本番・ローカルでは compose の flyway コンテナが流すが、テストの中では
        // 同じマイグレーションとサンプルデータを Flyway の Java API で流す。
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(
                        "filesystem:" + DB_DIR.resolve("migration"),
                        "filesystem:" + DB_DIR.resolve("sample"))
                .load()
                .migrate();
    }

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void 公開中のDiscoveryの件数がサンプルデータどおりに読める() {
        // サンプルデータ（db/sample/R__sample_data.sql）で PUBLIC の Discovery を 11 件入れている。
        long count = SampleQueries.countPublicDiscoveries(jdbcClient);
        assertThat(count).isEqualTo(11L);
    }

    @Test
    void PostGISの関数が使える() {
        // 大岡川（桜木町駅〜蒔田）と 山下公園（氷川丸）の代表点の距離（メートル）が
        // 正の値として求められることを確かめる。値そのものは概算で、桁の範囲で確認する。
        double meters = SampleQueries.distanceBetweenPlaces(
                jdbcClient, "大岡川（桜木町駅〜蒔田）", "山下公園（氷川丸）");
        assertThat(meters).isGreaterThan(0.0);
        assertThat(meters).isLessThan(50_000.0);
    }
}
