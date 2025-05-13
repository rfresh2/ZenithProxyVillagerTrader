package dev.zenith.trader.module;

import com.github.rfresh2.EventConsumer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.inventory.Container;
import com.zenith.discord.Embed;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.*;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.inventory.util.InventoryUtil;
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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.VillagerData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static dev.zenith.trader.VillagerTraderPlugin.PLUGIN_CONFIG;

public class VillagerTrader extends Module {
    public static final int PRIORITY = 9000;
    private State state = State.RESTOCK_GO_TO_CHEST;
    private final Cache<Integer, Boolean> interactedVillagersCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();
    private PathingRequestFuture restockPathingFuture = PathingRequestFuture.rejected;
    private RequestFuture restockWithdrawFuture = RequestFuture.rejected;
    private RequestFuture emeraldBlockCraftFuture = RequestFuture.rejected;
    private PathingRequestFuture interactWithVillagerFuture = PathingRequestFuture.rejected;
    private ClientboundMerchantOffersPacket offersPacket = null;
    private RequestFuture purchaseFuture = RequestFuture.rejected;
    private PathingRequestFuture storePathingFuture = PathingRequestFuture.rejected;
    private RequestFuture storeDepositFuture = RequestFuture.rejected;
    private final Timer waitForRestockTimer = Timers.tickTimer();

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
        state = State.RESTOCK_GO_TO_CHEST;
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
            case RESTOCK_GO_TO_CHEST -> {
                int emeraldCount = countItem(ItemRegistry.EMERALD.id());
                int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockStacks) {
                    var restockChest = PLUGIN_CONFIG.restockChest;
                    restockPathingFuture = BARITONE.rightClickBlock(restockChest.x(), restockChest.y(), restockChest.z());
                    setState(State.RESTOCK_PATHING_TO_CHEST);
                } else if (emeraldBlockCount > 0) {
                    setState(State.RESTOCK_CRAFT_EMERALD_BLOCKS);
                } else {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                }
            }
            case RESTOCK_PATHING_TO_CHEST -> {
                if (restockPathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getType() == ContainerType.GENERIC_9X6) {
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
                        setState(State.RESTOCK_WITHDRAWING_FROM_CHEST);
                    } else {
                        setState(State.RESTOCK_GO_TO_CHEST);
                    }
                }
            }
            case RESTOCK_WITHDRAWING_FROM_CHEST -> {
                if (restockWithdrawFuture.isCompleted()) {
                    int emeraldCount = countItem(ItemRegistry.EMERALD.id());
                    int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                    if (emeraldCount + (emeraldBlockCount * 9) < PLUGIN_CONFIG.restockStacks) {
                        discordNotification(Embed.builder()
                            .title("Villager Trader")
                            .description("Not enough emeralds to continue trading. Disabling.")
                            .errorColor());
                        stop();
                        return;
                    }
                    if (emeraldBlockCount > 0) {
                        setState(State.RESTOCK_CRAFT_EMERALD_BLOCKS);
                    } else {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    }
                }
            }
            case RESTOCK_CRAFT_EMERALD_BLOCKS -> {
                int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockCount == 0) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    return;
                }
                int emptySlots = countInvEmptySlots();
                if (emptySlots < 4) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    return;
                }
                int emeraldBlockSlot = InventoryUtil.searchPlayerInventory(i -> i.getId() == ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockSlot == -1) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
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
                setState(State.RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS);
            }
            case RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS -> {
                if (emeraldBlockCraftFuture.isCompleted()) {
                    int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                    if (emeraldBlockCount > 0) {
                        setState(State.RESTOCK_CRAFT_EMERALD_BLOCKS);
                    } else {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    }
                }
            }
            case TRADING_INTERACT_WITH_VILLAGER -> {
                int buyItemCount = countBuyItem();
                if (buyItemCount > PLUGIN_CONFIG.outputThreshold) {
                    setState(State.STORE_GO_TO_CHEST);
                    return;
                }
                var nextVillagerOptional = nextVillager();
                if (nextVillagerOptional.isEmpty()) {
                    if (interactedVillagersCache.asMap().isEmpty()) {
                        discordNotification(Embed.builder()
                            .title("Villager Trader")
                            .description("No villagers to trade with. Disabling.")
                            .errorColor());
                        stop();
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
                interactedVillagersCache.put(nextVillager.getEntityId(), true);
                setState(State.TRADING_AWAIT_INTERACT_WITH_VILLAGER);
            }
            case TRADING_AWAIT_INTERACT_WITH_VILLAGER -> {
                if (interactWithVillagerFuture.isCompleted()) {
                    if (offersPacket == null) {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        return;
                    }
                    if (offersPacket.getContainerId() != CACHE.getPlayerCache().getInventoryCache().getOpenContainerId()) {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        return;
                    }
                    setState(State.TRADING_TRY_START_PURCHASE);
                }
            }
            case TRADING_TRY_START_PURCHASE -> {
                var buyItemIds = getBuyItemIds();
                var trades = offersPacket.getTrades();
                List<InventoryAction> actions = Lists.newArrayList();
                for (int i = 0; i < trades.length; i++) {
                    var trade = trades[i];
                    if (trade.isTradeDisabled()) continue;
                    if (trade.getOutput() == null) continue;
                    if (!buyItemIds.contains(trade.getOutput().getId())) continue;
                    if (trade.getFirstInput().getId() != ItemRegistry.EMERALD.id()) continue;
                    if (trade.getSecondInput() != null) continue;
                    int inputStackSize = 64; // emeralds
                    int baseCost = trade.getFirstInput().getAmount();
                    int addnlDemandCost = Math.max(0, MathHelper.floorI((trade.getFirstInput().getAmount() * trade.getDemand() * trade.getPriceMultiplier())));
                    int cost = MathHelper.clamp(baseCost + addnlDemandCost + trade.getSpecialPrice(), 1, inputStackSize);
                    int availableTradeCount = trade.getMaxUses() - trade.getNumUses(); // each shift click can consume many trades
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
                    if (countBuyItem() >= PLUGIN_CONFIG.outputThreshold || countItem(ItemRegistry.EMERALD.id()) < 64) {
                        setState(State.STORE_GO_TO_CHEST);
                    } else {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    }
                }
            }
            case STORE_GO_TO_CHEST -> {
                var storeChest = PLUGIN_CONFIG.storeChest;
                storePathingFuture = BARITONE.rightClickBlock(storeChest.x(), storeChest.y(), storeChest.z());
                setState(State.STORE_DEPOSIT);
            }
            case STORE_DEPOSIT -> {
                if (storePathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getType() != ContainerType.GENERIC_9X6) {
                        setState(State.STORE_GO_TO_CHEST);
                        return;
                    }
                    var outputItemIds = getBuyItemIds();
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
                    setState(State.STORE_AWAIT_DEPOSIT);
                }
            }
            case STORE_AWAIT_DEPOSIT -> {
                if (storeDepositFuture.isCompleted()) {
                    int buyItemCount = countBuyItem();
                    if (buyItemCount > 0) {
                        discordNotification(Embed.builder()
                            .title("Villager Trader")
                            .description("Unable to deposit buy items. Disabling.")
                            .errorColor());
                        stop();
                        return;
                    }
                    setState(State.RESTOCK_GO_TO_CHEST);
                }
            }
            case WAITING_FOR_VILLAGER_TRADE_RESTOCK -> {
                if (waitForRestockTimer.tick(20L * PLUGIN_CONFIG.villagerTradeRestockWaitSeconds)) {
                    setState(State.RESTOCK_GO_TO_CHEST);
                }
            }
        }
    }

    private IntSet getBuyItemIds() {
        IntSet buyItemIds = new IntOpenHashSet();
        for (var iterator = PLUGIN_CONFIG.buyItems.iterator(); iterator.hasNext(); ) {
            final String itemName = iterator.next();
            var itemData = ItemRegistry.REGISTRY.get(itemName);
            if (itemData != null) {
                buyItemIds.add(itemData.id());
            } else {
                warn("Buy item {} not found in registry, removing", itemName);
                iterator.remove();
            }
        }
        return buyItemIds;
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

    private int countItem(int id) {
        return CACHE.getPlayerCache().getPlayerInventory().stream()
            .filter(item -> item != Container.EMPTY_STACK)
            .filter(item -> item.getId() == id)
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private int countInvEmptySlots() {
        int count = 0;
        for (int i = 9; i < 45; i++) {
            if (CACHE.getPlayerCache().getPlayerInventory().get(i) == Container.EMPTY_STACK) {
                count++;
            }
        }
        return count;
    }

    private int countBuyItem() {
        int count = 0;
        for (int id : getBuyItemIds()) {
            count += countItem(id);
        }
        return count;
    }

    public enum State {
        RESTOCK_GO_TO_CHEST,
        RESTOCK_PATHING_TO_CHEST,
        RESTOCK_WITHDRAWING_FROM_CHEST,
        RESTOCK_CRAFT_EMERALD_BLOCKS,
        RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS,
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
