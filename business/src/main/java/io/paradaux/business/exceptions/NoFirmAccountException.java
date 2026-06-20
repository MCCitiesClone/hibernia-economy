package io.paradaux.business.exceptions;

/**
 * Thrown when a firm has no usable treasury account to resolve — i.e. its
 * {@code default_account_id} is unset (or pointed at an archived/removed
 * account) and there is no surviving account to fall back to.
 *
 * <p>Extends {@link IllegalStateException} so existing {@code catch
 * (IllegalStateException)} handlers keep working, while callers that want to
 * distinguish "this firm has no account" from "insufficient funds" (also an
 * {@code IllegalStateException}) can catch this subtype first.
 */
public class NoFirmAccountException extends IllegalStateException {
    public NoFirmAccountException(String message) {
        super(message);
    }
}
