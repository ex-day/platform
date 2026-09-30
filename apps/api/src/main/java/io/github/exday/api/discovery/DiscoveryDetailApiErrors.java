package io.github.exday.api.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** {@link DiscoveryDetailController} のエラーを RFC 9457 で返す。 */
@RestControllerAdvice(assignableTypes = DiscoveryDetailController.class)
public class DiscoveryDetailApiErrors extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DiscoveryNotFoundException.class)
    public ProblemDetail discoveryNotFound(DiscoveryNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "Discovery がない、または公開状態が HIDDEN です。");
    }
}
