package io.paradaux.business.services;

import java.util.UUID;

/**
 * Sends in-game notifications to the online members of a firm (proprietor +
 * current employees). A single primitive that the various "tell the firm
 * something happened" features build on — incoming transfers, employee
 * join/leave, promotions, etc. (PAR-94). Delivery is best-effort: a failure to
 * notify never propagates to the operation that triggered it.
 */
public interface FirmNotificationService {

    /** Notify every online member of the firm. */
    void notifyFirm(int firmId, String messageKey, Object... placeholders);

    /**
     * Notify every online member of the firm except {@code excluded} — used so the
     * player who triggered an event isn't notified of their own action.
     */
    void notifyFirmExcept(int firmId, UUID excluded, String messageKey, Object... placeholders);
}
