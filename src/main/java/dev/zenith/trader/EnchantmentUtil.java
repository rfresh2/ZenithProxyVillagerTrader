package dev.zenith.trader;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zenith.mc.enchantment.EnchantmentData;
import com.zenith.mc.enchantment.EnchantmentRegistry;
import com.zenith.mc.item.ItemRegistry;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EnchantmentUtil {

    public static final String ENCHANTMENT_LEVEL_MAX = "{\"aqua_affinity\":1,\"flame\":1,\"silk_touch\":1,\"mending\":1,\"infinity\":1,\"channeling\":1,\"binding_curse\":1,\"vanishing_curse\":1,\"multishot\":1,\"fire_aspect\":2,\"punch\":2,\"knockback\":2,\"frost_walker\":2,\"unbreaking\":3,\"sweeping_edge\":3,\"luck_of_the_sea\":3,\"lure\":3,\"wind_burst\":3,\"looting\":3,\"depth_strider\":3,\"riptide\":3,\"thorns\":3,\"quick_charge\":3,\"fortune\":3,\"swift_sneak\":3,\"soul_speed\":3,\"respiration\":3,\"loyalty\":3,\"protection\":4,\"projectile_protection\":4,\"blast_protection\":4,\"breach\":4,\"piercing\":4,\"feather_falling\":4,\"fire_protection\":4,\"bane_of_arthropods\":5,\"efficiency\":5,\"sharpness\":5,\"density\":5,\"smite\":5,\"impaling\":5,\"power\":5}";

    public static final HashMap<String, Integer> MAX_LEVEL_MAP = new Gson()
            .fromJson(ENCHANTMENT_LEVEL_MAX, new TypeToken<HashMap<String, Integer>>() {}.getType());


    public static int getEnchantmentLevel(ItemStack item, EnchantmentData enchantmentData) {
        return Optional.ofNullable(item)
                .map(ItemStack::getDataComponents)
                .map(dataComponents -> dataComponents.get(DataComponentTypes.ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.get(enchantmentData.id()))
                .orElse(0);
    }


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
        return MAX_LEVEL_MAP.get(name) == level;
    }

    public static Optional<Integer> getMaxLevel(String name) {
        return Optional.ofNullable(MAX_LEVEL_MAP.get(name));
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
