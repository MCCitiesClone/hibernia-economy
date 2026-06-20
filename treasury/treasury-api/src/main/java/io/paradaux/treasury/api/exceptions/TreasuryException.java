package io.paradaux.treasury.api.exceptions;

/**
 * Base type for typed Treasury runtime exceptions. Distinguished from the
 * grab-bag of {@link IllegalStateException} / {@link IllegalArgumentException}
 * that the implementation otherwise relies on, so handlers can pattern-match
 * specific failure modes without scraping {@code getMessage()}.
 */
public abstract class TreasuryException extends RuntimeException {
    protected TreasuryException(String message) { super(message); }
    protected TreasuryException(String message, Throwable cause) { super(message, cause); }
}
