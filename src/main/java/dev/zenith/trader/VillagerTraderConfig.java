package dev.zenith.trader;

import com.google.common.collect.Lists;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.zenith.trader.module.VillagerTrader.VillagerProfession;

public class VillagerTraderConfig {
    public boolean enabled = false;
    public ArrayList<VillagerProfession> villagerProfessions = Lists.newArrayList(VillagerProfession.CLERIC);
    public ArrayList<String> buyItems = Lists.newArrayList(ItemRegistry.EXPERIENCE_BOTTLE.name());
    public int restockStacks = 2;
    public int restockEmeraldCountThreshold = 64;
    public BlockPos restockChest = BlockPos.ZERO;
    public BlockPos storeChest = BlockPos.ZERO;
    public int buyItemStoreStacksThreshold = 10;
    public BlockPos bookRestockChest = BlockPos.ZERO;
    public int bookRestockStacksThreshold = 30;
    public int villagerTradeRestockWaitSeconds = 60;
    public int maxSpendPerTrade = 99;
    public Map<String, Integer> itemMaxSpendPerTrade = new LinkedHashMap<>();
    public long waitForInteractTimeoutTicks = 20L;
    public boolean buyEnchantBook = true;
    public boolean onlyBuyDesiredEnchantments = true;
    public boolean onlyBuyMaxLevelEnchantments = true;
    public Map<String, Integer> desiredEnchantments = new LinkedHashMap<>();
}
