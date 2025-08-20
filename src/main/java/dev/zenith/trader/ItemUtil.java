package dev.zenith.trader;

import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemRegistry;
import dev.zenith.trader.module.VillagerTrader;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static com.zenith.Globals.CACHE;
import static dev.zenith.trader.VillagerTraderPlugin.PLUGIN_CONFIG;

/*
 * @author IceTank
 * @since 20.08.2025
 */
public class ItemUtil {
    public static int countItem(int id) {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            if (item.getId() == id) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static int countInvEmptySlots() {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            if (inv.get(i) == Container.EMPTY_STACK) {
                count++;
            }
        }
        return count;
    }

    public static IntSet getBuyItemIds(VillagerTrader villagerTrader) {
        IntSet buyItemIds = new IntOpenHashSet();
        for (var iterator = PLUGIN_CONFIG.buyItems.iterator(); iterator.hasNext(); ) {
            final String itemName = iterator.next();
            var itemData = ItemRegistry.REGISTRY.get(itemName);
            if (itemData != null) {
                buyItemIds.add(itemData.id());
            } else {
                villagerTrader.warn("Buy item {} not found in registry, removing", itemName);
                iterator.remove();
            }
        }
        return buyItemIds;
    }
}
