package io.github.exday.api;

import io.github.exday.api.conversation.ConversationNotFoundException;
import io.github.exday.api.detour.DetourDataUnavailableException;
import io.github.exday.api.detour.InvalidDetourCursorException;
import io.github.exday.api.detour.InvalidDetourMinutesException;
import io.github.exday.api.detour.InvalidDetourStationException;
import io.github.exday.api.discovery.DiscoveryNotFoundException;
import io.github.exday.api.place.PlaceNotFoundException;
import io.github.exday.api.project.ProjectNotFoundException;
import io.github.exday.api.projectlist.InvalidProjectListCursorException;
import io.github.exday.api.station.InvalidStationQueryException;
import io.github.exday.api.top.TopOriginNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * すべてのエンドポイントのエラーを RFC 9457（problem+json）で返す。
 *
 * <p>MVC の入力エラーは 400。ない・見られない Discovery・会話・企画、ない場所・TOP の起点は仕様に合わせて 404。
 * 寄り道の到達圏のデータ（ACTIVE の版）がないときは 503。
 * 種類（{@code type}）と状態は {@link ApiProblem}、{@code title}・{@code detail} は {@code problems.properties} にまとめる。
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    private final MessageSource messages;

    public ApiErrors(MessageSource messages) {
        this.messages = messages;
    }

    /** 種類から problem+json を作る（文言は problems.properties）。 */
    private ProblemDetail problem(ApiProblem problem) {
        var detail = ProblemDetail.forStatusAndDetail(problem.status(),
            messages.getMessage("problem." + problem.code() + ".detail", null, Locale.ROOT));
        detail.setType(problem.type());
        detail.setTitle(messages.getMessage("problem." + problem.code() + ".title", null, Locale.ROOT));
        return detail;
    }

    // 生成 interface の @Validated による引数検証（@Size・@Min 等）の違反を 400 にする。
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidArgument(ConstraintViolationException ex) {
        return problem(ApiProblem.INVALID_PARAMETERS);
    }

    @ExceptionHandler(DiscoveryNotFoundException.class)
    public ProblemDetail discoveryNotFound(DiscoveryNotFoundException ex) {
        return problem(ApiProblem.DISCOVERY_NOT_FOUND);
    }

    @ExceptionHandler(PlaceNotFoundException.class)
    public ProblemDetail placeNotFound(PlaceNotFoundException ex) {
        return problem(ApiProblem.PLACE_NOT_FOUND);
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ProblemDetail conversationNotFound(ConversationNotFoundException ex) {
        return problem(ApiProblem.CONVERSATION_NOT_FOUND);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail projectNotFound(ProjectNotFoundException ex) {
        return problem(ApiProblem.PROJECT_NOT_FOUND);
    }

    @ExceptionHandler(InvalidProjectListCursorException.class)
    public ProblemDetail invalidProjectListCursor(InvalidProjectListCursorException ex) {
        return problem(ApiProblem.PROJECT_LIST_INVALID_CURSOR);
    }

    @ExceptionHandler(InvalidDetourCursorException.class)
    public ProblemDetail invalidDetourCursor(InvalidDetourCursorException ex) {
        return problem(ApiProblem.DETOUR_INVALID_CURSOR);
    }

    @ExceptionHandler(InvalidDetourMinutesException.class)
    public ProblemDetail invalidDetourMinutes(InvalidDetourMinutesException ex) {
        return problem(ApiProblem.DETOUR_INVALID_MINUTES);
    }

    @ExceptionHandler(InvalidDetourStationException.class)
    public ProblemDetail invalidDetourStation(InvalidDetourStationException ex) {
        return problem(ex.problem());
    }

    @ExceptionHandler(DetourDataUnavailableException.class)
    public ProblemDetail detourDataUnavailable(DetourDataUnavailableException ex) {
        return problem(ApiProblem.DETOUR_DATA_UNAVAILABLE);
    }

    @ExceptionHandler(InvalidStationQueryException.class)
    public ProblemDetail invalidStationQuery(InvalidStationQueryException ex) {
        return problem(ApiProblem.STATION_INVALID_QUERY);
    }

    @ExceptionHandler(TopOriginNotFoundException.class)
    public ProblemDetail topOriginNotFound(TopOriginNotFoundException ex) {
        return problem(ApiProblem.TOP_ORIGIN_NOT_FOUND);
    }
}
