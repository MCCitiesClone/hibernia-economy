package io.paradaux.business.services;

import java.util.UUID;

/**
 * Tracks short-lived "are you sure?" confirmations for firm disbandment, so a
 * proprietor has to issue the command twice — once to see the warning (funds are
 * returned to them, the name can't be reused) and once to confirm (PAR-93/PAR-86).
 */
public interface FirmDisbandConfirmationService {

    /** Record that {@code player} has been warned about disbanding {@code firmId}. */
    void request(UUID player, int firmId);

    /**
     * Consume a pending confirmation: returns {@code true} iff {@code player} has an
     * unexpired pending confirmation for exactly {@code firmId} (which is then cleared).
     */
    boolean consume(UUID player, int firmId);
}
