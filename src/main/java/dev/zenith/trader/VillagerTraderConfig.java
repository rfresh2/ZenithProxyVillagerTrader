package dev.zenith.trader;

import com.google.common.collect.Lists;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemRegistry;

import java.util.ArrayList;

import static dev.zenith.trader.module.VillagerTrader.VillagerProfession;

public class VillagerTraderConfig {
    public boolean enabled = false;
    public ArrayList<VillagerProfession> villagerProfessions = Lists.newArrayList(VillagerProfession.CLERIC);
    public ArrayList<String> buyItems = Lists.newArrayList(ItemRegistry.EXPERIENCE_BOTTLE.name());
    public int restockStacks = 4;
    public BlockPos restockChest = BlockPos.ZERO;
    public BlockPos storeChest = BlockPos.ZERO;
    public int buyItemStoreStacksThreshold = 10;
    public int villagerTradeRestockWaitSeconds = 60;
    public int maxSpendPerTrade = 99;
}
