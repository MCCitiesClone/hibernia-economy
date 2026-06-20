package io.paradaux.treasury.api.exceptions;

/**
 * Thrown when a government account lookup fails — either by display name or
 * by id (depending on the call site).
 */
public class GovAccountNotFoundException extends TreasuryException {
    private final String identifier;

    public GovAccountNotFoundException(String identifier) {
        super("Government account '" + identifier + "' not found");
        this.identifier = identifier;
    }

    public String getIdentifier() { return identifier; }
}
