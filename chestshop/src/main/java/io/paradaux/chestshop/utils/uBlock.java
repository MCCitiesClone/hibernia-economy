package io.paradaux.chestshop.utils;

import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getLeftSide;
import static io.paradaux.chestshop.utils.ImplementationAdapter.getRightSide;
import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;

/**
 * @author Acrobot
 */
public class uBlock {
    public static final BlockFace[] CHEST_EXTENSION_FACES = {BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};
    public static final BlockFace[] SHOP_FACES = {BlockFace.SELF, BlockFace.DOWN, BlockFace.UP, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};

    public static Sign getConnectedSign(Block block) {
        Sign sign = uBlock.findAnyNearbyShopSign(block);

        if (sign == null) {
            Block neighbor = findNeighbor(block);
            if (neighbor != null) {
                sign = uBlock.findAnyNearbyShopSign(neighbor);
            }
        }

        return sign;
    }

    public static Container findConnectedContainer(Sign sign) {
        if (!BlockUtil.isLoaded(sign.getBlock())) {
            return null;
        }

        BlockFace signFace = null;
        BlockData data = sign.getBlockData();
        if (data instanceof WallSign) {
            signFace = ((WallSign) data).getFacing().getOppositeFace();
        }
        return findConnectedContainer(sign.getLocation(), signFace);
    }

    public static Container findConnectedContainer(Block block) {
        if (!BlockUtil.isLoaded(block)) {
            return null;
        }

        BlockFace signFace = null;
        BlockData data = block.getBlockData();
        if (data instanceof WallSign) {
            signFace = ((WallSign) data).getFacing().getOppositeFace();
        }
        return findConnectedContainer(block.getLocation(), signFace);
    }

    private static Container findConnectedContainer(Location location, BlockFace signFace) {
        if (signFace != null) {
            Block faceBlock = location.clone().add(signFace.getModX(), signFace.getModY(), signFace.getModZ()).getBlock();
            if (uBlock.couldBeShopContainer(faceBlock)) {
                return (Container) faceBlock.getState();
            }
        }

        for (BlockFace bf : SHOP_FACES) {
            if (bf != signFace) {
                Block faceBlock = location.clone().add(bf.getModX(), bf.getModY(), bf.getModZ()).getBlock();
                if (uBlock.couldBeShopContainer(faceBlock)) {
                    return (Container) faceBlock.getState();
                }
            }
        }
        return null;
    }

    public static List<Sign> findConnectedShopSigns(InventoryHolder chestShopInventoryHolder) {
        List<Sign> result = new ArrayList<>();

        if (chestShopInventoryHolder instanceof DoubleChest) {
            InventoryHolder leftChestSide = getLeftSide((DoubleChest) chestShopInventoryHolder, false);
            InventoryHolder rightChestSide = getRightSide((DoubleChest) chestShopInventoryHolder, false);

            if (!(leftChestSide instanceof BlockState) || !(rightChestSide instanceof BlockState)) {
                return result;
            }

            Block leftChest = ((BlockState) leftChestSide).getBlock();
            Block rightChest = ((BlockState) rightChestSide).getBlock();

            if (ChestShopSign.isShopBlock(leftChest)) {
                result.addAll(uBlock.findConnectedShopSigns(leftChest));
            }

            if (ChestShopSign.isShopBlock(rightChest)) {
                result.addAll(uBlock.findConnectedShopSigns(rightChest));
            }
        }

        else if (chestShopInventoryHolder instanceof BlockState) {
            Block chestBlock = ((BlockState) chestShopInventoryHolder).getBlock();

            if (ChestShopSign.isShopBlock(chestBlock)) {
                result.addAll(uBlock.findConnectedShopSigns(chestBlock));
            }
        }

        return result;
    }

    public static List<Sign> findConnectedShopSigns(Block chestBlock) {
        List<Sign> result = new ArrayList<>();

        for (BlockFace bf : SHOP_FACES) {
            Block faceBlock = chestBlock.getRelative(bf);

            if (!BlockUtil.isSign(faceBlock)) {
                continue;
            }

            Sign sign = (Sign) faceBlock.getState();

            Container signContainer = findConnectedContainer(sign);
            if (signContainer == null || !chestBlock.equals(signContainer.getBlock())) {
                continue;
            }

            if (ChestShopSign.isValid(sign)) {
                result.add(sign);
            }
        }

        return result;
    }

    public static Sign findAnyNearbyShopSign(Block block) {
        for (BlockFace bf : SHOP_FACES) {
            Block faceBlock = block.getRelative(bf);
            if (!BlockUtil.isLoaded(faceBlock)) {
                continue;
            }

            BlockData data = faceBlock.getBlockData();
            if (data instanceof WallSign) {
                if (((WallSign) data).getFacing() != bf
                        && couldBeShopContainer(faceBlock.getRelative(((WallSign) data).getFacing().getOppositeFace()))) {
                    continue;
                }
            } else if (!(data instanceof org.bukkit.block.data.type.Sign)) {
                continue;
            }

            Sign sign = (Sign) getState(faceBlock, false);

            if (ChestShopSign.isValid(sign)) {
                return sign;
            }
        }
        return null;
    }

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

    public static boolean couldBeShopContainer(Block block) {
        return block != null && BlockUtil.isLoaded(block) && Properties.SHOP_CONTAINERS.contains(block.getType());
    }

    public static boolean couldBeShopContainer(InventoryHolder holder) {
        return (holder instanceof Container && couldBeShopContainer(((Container) holder).getBlock()))
                    || (holder instanceof DoubleChest && couldBeShopContainer(getLeftSide((DoubleChest) holder, false)));
    }
}
