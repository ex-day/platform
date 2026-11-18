package io.github.exday.api;

import io.github.exday.api.conversation.ConversationNotFoundException;
import io.github.exday.api.discovery.DiscoveryNotFoundException;
import io.github.exday.api.place.PlaceNotFoundException;
import io.github.exday.api.generated.model.SectionKey;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * すべてのエンドポイントのエラーを RFC 9457（problem+json）で返す。
 *
 * <p>MVC の入力エラーは 400。未知のセクションと、ない・見られない Discovery・会話、ない場所は仕様に合わせて 404。
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

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch
                && mismatch.getRequiredType() == SectionKey.class) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested discovery section does not exist.");
            return handleExceptionInternal(ex, problem, headers, HttpStatus.NOT_FOUND, request);
        }
        return super.handleTypeMismatch(ex, headers, status, request);
    }
}
