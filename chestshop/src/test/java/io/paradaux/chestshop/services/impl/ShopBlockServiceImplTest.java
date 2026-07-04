package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.services.SignService;
import io.paradaux.chestshop.support.ServerTest;
import io.paradaux.chestshop.support.TestConfigs;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real block↔sign geometry on MockBukkit: {@link ShopBlockServiceImpl} resolving the sign a
 * container is attached to (and vice-versa) and the shop-container predicates, over a real
 * {@link SignService} and {@code SHOP_CONTAINERS} config with placed chests and wall signs.
 */
class ShopBlockServiceImplTest extends ServerTest {

    private ShopBlockServiceImpl service;
    private World world;
    private int base = 0;

    @BeforeEach
    void wire() {
        ChestShopConfiguration config = TestConfigs.with(TestConfigs.defaults(),
                "shopContainersRaw", List.of("CHEST"));
        service = new ShopBlockServiceImpl(config, new SignService(config));
        world = server.addSimpleWorld("blockworld");
        for (int cx = -2; cx <= 40; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
    }

    /** A chest with a valid wall-shop-sign hung on the block to its EAST. Returns [chest, sign]. */
    private Block[] shopWithWallSign() {
        int x = base += 6;
        Block chest = world.getBlockAt(x, 64, 0);
        chest.setType(Material.CHEST);

        Block signBlock = world.getBlockAt(x + 1, 64, 0);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.EAST); // opposite (WEST) points back at the chest
        signBlock.setBlockData(data);

        Sign sign = (Sign) signBlock.getState();
        sign.setLine(SignService.NAME_LINE, "Notch");
        sign.setLine(SignService.QUANTITY_LINE, "64");
        sign.setLine(SignService.PRICE_LINE, "B 5");
        sign.setLine(SignService.ITEM_LINE, "Diamond");
        sign.update();

        return new Block[]{chest, signBlock};
    }

    // ---- couldBeShopContainer ---------------------------------------------------

    @Test
    void couldBeShopContainer_block() {
        Block chest = world.getBlockAt(200, 64, 0);
        chest.setType(Material.CHEST);
        assertThat(service.couldBeShopContainer(chest)).isTrue();

        Block stone = world.getBlockAt(202, 64, 0);
        stone.setType(Material.STONE);
        assertThat(service.couldBeShopContainer(stone)).isFalse();

        assertThat(service.couldBeShopContainer((Block) null)).isFalse();
    }

    @Test
    void couldBeShopContainer_holder_containerAndDoubleChest() {
        Block chest = world.getBlockAt(204, 64, 0);
        chest.setType(Material.CHEST);
        Container container = (Container) chest.getState();
        assertThat(service.couldBeShopContainer((InventoryHolder) container)).isTrue();

        DoubleChest dc = mock(DoubleChest.class);
        when(dc.getLeftSide(false)).thenReturn((InventoryHolder) container);
        assertThat(service.couldBeShopContainer(dc)).isTrue();

        assertThat(service.couldBeShopContainer(mock(InventoryHolder.class))).isFalse();
    }

    // ---- findConnectedContainer -------------------------------------------------

    @Test
    void findConnectedContainer_fromWallSign() {
        Block[] shop = shopWithWallSign();
        Sign sign = (Sign) shop[1].getState();
        Container container = service.findConnectedContainer(sign);
        assertThat(container).isNotNull();
        assertThat(container.getBlock()).isEqualTo(shop[0]);
    }

    @Test
    void findConnectedContainer_fromBlock() {
        Block[] shop = shopWithWallSign();
        Container container = service.findConnectedContainer(shop[1]);
        assertThat(container).isNotNull();
        assertThat(container.getBlock()).isEqualTo(shop[0]);
    }

    @Test
    void findConnectedContainer_fromStandingSign_searchesAllFaces() {
        // A standing sign (no WallSign facing) above a chest: found via the SHOP_FACES loop.
        int x = base += 6;
        Block chest = world.getBlockAt(x, 64, 0);
        chest.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(x, 65, 0);
        signBlock.setType(Material.OAK_SIGN); // standing sign -> signFace null
        Sign sign = (Sign) signBlock.getState();

        Container container = service.findConnectedContainer(sign);
        assertThat(container).isNotNull();
        assertThat(container.getBlock()).isEqualTo(chest);
    }

