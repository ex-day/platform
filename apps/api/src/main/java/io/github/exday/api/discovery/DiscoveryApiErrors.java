package io.github.exday.api.discovery;

import io.github.exday.api.generated.model.SectionKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** MVC の入力エラーを RFC 9457 で返す。未知のセクションだけは仕様に合わせて 404。 */
@RestControllerAdvice(assignableTypes = DiscoverySectionsController.class)
public class DiscoveryApiErrors extends ResponseEntityExceptionHandler {
    // 生成 interface の @Validated による引数検証を RFC 9457 に変換する。
    @org.springframework.web.bind.annotation.ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail invalidArgument(jakarta.validation.ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request parameters are invalid.");
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
