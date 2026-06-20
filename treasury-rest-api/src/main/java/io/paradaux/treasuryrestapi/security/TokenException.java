package io.paradaux.treasuryrestapi.security;

import org.springframework.http.HttpStatus;

/**
 * Thrown by {@link JwtTokenVerifier} when any step of the verification pipeline fails.
 * The filter catches this and writes the appropriate JSON error response.
 */
public class TokenException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public TokenException(HttpStatus status, String errorCode, String message) {
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