    @Test
    void findConnectedContainer_nullWhenNoContainerNearby() {
        int x = base += 6;
        Block signBlock = world.getBlockAt(x, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        Sign sign = (Sign) signBlock.getState();
        assertThat(service.findConnectedContainer(sign)).isNull();
    }

    @Test
    void findConnectedContainer_nullWhenSignBlockNotLoaded() {
        Sign sign = mock(Sign.class);
        Block block = mock(Block.class);
        World unloaded = mock(World.class);
        when(sign.getBlock()).thenReturn(block);
        when(block.getWorld()).thenReturn(unloaded);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(unloaded.isChunkLoaded(0, 0)).thenReturn(false);
        assertThat(service.findConnectedContainer(sign)).isNull();
    }

    // ---- getConnectedSign / findAnyNearbyShopSign -------------------------------

    @Test
    void getConnectedSign_findsAttachedShopSign() {
        Block[] shop = shopWithWallSign();
        Sign sign = service.getConnectedSign(shop[0]);
        assertThat(sign).isNotNull();
        assertThat(SignService.getOwner(sign)).isEqualTo("Notch");
    }

    @Test
    void getConnectedSign_nullWhenNothingAttached() {
        Block chest = world.getBlockAt(300, 64, 0);
        chest.setType(Material.CHEST);
        assertThat(service.getConnectedSign(chest)).isNull();
    }

    @Test
    void findAnyNearbyShopSign_skipsInvalidSign() {
        int x = base += 6;
        Block chest = world.getBlockAt(x, 64, 0);
        chest.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(x + 1, 64, 0);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.EAST);
        signBlock.setBlockData(data);
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(SignService.NAME_LINE, ""); // invalid: no owner
        sign.update();

        assertThat(service.findAnyNearbyShopSign(chest)).isNull();
    }

    // ---- findConnectedShopSigns -------------------------------------------------

    @Test
    void findConnectedShopSigns_fromBlock() {
        Block[] shop = shopWithWallSign();
        List<Sign> found = service.findConnectedShopSigns(shop[0]);
        assertThat(found).hasSize(1);
        assertThat(SignService.getOwner(found.get(0))).isEqualTo("Notch");
    }

    @Test
    void findConnectedShopSigns_fromBlockStateHolder() {
        Block[] shop = shopWithWallSign();
        BlockState chestState = shop[0].getState();
        List<Sign> found = service.findConnectedShopSigns((InventoryHolder) chestState);
        assertThat(found).hasSize(1);
    }

    @Test
    void findConnectedShopSigns_fromDoubleChest() {
        Block[] shop = shopWithWallSign();
        BlockState chestState = shop[0].getState();
        DoubleChest dc = mock(DoubleChest.class);
        when(dc.getLeftSide(false)).thenReturn((InventoryHolder) chestState);
        when(dc.getRightSide(false)).thenReturn((InventoryHolder) chestState);
        List<Sign> found = service.findConnectedShopSigns(dc);
        assertThat(found).isNotEmpty();
    }

    @Test
    void findConnectedShopSigns_doubleChest_emptyWhenSidesNotBlockStates() {
        DoubleChest dc = mock(DoubleChest.class);
        when(dc.getLeftSide(false)).thenReturn(mock(InventoryHolder.class));
        when(dc.getRightSide(false)).thenReturn(mock(InventoryHolder.class));
        assertThat(service.findConnectedShopSigns(dc)).isEmpty();
    }

    @Test
    void findConnectedShopSigns_otherHolder_empty() {
        assertThat(service.findConnectedShopSigns(mock(InventoryHolder.class))).isEmpty();
    }

    // ---- isShopBlock ------------------------------------------------------------

    @Test
    void isShopBlock_block_trueWhenSignAttached_falseOtherwise() {
        Block[] shop = shopWithWallSign();
        assertThat(service.isShopBlock(shop[0])).isTrue();

        Block loneChest = world.getBlockAt(400, 64, 0);
        loneChest.setType(Material.CHEST);
        assertThat(service.isShopBlock(loneChest)).isFalse();

        Block stone = world.getBlockAt(402, 64, 0);
        stone.setType(Material.STONE);
        assertThat(service.isShopBlock(stone)).isFalse();
    }

    @Test
    void findConnectedContainer_fromNonSignBlock_searchesFaces() {
        // A non-WallSign block (the chest itself) -> signFace null -> SHOP_FACES loop (SELF hits it).
        Block chest = world.getBlockAt(base += 6, 64, 0);
        chest.setType(Material.CHEST);
        Container container = service.findConnectedContainer(chest);
        assertThat(container).isNotNull();
        assertThat(container.getBlock()).isEqualTo(chest);
    }

