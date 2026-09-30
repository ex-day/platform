package io.github.exday.api;

import io.github.exday.api.discovery.DiscoveryNotFoundException;
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
 * <p>MVC の入力エラーは 400。未知のセクションと、ない・非公開の Discovery は仕様に合わせて 404。
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
            "Discovery がない、または公開状態が HIDDEN です。");
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
