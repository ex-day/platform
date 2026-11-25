package io.github.exday.api;

import io.github.exday.api.conversation.ConversationNotFoundException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * すべてのエンドポイントのエラーを RFC 9457（problem+json）で返す。
 *
 * <p>MVC の入力エラーは 400。ない・見られない Discovery・会話・企画、ない場所・TOP の起点は仕様に合わせて 404。
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    // 生成 interface の @Validated による引数検証（@Size・@Min 等）の違反を 400 にする。
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidArgument(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request parameters are invalid.");
    }

    @ExceptionHandler(DiscoveryNotFoundException.class)
    public ProblemDetail discoveryNotFound(DiscoveryNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "Discovery がない、または見られない（非公開・成立の取り消し）。");
    }

    @ExceptionHandler(PlaceNotFoundException.class)
    public ProblemDetail placeNotFound(PlaceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "場所がない。");
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ProblemDetail conversationNotFound(ConversationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "会話がない、または見られない（非公開）。またはカーソルがこの会話のものでない。");
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail projectNotFound(ProjectNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "企画がない、または見られない（非公開）。またはカーソルがこの企画のものでない。");
    }

    @ExceptionHandler(InvalidProjectListCursorException.class)
    public ProblemDetail invalidProjectListCursor(InvalidProjectListCursorException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "カーソルが、この絞り込み・並べ方で作ったものではない。条件を変えたら最初から読む。");
    }

    @ExceptionHandler(InvalidDetourCursorException.class)
    public ProblemDetail invalidDetourCursor(InvalidDetourCursorException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "カーソルが読めない、条件が違う、または到達圏のデータ・計算の決まりが切り替わった。最初から読み直す。");
    }

    @ExceptionHandler(InvalidDetourMinutesException.class)
    public ProblemDetail invalidDetourMinutes(InvalidDetourMinutesException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "使える時間は15分刻み。");
    }

    @ExceptionHandler(InvalidDetourStationException.class)
    public ProblemDetail invalidDetourStation(InvalidDetourStationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidStationQueryException.class)
    public ProblemDetail invalidStationQuery(InvalidStationQueryException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "探す文字が空。");
    }

    @ExceptionHandler(TopOriginNotFoundException.class)
    public ProblemDetail topOriginNotFound(TopOriginNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "起点の場所がない。");
    }
}