    @Test
    void findConnectedContainer_block_nullWhenNotLoaded() {
        Block block = mock(Block.class);
        World unloaded = mock(World.class);
        when(block.getWorld()).thenReturn(unloaded);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(unloaded.isChunkLoaded(0, 0)).thenReturn(false);
        assertThat(service.findConnectedContainer(block)).isNull();
    }

    @Test
    void findConnectedShopSigns_holder_emptyForNonShopChest() {
        Block loneChest = world.getBlockAt(base += 6, 64, 0);
        loneChest.setType(Material.CHEST);
        assertThat(service.findConnectedShopSigns((InventoryHolder) loneChest.getState())).isEmpty();
    }

    @Test
    void findAnyNearbyShopSign_continuesPastUnloadedFace() {
        // Chest at a chunk boundary: its EAST neighbour is in an unloaded chunk (isLoaded==false,
        // continue), while a valid wall sign sits on a loaded face.
        int x = 40 * 16 + 15; // chunk 40 (loaded); EAST -> chunk 41 (unloaded)
        Block chest = world.getBlockAt(x, 64, 0);
        chest.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(x - 1, 64, 0); // WEST, loaded
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.WEST); // opposite (EAST) points at the chest
        signBlock.setBlockData(data);
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(SignService.NAME_LINE, "Notch");
        sign.setLine(SignService.QUANTITY_LINE, "64");
        sign.setLine(SignService.PRICE_LINE, "B 5");
        sign.setLine(SignService.ITEM_LINE, "Diamond");
        sign.update();

        assertThat(service.findAnyNearbyShopSign(chest)).isNotNull();
    }

    @Test
    void findConnectedShopSigns_skipsSignWhoseContainerIsAnotherBlock() {
        // A valid wall sign that resolves to a DIFFERENT chest than the one we search from.
        int x = base += 6;
        Block chestA = world.getBlockAt(x, 64, 0);
        chestA.setType(Material.CHEST);
        Block chestB = world.getBlockAt(x + 2, 64, 0);
        chestB.setType(Material.CHEST);
        // Sign between them, attached to chestB (facing EAST -> opposite WEST is x+1... arrange so it
        // resolves to chestB, not chestA). Place sign at x+3 facing EAST so opposite (WEST=x+2)=chestB.
        Block signBlock = world.getBlockAt(x + 3, 64, 0);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.EAST);
        signBlock.setBlockData(data);
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(SignService.NAME_LINE, "Notch");
        sign.setLine(SignService.QUANTITY_LINE, "64");
        sign.setLine(SignService.PRICE_LINE, "B 5");
        sign.setLine(SignService.ITEM_LINE, "Diamond");
        sign.update();

        // Searching from chestA: the only nearby sign resolves to chestB, so it's skipped.
        assertThat(service.findConnectedShopSigns(chestA)).isEmpty();
    }

    @Test
    void findConnectedShopSigns_skipsInvalidSignOnChest() {
        int x = base += 6;
        Block chest = world.getBlockAt(x, 64, 0);
        chest.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(x + 1, 64, 0);
        signBlock.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) signBlock.getBlockData();
        data.setFacing(BlockFace.EAST);
        signBlock.setBlockData(data);
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(SignService.NAME_LINE, "Notch");
        sign.setLine(SignService.QUANTITY_LINE, "64");
        sign.setLine(SignService.PRICE_LINE, "nope"); // invalid price -> isValid false
        sign.setLine(SignService.ITEM_LINE, "Diamond");
        sign.update();

        assertThat(service.findConnectedShopSigns(chest)).isEmpty();
    }

    @Test
    void isShopBlock_holder_blockStateAndDoubleChestAndOther() {
        Block[] shop = shopWithWallSign();
        BlockState chestState = shop[0].getState();
        assertThat(service.isShopBlock((InventoryHolder) chestState)).isTrue();

        DoubleChest dc = mock(DoubleChest.class);
        when(dc.getLeftSide(false)).thenReturn((InventoryHolder) chestState);
        when(dc.getRightSide(false)).thenReturn((InventoryHolder) chestState);
        assertThat(service.isShopBlock(dc)).isTrue();

        assertThat(service.isShopBlock(mock(InventoryHolder.class))).isFalse();
    }
}
