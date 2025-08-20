package dev.zenith.trader.module.statemachine;

import com.google.common.collect.Lists;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.PlaceRecipe;
import com.zenith.feature.inventory.actions.ShiftClick;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.inventory.util.InventoryUtil;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.util.RequestFuture;
import dev.zenith.trader.ItemUtil;
import dev.zenith.trader.module.IStatemachine;
import dev.zenith.trader.module.VillagerTrader;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;

import java.util.List;

import static com.zenith.Globals.*;
import static dev.zenith.trader.ItemUtil.*;
import static dev.zenith.trader.VillagerTraderPlugin.PLUGIN_CONFIG;
import static dev.zenith.trader.module.VillagerTrader.PRIORITY;

/*
 * @author IceTank
 * @since 20.08.2025
 */
public class RestockStatemachine implements IStatemachine {
    RestockStates state = RestockStates.START;
    private PathingRequestFuture restockPathingFuture = PathingRequestFuture.rejected;
    private RequestFuture restockWithdrawFuture = RequestFuture.rejected;
    private RequestFuture emeraldBlockCraftFuture = RequestFuture.rejected;
    VillagerTrader owner;

    public RestockStatemachine(VillagerTrader owner) {
        this.owner = owner;
    }

    public void restock() {
        reset();
    }

