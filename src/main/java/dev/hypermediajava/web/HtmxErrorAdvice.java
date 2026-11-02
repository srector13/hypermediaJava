package dev.hypermediajava.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.view.FragmentsRendering;

import java.util.Map;

/**
 * For htmx requests, errors become a toast instead of a broken half-swapped page:
 * the response is re-targeted to {@code #toasts} with {@code HX-Retarget} / {@code HX-Reswap}.
 * Full-page requests fall through to Spring Boot's normal error handling.
 */
@ControllerAdvice
public class HtmxErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(HtmxErrorAdvice.class);

    @ExceptionHandler(Exception.class)
    public Object handle(Exception ex, HttpServletRequest request) throws Exception {
        if (!Htmx.isHtmx(request)) {
            throw ex;
        }
        HttpStatusCode status = ex instanceof ErrorResponse er ? er.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) {
            log.error("htmx request {} {} failed", request.getMethod(), request.getRequestURI(), ex);
        }
        String message = switch (status.value()) {
            case 404 -> "That record no longer exists.";
            case 400 -> "The request was not valid.";
            default -> "Something went wrong (" + status.value() + ").";
        };
        return FragmentsRendering
                .fragment("fragments/ui :: toast-content", Map.of("kind", "error", "message", message))
                .status(status)
                .header(Htmx.RETARGET, "#toasts")
                .header(Htmx.RESWAP, "beforeend")
                .build();
    }

}
