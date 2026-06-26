package io.paradaux.chestshop.configuration;

import org.bukkit.ChatColor;

/**
 * Small colour helper retained from the old breeze configurator — its reflective
 * config loading has been replaced by HiberniaFramework's Configurator
 * ({@link ChestShopConfiguration}). Still used by {@link Messages} until the i18n
 * layer is migrated too.
 *
 * @author Acrobot
 */
public final class Configuration {

    private Configuration() {
    }

    /**
     * Colourises a string (using the '&amp;' character).
     *
     * @param string String to colourise
     * @return Colourised string
     */
    public static String getColoured(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }
}
