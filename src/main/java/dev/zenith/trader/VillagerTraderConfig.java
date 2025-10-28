package dev.zenith.trader;

import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

import java.util.LinkedHashMap;
import java.util.Objects;

import static dev.zenith.trader.module.VillagerTrader.VillagerProfession;

public class VillagerTraderConfig {
    public boolean enabled = false;
    public long waitForInteractTimeoutTicks = 20L;

    public LinkedHashMap<String, Trade> trades = new LinkedHashMap<>();

    public static class Trade {
        public VillagerProfession villagerProfession = VillagerProfession.CLERIC;
        public String inputItem1 = ItemRegistry.AIR.name();
        public String inputItem2 = ItemRegistry.AIR.name();
        public String outputItem = ItemRegistry.AIR.name();
        public BlockPos inputItem1Chest = BlockPos.ZERO;
        public BlockPos inputItem2Chest = BlockPos.ZERO;
        public BlockPos outputChest = BlockPos.ZERO;
        public int inputItem1RestockStacks = 4;
        public int inputItem1RestockCountThreshold = 64;
        public int inputItem2RestockStacks = 4;
        public int inputItem2RestockCountThreshold = 64;
        public int outputItemStoreCountThreshold = 64;
        public int maxInput1PerTrade = 99;
        public int maxInput2PerTrade = 99;
        public PostTradeStoreMode postTradeStoreMode = PostTradeStoreMode.NONE;
        public enum PostTradeStoreMode {
            NONE,
            TO_RESTOCK,
            TO_OVERFLOW
        }
        public BlockPos overflowChestPos = BlockPos.ZERO;
        public Object2IntLinkedOpenHashMap<String> outputItemEnchantments = new Object2IntLinkedOpenHashMap<>();

        public boolean has2InputTrade() {
            return !Objects.equals(inputItem2, ItemRegistry.AIR.name());
        }

        public boolean hasEmeraldInputs() {
            return Objects.equals(inputItem1, ItemRegistry.EMERALD.name()) || Objects.equals(inputItem2, ItemRegistry.EMERALD.name());
        }

        public ItemData getInputItem1() {
            return ItemRegistry.REGISTRY.get(inputItem1);
        }

        public ItemData getInputItem2() {
            return ItemRegistry.REGISTRY.get(inputItem2);
        }

        public ItemData getOutputItem() {
            return ItemRegistry.REGISTRY.get(outputItem);
        }
    }
}
