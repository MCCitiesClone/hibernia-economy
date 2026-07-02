package io.paradaux.chestshop.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

/**
 * Pure, stateless shop-block geometry: the block faces a shop's sign/container may sit on,
 * and the double-chest neighbour lookup. The stateful, config/sign-backed resolution
 * (getConnectedSign, findConnectedContainer, isShopBlock, …) split out to
 * {@link io.paradaux.chestshop.services.ShopBlockService} (PAR-282).
 *
 * @author Acrobot
 */
public final class ShopBlockUtil {

    private ShopBlockUtil() {
    }

    public static final BlockFace[] CHEST_EXTENSION_FACES = {BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};
    public static final BlockFace[] SHOP_FACES = {BlockFace.SELF, BlockFace.DOWN, BlockFace.UP, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};

    /**
     * The other half of a double chest, or {@code null} if {@code block} is a single chest
     * (or the neighbour isn't loaded / isn't the same type).
     */
    public static Block findNeighbor(Block block) {
        if (!BlockUtil.isLoaded(block)) {
            return null;
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Chest)) {
            return null;
        }

        Chest chestData = (Chest) blockData;
        if (chestData.getType() == Chest.Type.SINGLE) {
            return null;
        }

        BlockFace chestFace = chestData.getFacing();
        // we have to rotate is to get the adjacent chest
        // west, right -> south
        // west, left -> north
        if (chestFace == BlockFace.WEST) {
            chestFace = BlockFace.NORTH;
        } else if (chestFace == BlockFace.NORTH) {
            chestFace = BlockFace.EAST;
        } else if (chestFace == BlockFace.EAST) {
            chestFace = BlockFace.SOUTH;
        } else if (chestFace == BlockFace.SOUTH) {
            chestFace = BlockFace.WEST;
        }
        if (chestData.getType() == Chest.Type.RIGHT) {
            chestFace = chestFace.getOppositeFace();
        }

        Block neighborBlock = block.getRelative(chestFace);
        if (!BlockUtil.isLoaded(neighborBlock)) {
            return null;
        }

        if (neighborBlock.getType() == block.getType()) {
            return neighborBlock;
        }

        return null;
    }
}
