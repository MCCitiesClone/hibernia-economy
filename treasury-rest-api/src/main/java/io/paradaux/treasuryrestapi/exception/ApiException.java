package io.paradaux.treasuryrestapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Business logic exception that maps directly to an HTTP error response.
 * Caught by {@link GlobalExceptionHandler} which writes the JSON error envelope.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