    @Override
    public void reset() {
        this.state = RestockStates.START;
        this.restockPathingFuture = PathingRequestFuture.rejected;
        this.restockWithdrawFuture = RequestFuture.rejected;
        this.emeraldBlockCraftFuture = RequestFuture.rejected;
    }
    @Override
    public void onTick() {
        switch (state) {
            case START -> {
                if (needsBooks()) {
                    switchState(RestockStates.GO_TO_CHEST_BOOKS);
                } else if (needsCashMoney()) {
                    switchState(RestockStates.GO_TO_CHEST_EMERALDS);
                } else {
                    owner.info("Restocking completed, we have enough to trade");
                    switchState(RestockStates.SUCCESS);
                }
            }
            case GO_TO_CHEST_EMERALDS -> {
                int emeraldCount = ItemUtil.countItem(ItemRegistry.EMERALD.id());
                int emeraldBlockCount = ItemUtil.countItem(ItemRegistry.EMERALD_BLOCK.id());

                if (emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
                    var restockChest = PLUGIN_CONFIG.restockChest;
                    restockPathingFuture = BARITONE.rightClickBlock(restockChest.x(), restockChest.y(), restockChest.z());
                    restockPathingFuture.addExecutedListener(f -> owner.waitForInteractTimer.reset());
                    switchState(RestockStates.PATHING_TO_CHEST_EMERALD);
                } else if (emeraldBlockCount > 0) {
                    switchState(RestockStates.CRAFT_EMERALD_BLOCKS);
                } else {
                    switchState(RestockStates.START);
                }
            }
            case GO_TO_CHEST_BOOKS -> {
                if (needsBooks()) {
                    var restockChest = PLUGIN_CONFIG.restockChestBooks;
                    restockPathingFuture = BARITONE.rightClickBlock(restockChest.x(), restockChest.y(), restockChest.z());
                    restockPathingFuture.addExecutedListener(f -> owner.waitForInteractTimer.reset());
                    switchState(RestockStates.PATHING_TO_CHEST_BOOKS);
                } else {
                    switchState(RestockStates.START);
                }
            }

            case PATHING_TO_CHEST_EMERALD -> {
                if (restockPathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        var actions = Lists.newArrayList(
                                InventoryActionMacros.withdraw(
                                        openContainer.getContainerId(),
                                        i -> i.getId() == ItemRegistry.EMERALD.id() || i.getId() == ItemRegistry.EMERALD_BLOCK.id(),
                                        PLUGIN_CONFIG.restockStacks));
                        actions.add(new CloseContainer(openContainer.getContainerId()));
                        restockWithdrawFuture = INVENTORY.submit(InventoryActionRequest.builder()
                                .owner(this)
                                .actions(actions)
                                .priority(PRIORITY)
                                .build());
                        switchState(RestockStates.WITHDRAWING_FROM_CHEST_EMERALD);
                    } else {
                        if (owner.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            switchState(RestockStates.GO_TO_CHEST_EMERALDS);
                        }
                    }
                }
            }
            case PATHING_TO_CHEST_BOOKS -> {
                if (restockPathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        var actions = Lists.newArrayList(
                                InventoryActionMacros.withdraw(
                                        openContainer.getContainerId(),
                                        i -> i.getId() == ItemRegistry.BOOK.id(),
                                        PLUGIN_CONFIG.restockStacks));
                        actions.add(new CloseContainer(openContainer.getContainerId()));
                        restockWithdrawFuture = INVENTORY.submit(InventoryActionRequest.builder()
                                .owner(this)
                                .actions(actions)
                                .priority(PRIORITY)
                                .build());
                        switchState(RestockStates.WITHDRAWING_FROM_CHEST_BOOKS);
                    } else {
                        if (owner.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            switchState(RestockStates.GO_TO_CHEST_BOOKS);
                        }
                    }
                }
            }
            case WITHDRAWING_FROM_CHEST_EMERALD -> {
                if (restockWithdrawFuture.isCompleted()) {
                    int emeraldCount = countItem(ItemRegistry.EMERALD.id());
                    int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                    if (emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
                        owner.warn("We have fewer than {} emeralds after restocking, trying to continue trading anyway", PLUGIN_CONFIG.restockEmeraldCountThreshold);
                    }
                    if (emeraldBlockCount > 0) {
                        switchState(RestockStates.CRAFT_EMERALD_BLOCKS);
                    } else {
                        switchState(RestockStates.START);
                    }
                }
            }
            case WITHDRAWING_FROM_CHEST_BOOKS -> {
                if (restockWithdrawFuture.isCompleted()) {
                    int bookCount = countItem(ItemRegistry.BOOK.id());
                    if (bookCount < PLUGIN_CONFIG.restockEmeraldCountThreshold) { // Uses emerald count threshold because im lazy
                        owner.warn("We have fewer than {} books after restocking, trying to continue trading anyway", PLUGIN_CONFIG.restockEmeraldCountThreshold);
                    }
                    switchState(RestockStates.START);
                }
            }
            case CRAFT_EMERALD_BLOCKS -> {
                int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockCount == 0) {
                    switchState(RestockStates.START);
                    return;
                }
                int emptySlots = countInvEmptySlots();
                if (emptySlots < 4) {
                    switchState(RestockStates.START);
                    return;
                }
                int emeraldBlockSlot = InventoryUtil.searchPlayerInventory(i -> i.getId() == ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockSlot == -1) {
                    switchState(RestockStates.START);
                    return;
                }
                List<InventoryAction> actions = Lists.newArrayList();
                actions.add(new PlaceRecipe(0, "minecraft:emerald", true));
                actions.add(new ShiftClick(0, ShiftClickItemAction.LEFT_CLICK));
                actions.add(new CloseContainer(0));
                emeraldBlockCraftFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                switchState(RestockStates.AWAIT_CRAFT_EMERALD_BLOCKS);
            }
            case AWAIT_CRAFT_EMERALD_BLOCKS -> {
                if (emeraldBlockCraftFuture.isCompleted()) {
                    int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                    if (emeraldBlockCount > 0) {
                        switchState(RestockStates.CRAFT_EMERALD_BLOCKS);
                    } else {
                        switchState(RestockStates.START);
                    }
                }
            }
        }
    }

    @Override
    public boolean isSuccessful() {
        return this.state == RestockStates.SUCCESS;
    }

    @Override
    public boolean isError() {
        return this.state == RestockStates.ERROR;
    }

    @Override
    public boolean isRunning() {
        return this.state != RestockStates.SUCCESS && this.state != RestockStates.ERROR;
    }

    private void switchState(RestockStates newState) {
        this.state = newState;
        // Additional logic for state transition can be added here
    }

    public boolean needsRestock() {
        return needsCashMoney() || needsBooks();
    }

    private boolean needsCashMoney() {
        int emeraldCount = ItemUtil.countItem(ItemRegistry.EMERALD.id());
        int emeraldBlockCount = ItemUtil.countItem(ItemRegistry.EMERALD_BLOCK.id());
        return emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockEmeraldCountThreshold;
    }

    private boolean needsBooks() {
        if (!desiresBookTrades()) {
            return false;
        }
        int bookCount = ItemUtil.countItem(ItemRegistry.BOOK.id());
        return bookCount < PLUGIN_CONFIG.restockEmeraldCountThreshold;
    }

    private boolean desiresBookTrades() {
        return getBuyItemIds(owner).contains(ItemRegistry.ENCHANTED_BOOK.id());
    }

    enum RestockStates {
        START,
        GO_TO_CHEST_EMERALDS,
        PATHING_TO_CHEST_EMERALD,
        WITHDRAWING_FROM_CHEST_EMERALD,
        GO_TO_CHEST_BOOKS,
        PATHING_TO_CHEST_BOOKS,
        WITHDRAWING_FROM_CHEST_BOOKS,
        CRAFT_EMERALD_BLOCKS,
        AWAIT_CRAFT_EMERALD_BLOCKS,
        SUCCESS,
        ERROR
    }
}
