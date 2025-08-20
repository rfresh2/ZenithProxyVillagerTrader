package dev.zenith.trader.module;

import com.github.rfresh2.EventConsumer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.*;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.util.RequestFuture;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import dev.zenith.trader.EnchantmentUtil;
import dev.zenith.trader.ItemUtil;
import dev.zenith.trader.module.statemachine.RestockStatemachine;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.VillagerData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.VillagerTrade;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static dev.zenith.trader.VillagerTraderPlugin.PLUGIN_CONFIG;

public class VillagerTrader extends Module {
    public static final int PRIORITY = 9000;
    private State state = State.RESTOCK;
    private final Cache<Integer, Boolean> interactedVillagersCache = CacheBuilder.newBuilder()
        .build();
    private PathingRequestFuture interactWithVillagerFuture = PathingRequestFuture.rejected;
    private ClientboundMerchantOffersPacket offersPacket = null;
    private RequestFuture purchaseFuture = RequestFuture.rejected;
    private PathingRequestFuture storePathingFuture = PathingRequestFuture.rejected;
    private RequestFuture storeDepositFuture = RequestFuture.rejected;
    private final Timer waitForRestockTimer = Timers.tickTimer();
    public final Timer waitForInteractTimer = Timers.tickTimer();
    private final RestockStatemachine restockStateMachine = new RestockStatemachine(this);

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.RESTOCK;
        interactedVillagersCache.invalidateAll();
        offersPacket = null;
    }

    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("villager-trader")
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .inbound(ClientboundMerchantOffersPacket.class, this::onMerchantOffers)
                .build())
            .build();
    }

    private ClientboundMerchantOffersPacket onMerchantOffers(ClientboundMerchantOffersPacket packet, ClientSession session) {
        this.offersPacket = packet;
        debug("Offers: {}", packet);
        return packet;
    }

    private void onTick(ClientBotTick event) {
        switch (state) {
            case START -> {
                int emeraldCount = ItemUtil.countItem(ItemRegistry.EMERALD.id());
                int emeraldBlockCount = ItemUtil.countItem(ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockEmeraldCountThreshold || emeraldBlockCount > 0) {
                    restockStateMachine.restock();
                    setState(State.RESTOCK);
                } else {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                }
            }
            case RESTOCK -> {
                restockStateMachine.onTick();
                if (restockStateMachine.isError()) {
                    warn("Restock state machine encountered an error, stopping trader");
                    stop();
                    return;
                }
                if (restockStateMachine.isRunning()) {
                    return;
                }

                // Restock Success
                setState(State.TRADING_INTERACT_WITH_VILLAGER);
            }
            case TRADING_INTERACT_WITH_VILLAGER -> {
                int buyItemCount = countBuyItemSlotUsages();
                if (buyItemCount > PLUGIN_CONFIG.buyItemStoreStacksThreshold) {
                    setState(State.STORE_GO_TO_CHEST);
                    return;
                }
                var nextVillagerOptional = nextVillager();
                if (nextVillagerOptional.isEmpty()) {
                    if (interactedVillagersCache.asMap().isEmpty()) {
                        warn("No villagers found to trade with, going back to restock chest");
                        setState(State.RESTOCK);
                    } else {
                        if (countBuyItem() > 0) {
                            setState(State.STORE_GO_TO_CHEST);
                        } else {
                            setState(State.WAITING_FOR_VILLAGER_TRADE_RESTOCK);
                            waitForRestockTimer.reset();
                            inGameAlert("Waiting for villagers to restock trades");
                            info("Waiting {}s for villagers to restock trades", PLUGIN_CONFIG.villagerTradeRestockWaitSeconds);
                        }
                    }
                    return;
                }
                var nextVillager = nextVillagerOptional.get();
                offersPacket = null;
                interactWithVillagerFuture = BARITONE.rightClickEntity(nextVillager);
                interactWithVillagerFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                interactedVillagersCache.put(nextVillager.getEntityId(), true);
                setState(State.TRADING_AWAIT_INTERACT_WITH_VILLAGER);
            }
            case TRADING_AWAIT_INTERACT_WITH_VILLAGER -> {
                if (interactWithVillagerFuture.isCompleted()) {
                    if (offersPacket == null) {
                        if (waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        }
                        return;
                    }
                    if (offersPacket.getContainerId() != CACHE.getPlayerCache().getInventoryCache().getOpenContainerId()) {
                        if (waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        }
                        return;
                    }
                    setState(State.TRADING_TRY_START_PURCHASE);
                }
            }
            case TRADING_TRY_START_PURCHASE -> {
                var buyItemIds = ItemUtil.getBuyItemIds(this);
                VillagerTrade[] trades = offersPacket.getTrades();
                List<InventoryAction> actions = Lists.newArrayList();
                for (int i = 0; i < trades.length; i++) {
                    var trade = trades[i];
                    if (trade.isTradeDisabled()) continue;
                    if (trade.getOutput() == null) continue;
                    System.out.println("Trade output: " + trade.getOutput());
                    if (!buyItemIds.contains(trade.getOutput().getId())) continue;


                    if (!isEBookTrade(trade)) {
                        if (trade.getFirstInput().getId() != ItemRegistry.EMERALD.id()) continue;
                        if (trade.getSecondInput() != null) continue;
                    }

                    if (!matchesDesiredEnchantments(trade.getOutput())) continue;

                    int inputStackSize = 64; // emeralds
                    int baseCost = trade.getFirstInput().getAmount();
                    int addnlDemandCost = Math.max(0, MathHelper.floorI((trade.getFirstInput().getAmount() * trade.getDemand() * trade.getPriceMultiplier())));
                    int cost = MathHelper.clamp(baseCost + addnlDemandCost + trade.getSpecialPrice(), 1, inputStackSize);
                    if (cost > PLUGIN_CONFIG.maxSpendPerTrade) continue;
                    int availableTradeCount = Math.min(trade.getMaxUses(), 20) - trade.getNumUses(); // each shift click can consume many trades
                    int maxTradesPerInputStack = inputStackSize / cost;
                    int outputsStackSize = ItemRegistry.REGISTRY.get(trade.getOutput().getId()).stackSize();
                    int maxTradesPerOutputStack = outputsStackSize / trade.getOutput().getAmount();
                    int maxTradesPerShiftClick = Math.min(maxTradesPerInputStack, maxTradesPerOutputStack);

                    for (int j = 0; j < availableTradeCount; j+= maxTradesPerShiftClick) {
                        actions.add(new SelectTrade(offersPacket.getContainerId(), i));
                        actions.add(new ShiftClick(offersPacket.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
                    }
                }
                actions.add(new CloseContainer(offersPacket.getContainerId()));
                purchaseFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .priority(PRIORITY)
                    .actions(actions)
                    .build());
                setState(State.TRADING_AWAIT_PURCHASE);
            }
            case TRADING_AWAIT_PURCHASE -> {
                if (purchaseFuture.isCompleted()) {
                    if (countBuyItemSlotUsages() > PLUGIN_CONFIG.buyItemStoreStacksThreshold) {
                        setState(State.STORE_GO_TO_CHEST);
                    } else if (ItemUtil.countItem(ItemRegistry.EMERALD.id()) < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
                        setState(State.RESTOCK);
                    } else {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    }
                }
            }
            case STORE_GO_TO_CHEST -> {
                var storeChest = PLUGIN_CONFIG.storeChest;
                storePathingFuture = BARITONE.rightClickBlock(storeChest.x(), storeChest.y(), storeChest.z());
                storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.STORE_DEPOSIT);
            }
            case STORE_DEPOSIT -> {
                if (storePathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            setState(State.STORE_GO_TO_CHEST);
                        }
                        return;
                    }
                    var outputItemIds = ItemUtil.getBuyItemIds(this);
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> outputItemIds.contains(i.getId())
                        ));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    storeDepositFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .priority(PRIORITY)
                        .actions(actions)
                        .build());
                    storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                    setState(State.STORE_AWAIT_DEPOSIT);
                }
            }
            case STORE_AWAIT_DEPOSIT -> {
                if (storeDepositFuture.isCompleted()) {
                    int buyItemCount = countBuyItem();
                    if (buyItemCount > 0) {
                        if (waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
                            warn("Unable to fully deposit buy items, trying to continue anyway");
                            setState(State.RESTOCK);
                        }
                        return;
                    }
                    setState(State.RESTOCK);
                }
            }
            case WAITING_FOR_VILLAGER_TRADE_RESTOCK -> {
                if (waitForRestockTimer.tick(20L * PLUGIN_CONFIG.villagerTradeRestockWaitSeconds)) {
                    interactedVillagersCache.invalidateAll();
                    setState(State.RESTOCK);
                }
            }
        }
    }

    private boolean isEBookTrade(VillagerTrade trade) {
        if (trade.getSecondInput() == null) return false;

        boolean hasBookInput = trade.getFirstInput().getId() == ItemRegistry.BOOK.id()
                || trade.getSecondInput().getId() == ItemRegistry.BOOK.id();
        boolean hasEmeraldTrade = trade.getFirstInput().getId() == ItemRegistry.EMERALD.id()
                || trade.getSecondInput().getId() == ItemRegistry.EMERALD.id();

        return hasBookInput && hasEmeraldTrade;
    }

    /**
     * Looks for any enchantments on the book that match the desired enchantments
     * @param itemStack the item stack to check
     * @return true if the item stack matches any of the desired enchantments, false otherwise
     */
    private boolean matchesDesiredEnchantments(ItemStack itemStack) {
        if (!EnchantmentUtil.isEnchantedBook(itemStack)) {
            return false;
        }

        Map<String, Integer> bookEnchantments = EnchantmentUtil.getEnchantmentMap(itemStack);

        // Check desired enchantments requirement
        if (!PLUGIN_CONFIG.onlyBuyDesiredEnchantments || PLUGIN_CONFIG.desiredEnchantments.isEmpty()) {
            if (PLUGIN_CONFIG.onlyBuyMaxLevelEnchantments) {
                // If only buying max level enchantments, check if the book has any enchantments
                if (!bookEnchantments.entrySet().stream().allMatch(entry -> {
                        String enchantmentName = entry.getKey();
                        int enchantmentLevel = entry.getValue();
                        return EnchantmentUtil.isMaxLevel(enchantmentName, enchantmentLevel);
                    })) {
                    debug("Book enchantments do not match max level requirements, skipping");
                    return false; // If any enchantment is not at max level, skip this book
                }
            }
            return true;
        }

        // If any enchantments are found buy it
        // Skip onlyBuyMaxEnchantments check as its kinda redundant if you check for desiredLevel anyway
        // This does not support multi enchant books but who gives a shit about none vanilla
        for (Map.Entry<String, Integer> desiredEntry : PLUGIN_CONFIG.desiredEnchantments.entrySet()) {
            int desiredLevel = desiredEntry.getValue();
            String desiredEnchant = desiredEntry.getKey();
            Integer actualLevel = bookEnchantments.get(desiredEnchant);

            if (actualLevel != null && actualLevel == desiredLevel) {
                return true;
            }
        }

        // No desired enchantments matched
        return false;
    }

    private void stop() {
        PLUGIN_CONFIG.enabled = false;
        syncEnabledFromConfig();
        saveConfigAsync();
    }

    private void setState(State newState) {
        debug("State change: {} -> {}", state, newState);
        this.state = newState;
    }

    private Optional<EntityLiving> nextVillager() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(e -> e.getEntityType() == EntityType.VILLAGER)
            .filter(e -> !interactedVillagersCache.asMap().containsKey(e.getEntityId()))
            .map(e -> (EntityLiving) e)
            .filter(e -> PLUGIN_CONFIG.villagerProfessions.contains(getVillagerProfession(e)))
            .min(Comparator.comparingDouble(e -> e.distanceSqTo(CACHE.getPlayerCache().getThePlayer())));
    }

    private VillagerProfession getVillagerProfession(EntityLiving villager) {
        var data = villager.getMetadataValue(18, MetadataTypes.VILLAGER_DATA, VillagerData.class);
        if (data == null) {
            return VillagerProfession.NONE;
        }
        return VillagerProfession.from(data.getProfession());
    }

    private int countBuyItem() {
        int count = 0;
        for (int id : ItemUtil.getBuyItemIds(this)) {
            count += ItemUtil.countItem(id);
        }
        return count;
    }

    private int countSlotUsages(int id) {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            if (item.getId() == id) {
                count++;
            }
        }
        return count;
    }

    private int countBuyItemSlotUsages() {
        int count = 0;
        for (int id : ItemUtil.getBuyItemIds(this)) {
            count += countSlotUsages(id);
        }
        return count;
    }

    public enum State {
        START,
        RESTOCK,
        TRADING_INTERACT_WITH_VILLAGER,
        TRADING_AWAIT_INTERACT_WITH_VILLAGER,
        TRADING_TRY_START_PURCHASE,
        TRADING_AWAIT_PURCHASE,
        STORE_GO_TO_CHEST,
        STORE_DEPOSIT,
        STORE_AWAIT_DEPOSIT,
        WAITING_FOR_VILLAGER_TRADE_RESTOCK
    }

    public enum VillagerProfession {
        NONE,
        ARMORER,
        BUTCHER,
        CARTOGRAPHER,
        CLERIC,
        FARMER,
        FISHERMAN,
        FLETCHER,
        LEATHERWORKER,
        LIBRARIAN,
        MASON,
        NITWIT,
        SHEPHERD,
        TOOLSMITH,
        WEAPONSMITH;

        private static final VillagerProfession[] VALUES = values();

        public static VillagerProfession from(int id) {
            return VALUES[id];
        }
    }

}
