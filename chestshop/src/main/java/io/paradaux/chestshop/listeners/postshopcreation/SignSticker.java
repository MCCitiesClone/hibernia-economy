package io.paradaux.chestshop.listeners.postshopcreation;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ShopCreatedEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;

/**
 * Sticks a freshly-created shop sign onto its chest. Invoked directly by
 * {@link io.paradaux.chestshop.services.ShopService#onCreated} (was a @NORMAL
 * ShopCreatedEvent listener).
 *
 * @author Acrobot
 */
public class SignSticker {

    public static void onShopCreation(ShopCreatedEvent event) {
        if (!Properties.STICK_SIGNS_TO_CHESTS) {
            return;
        }

        if (ChestShopSign.isAdminShop(event.getSignLines())) {
            return;
        }

        stickSign(event.getSign().getBlock(), event.getSignLines());
    }

    private static void stickSign(Block signBlock, String[] lines) {
        if (!(signBlock.getBlockData() instanceof Sign)) {
            return;
        }

        BlockFace shopBlockFace = null;

        for (BlockFace face : uBlock.CHEST_EXTENSION_FACES) {
            if (uBlock.couldBeShopContainer(signBlock.getRelative(face))) {
                shopBlockFace = face;
                break;
            }
        }

        if (shopBlockFace == null) {
            return;
        }

        int index = signBlock.getType().name().indexOf("SIGN");
        if (index < 0) {
            return;
        }
        Material newMaterial = Material.valueOf(signBlock.getType().name().substring(0, index) + "WALL_SIGN");

        signBlock.setType(newMaterial);

        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) signBlock.getState();

        WallSign signMaterial = (WallSign) Bukkit.createBlockData(newMaterial);
        signMaterial.setFacing(shopBlockFace.getOppositeFace());
        sign.setBlockData(signMaterial);

        for (int i = 0; i < lines.length; ++i) {
            sign.setLine(i, lines[i]);
        }

        sign.update(true);
    }
}
