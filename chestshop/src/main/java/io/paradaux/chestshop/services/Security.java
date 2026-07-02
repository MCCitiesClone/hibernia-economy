package io.paradaux.chestshop.services;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 * Shop-block protection facade over {@link ProtectionService}. Injected like any
 * other collaborator (PAR-282) — the former static {@code ChestShop.protection()}
 * locator hops are gone.
 *
 * @author Acrobot
 */
public interface Security {

    boolean canAccess(Player player, Block block);

    boolean canAccess(Player player, Block block, boolean ignoreDefaultProtection);

    boolean canView(Player player, Block block, boolean ignoreDefaultProtection);

    boolean canPlaceSign(Player player, Sign sign);
}
