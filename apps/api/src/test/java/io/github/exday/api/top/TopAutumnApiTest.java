package io.github.exday.api.top;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** S01（GET /top）の「今」が秋のときのテスト（#151 で止めた秋の S01 のテストを、新しい API で戻したもの）。 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-10-15T10:00:00+09:00")
class TopAutumnApiTest extends TopTestSupport {

    @Test
    void 今の季節に合うValueを持つものだけをいまこの時期に出し残りは近くの発見に出す() {
        var body = okJson("/top");
        assertThat(body.get("season")).isEqualTo("autumn");
        var seasonal = section(body, "seasonal");
        assertThat(discoveryIds(seasonal)).containsExactly(sid("disc:roses"));
        assertThat(items(seasonal).get(0)).containsEntry("scope", "nearby");
        // 桜（春だけ）の大岡川は、近くの発見に出る。運営の登録のものなので見本の表示を付ける
        var nearby = section(body, "nearby_discoveries");
        assertThat(discoveryIds(nearby)).containsExactly(sid("disc:walk"), sid("disc:ooka-sakura"));
        assertThat(items(nearby).get(1)).containsEntry("isExample", true);
        assertThat(discoveryIds(section(body, "examples"))).containsExactly(sid("disc:kozukue"));
    }
}
