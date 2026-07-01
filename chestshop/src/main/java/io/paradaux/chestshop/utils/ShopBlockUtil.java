package io.paradaux.chestshop.utils;

import io.paradaux.chestshop.configuration.ChestShopConfiguration;
import io.paradaux.chestshop.signs.ChestShopSign;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
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
@Singleton
public class ShopBlockUtil {
    public static final BlockFace[] CHEST_EXTENSION_FACES = {BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};
    public static final BlockFace[] SHOP_FACES = {BlockFace.SELF, BlockFace.DOWN, BlockFace.UP, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};

    private final ChestShopConfiguration config;
    // Lazy to break the ShopBlockUtil ↔ ChestShopSign construction cycle (this util
    // resolves shop signs; ChestShopSign asks back whether a block is a shop container).
    private final Provider<ChestShopSign> chestShopSign;

    @Inject
    public ShopBlockUtil(ChestShopConfiguration config, Provider<ChestShopSign> chestShopSign) {
        this.config = config;
        this.chestShopSign = chestShopSign;
    }

    public Sign getConnectedSign(Block block) {
        Sign sign = findAnyNearbyShopSign(block);

        if (sign == null) {
            Block neighbor = findNeighbor(block);
            if (neighbor != null) {
                sign = findAnyNearbyShopSign(neighbor);
            }
        }

        return sign;
    }

    public Container findConnectedContainer(Sign sign) {
        if (!BlockUtil.isLoaded(sign.getBlock())) {
            return null;
        }
        // (config/sign-dependent — see couldBeShopContainer / ChestShopSign below)

        BlockFace signFace = null;
        BlockData data = sign.getBlockData();
        if (data instanceof WallSign) {
            signFace = ((WallSign) data).getFacing().getOppositeFace();
        }
        return findConnectedContainer(sign.getLocation(), signFace);
    }

    public Container findConnectedContainer(Block block) {
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

    private Container findConnectedContainer(Location location, BlockFace signFace) {
        if (signFace != null) {
            Block faceBlock = location.clone().add(signFace.getModX(), signFace.getModY(), signFace.getModZ()).getBlock();
            if (couldBeShopContainer(faceBlock)) {
                return (Container) faceBlock.getState();
            }
        }

        for (BlockFace bf : SHOP_FACES) {
            if (bf != signFace) {
                Block faceBlock = location.clone().add(bf.getModX(), bf.getModY(), bf.getModZ()).getBlock();
                if (couldBeShopContainer(faceBlock)) {
                    return (Container) faceBlock.getState();
                }
            }
        }
        return null;
    }

    public List<Sign> findConnectedShopSigns(InventoryHolder chestShopInventoryHolder) {
        List<Sign> result = new ArrayList<>();

        if (chestShopInventoryHolder instanceof DoubleChest) {
            InventoryHolder leftChestSide = getLeftSide((DoubleChest) chestShopInventoryHolder, false);
            InventoryHolder rightChestSide = getRightSide((DoubleChest) chestShopInventoryHolder, false);

            if (!(leftChestSide instanceof BlockState) || !(rightChestSide instanceof BlockState)) {
                return result;
            }

            Block leftChest = ((BlockState) leftChestSide).getBlock();
            Block rightChest = ((BlockState) rightChestSide).getBlock();

            if (chestShopSign.get().isShopBlock(leftChest)) {
                result.addAll(findConnectedShopSigns(leftChest));
            }

            if (chestShopSign.get().isShopBlock(rightChest)) {
                result.addAll(findConnectedShopSigns(rightChest));
            }
        }

        else if (chestShopInventoryHolder instanceof BlockState) {
            Block chestBlock = ((BlockState) chestShopInventoryHolder).getBlock();

            if (chestShopSign.get().isShopBlock(chestBlock)) {
                result.addAll(findConnectedShopSigns(chestBlock));
            }
        }

        return result;
    }

    public List<Sign> findConnectedShopSigns(Block chestBlock) {
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

            if (chestShopSign.get().isValid(sign)) {
                result.add(sign);
            }
        }

        return result;
    }

    public Sign findAnyNearbyShopSign(Block block) {
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

            if (chestShopSign.get().isValid(sign)) {
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

    public boolean couldBeShopContainer(Block block) {
        return block != null && BlockUtil.isLoaded(block) && config.getShopContainers().contains(block.getType());
    }

    public boolean couldBeShopContainer(InventoryHolder holder) {
        return (holder instanceof Container && couldBeShopContainer(((Container) holder).getBlock()))
                    || (holder instanceof DoubleChest && couldBeShopContainer(getLeftSide((DoubleChest) holder, false)));
    }
}
