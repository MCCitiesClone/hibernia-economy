package io.paradaux.business.utils;

import java.util.regex.Pattern;

/**
 * Centralised validation for player-supplied names that end up rendered through
 * MiniMessage (firm names, account names). The whitelist excludes characters
 * that MiniMessage parses as tags ({@code <}, {@code >}), the legacy colour-code
 * char ({@code &}), or anything else that could be abused for
 * impersonation/spoofing in chat.
 */
public final class NameValidator {

    /** Letters, digits, space, underscore, hyphen, period. ({@code &} excluded — legacy colour code.) */
    private static final Pattern FIRM_NAME = Pattern.compile("[A-Za-z0-9 _.\\-]{2,32}");

    /** Same character set, slightly tighter range; account names are shown next to a firm name. */
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9 _.\\-]{2,40}");

    private NameValidator() {}

    public static boolean isValidFirmName(String s) {
        return s != null && FIRM_NAME.matcher(s).matches();
    }

    public static boolean isValidAccountName(String s) {
        return s != null && ACCOUNT_NAME.matcher(s).matches();
    }
}
