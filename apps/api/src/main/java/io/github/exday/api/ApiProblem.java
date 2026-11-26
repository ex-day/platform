package io.github.exday.api;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * API が返すエラー（RFC 9457 problem+json）の種類の一覧。種類ごとに HTTP の状態と、変えない識別子（{@code type}）を持つ。
 *
 * <p>画面は {@code type} で種類を見分け、利用者に見せる文言は画面の側で持つ（S12「計算できないとき」）。
 * API の {@code title}・{@code detail}（開発・調べる人向けの説明）は {@code src/main/resources/problems.properties} に
 * まとめて持ち、文言を変えるときはそこだけを直す（2026-11-26 の人の判断：文言は一元管理する）。
 * 識別子は画面との約束なので、名前を変えない（変えるときは openapi.yaml の {@code Problem} と S12 も直す）。
 */
public enum ApiProblem {
    INVALID_PARAMETERS("invalid-parameters", HttpStatus.BAD_REQUEST),
    DISCOVERY_NOT_FOUND("discovery-not-found", HttpStatus.NOT_FOUND),
    PLACE_NOT_FOUND("place-not-found", HttpStatus.NOT_FOUND),
    CONVERSATION_NOT_FOUND("conversation-not-found", HttpStatus.NOT_FOUND),
    PROJECT_NOT_FOUND("project-not-found", HttpStatus.NOT_FOUND),
    PROJECT_LIST_INVALID_CURSOR("project-list-invalid-cursor", HttpStatus.BAD_REQUEST),
    TOP_ORIGIN_NOT_FOUND("top-origin-not-found", HttpStatus.NOT_FOUND),
    STATION_INVALID_QUERY("station-invalid-query", HttpStatus.BAD_REQUEST),
    DETOUR_INVALID_MINUTES("detour-invalid-minutes", HttpStatus.BAD_REQUEST),
    DETOUR_INVALID_CURSOR("detour-invalid-cursor", HttpStatus.BAD_REQUEST),
    DETOUR_FROM_NOT_STATION("detour-from-not-station", HttpStatus.BAD_REQUEST),
    DETOUR_TO_NOT_STATION("detour-to-not-station", HttpStatus.BAD_REQUEST),
    DETOUR_FROM_OUT_OF_COVERAGE("detour-from-out-of-coverage", HttpStatus.BAD_REQUEST),
    DETOUR_TO_OUT_OF_COVERAGE("detour-to-out-of-coverage", HttpStatus.BAD_REQUEST),
    DETOUR_NO_RAIL_ROUTE("detour-no-rail-route", HttpStatus.BAD_REQUEST),
    DETOUR_DATA_UNAVAILABLE("detour-data-unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private static final String TYPE_PREFIX = "urn:ex-day:problem:";

    private final String code;
    private final HttpStatus status;

    ApiProblem(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    /** {@code problems.properties} のキーにも使う識別子（例：{@code detour-from-not-station}）。 */
    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    /** problem+json の {@code type}（例：{@code urn:ex-day:problem:detour-from-not-station}）。 */
    public URI type() {
        return URI.create(TYPE_PREFIX + code);
    }
}
