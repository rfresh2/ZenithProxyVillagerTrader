package dev.zenith.trader;

import com.zenith.mc.enchantment.EnchantmentData;
import com.zenith.mc.enchantment.EnchantmentRegistry;
import com.zenith.mc.item.ItemRegistry;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.util.*;
import java.util.stream.Collectors;

public class EnchantmentUtil {

    public static Map<Integer, Integer> getAllEnchantments(ItemStack itemStack) {
        Int2IntMap map = Optional.ofNullable(itemStack.getDataComponents())
                .map(dataComponents -> dataComponents.get(DataComponentTypes.STORED_ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .orElse(new Int2IntArrayMap());
        return map;
    }


    public static boolean isEnchantedBook(ItemStack itemStack) {
        return itemStack != null && itemStack.getId() == ItemRegistry.ENCHANTED_BOOK.id();
    }


    public static boolean isMaxLevel(String name, int level) {

        return getMaxLevel(name) == level;
    }

    public static int getMaxLevel(String name) {
        EnchantmentData data = EnchantmentRegistry.REGISTRY.get(name);
        if (data == null) {
            return 0;
        }
        return data.maxLevel();
    }

    public static List<String> getAllEnchantment() {
        List<String> enchantments = new ArrayList<>();
        for (int i = 0; i < EnchantmentRegistry.REGISTRY.size(); i++) {
            EnchantmentData data = EnchantmentRegistry.REGISTRY.get(i);
            enchantments.add(data.name());
        }
        return enchantments;
    }

    public static Map<String, Integer> getEnchantmentMap(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.STORED_ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.int2IntEntrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> Optional.ofNullable(EnchantmentRegistry.REGISTRY.get(entry.getIntKey()))
                                        .map(EnchantmentData::name)
                                        .orElse("unknown_" + entry.getIntKey()),
                                entry -> entry.getIntValue(),
                                (existing, replacement) -> replacement // 处理重复key的情况
                        )))
                .orElse(new HashMap<>());
    }

    // 使用Stream API的版本
    public static String mapToJsonStringStream(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        return map.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
