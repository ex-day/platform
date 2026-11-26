package io.github.exday.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;
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
 *
 * <p>DB V2（Issue #135）では、V1 から V2 までのマイグレーションとサンプルデータが Flyway で通ることと、
 * 制約・ビューの確認（{@code db/check/v2_checks.sql}）が PostgreSQL 18 ＋ PostGIS の上で通ることも確かめる。
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
        // 場所の歩きを作り直す処理は動かさない（制約の確認のスクリプトと混ざらないように）
        registry.add("exday.detour.walk-refresh.enabled", () -> "false");
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
    void 一般に見えるDiscoveryの件数がサンプルデータどおりに読める() {
        // サンプルデータ（db/sample/R__sample_data.sql）で Discovery を 5 件入れ、1 件は成立を取り消している。
        long count = SampleQueries.countPublicDiscoveries(jdbcClient);
        assertThat(count).isEqualTo(4L);
    }

    @Test
    void PostGISの関数が使える() {
        // 汽車道（経路）と山下公園（地点）の距離（メートル）が正の値として求められることを確かめる。
        // 値そのものはサンプルの概算の形によるので、桁の範囲で確認する。
        double meters = SampleQueries.distanceBetweenPlaces(jdbcClient, "汽車道", "山下公園");
        assertThat(meters).isGreaterThan(0.0);
        assertThat(meters).isLessThan(50_000.0);
    }

    @Test
    void 制約とビューの確認が通る() throws Exception {
        runCheck("check/v2_checks.sql");
    }

    @Test
    void 駅と行き方の制約の確認が通る() throws Exception {
        runCheck("check/v3_checks.sql");
    }

    @Test
    void 到達圏の版とグラフと歩きの制約の確認が通る() throws Exception {
        runCheck("check/v4_checks.sql");
    }

    @Test
    void 歩きのグラフと場所ごとの作り直しと呼び名の確認が通る() throws Exception {
        runCheck("check/v5_checks.sql");
    }

    @Test
    void 探索用の路線の確認が通る() throws Exception {
        runCheck("check/v6_checks.sql");
    }

    /** リポジトリの tools/reach/。版を作る SQL（build_service.sql）を、小さなグラフで流して確かめる（PR #173 のレビュー）。 */
    private static final Path REACH_DIR = DB_DIR.resolveSibling("tools").resolve("reach");

    @Test
    void 系統を版に足すSQLが直通_乗換_通過地点_環状_置き換えを作る() throws Exception {
        runScripts(REACH_DIR.resolve("tests/service_graph.sql"), REACH_DIR.resolve("tests/service_case_ok.sql"),
                REACH_DIR.resolve("sql/build_service.sql"), REACH_DIR.resolve("tests/service_case_ok_assert.sql"));
    }

    @Test
    void 置き換えで外した区間への接続を失うと系統を版に足すSQLが失敗する() {
        assertThatThrownBy(() -> runScripts(REACH_DIR.resolve("tests/service_graph.sql"),
                REACH_DIR.resolve("tests/service_case_lost.sql"), REACH_DIR.resolve("sql/build_service.sql")))
            .hasMessageContaining("外した区間の駅どうし");
    }

    // 1つの接続で、ファイルをつないで流す（一時の表を引き継ぐ）。最初のファイルが BEGIN し、最後が ROLLBACK する
    // （失敗したときは接続を閉じてロールバックする）
    private static void runScripts(Path... paths) throws Exception {
        var script = new StringBuilder();
        for (Path path : paths) {
            script.append(Files.readString(path)).append('\n');
        }
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(script.toString());
        }
    }

    // db/check の SQL は psql で流す前提のため、psql のメタコマンド（\set・\echo）の行を除いて流す。
    // 失敗すると RAISE EXCEPTION（NG: ...）で SQLException になる。最後に ROLLBACK するのでデータは変わらない。
    private static void runCheck(String path) throws Exception {
        String script = Files.readAllLines(DB_DIR.resolve(path)).stream()
                .filter(line -> !line.startsWith("\\"))
                .collect(Collectors.joining("\n"));
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }
}
