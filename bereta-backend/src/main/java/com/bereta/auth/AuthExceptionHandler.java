package com.bereta.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> handle(AuthException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("code", exception.getCode());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (exception.getRetryAfterSeconds() != null) {
            problem.setProperty("retryAfterSeconds", exception.getRetryAfterSeconds());
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }
        return response.body(problem);
    }
}
