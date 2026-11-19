package io.github.exday.api.openquestion;

import java.util.List;

/**
 * 問いに「答えが出ている」かの決まり。どの画面でも同じ決まりにする（S14 の問いの区画、S01 の探しています）。
 *
 * <p>決まり：問いへの場所の採用（{@code place_adoption}）か、回答の採用（{@code question_answer}）が、
 * {@link #ACTIVE_ADOPTION} の状態のものを1つでも持てば、答えが出ている。どちらもなければ、答えの出ていない問い。
 * SQL では、この状態の一覧をパラメータで渡し、2つの表の EXISTS で判定する。
 */
public final class QuestionAnswers {

    private QuestionAnswers() {}

    /** 採用が有効な状態（採用中・再検討中）。取り消したものは答えに数えない。 */
    public static final List<String> ACTIVE_ADOPTION = List.of("ADOPTED", "RECONSIDERING");
}
