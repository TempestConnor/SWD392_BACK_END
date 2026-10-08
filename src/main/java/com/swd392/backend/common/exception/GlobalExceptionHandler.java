package com.swd392.backend.common.exception;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String ERROR_CODE_PROPERTY = "errorCode";
    public static final String VIOLATIONS_PROPERTY = "violations";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final String VALIDATION_DETAIL = "Request validation failed.";
    private static final String INTERNAL_DETAIL = "An unexpected error occurred.";

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException exception) {
        AppErrorMessage error = exception.getErrorMessage();
        if (error.getStatus().is5xxServerError()) {
            log.error("[ERROR] - ACTION: handleAppException: errorCode: {}", error.name(), exception);
        } else {
            log.warn("[ERROR] - ACTION: handleAppException: errorCode: {}", error.name());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problem.setProperty(ERROR_CODE_PROPERTY, error.name());

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error(
                "[ERROR] - ACTION: handleUnexpected: type: {}",
                exception.getClass().getName(),
                exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_DETAIL);
        problem.setProperty(ERROR_CODE_PROPERTY, INTERNAL_ERROR);

        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();

        ProblemDetail problem = exception.getBody();
        problem.setDetail(VALIDATION_DETAIL);
        problem.setProperty(ERROR_CODE_PROPERTY, VALIDATION_FAILED);
        problem.setProperty(VIOLATIONS_PROPERTY, violations);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private Map<String, String> toViolation(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage();

        return Map.of("field", fieldError.getField(), "message", message);
    }
}
