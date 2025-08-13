package dev.zenith.trader.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import dev.zenith.trader.EnchantmentUtil;
import dev.zenith.trader.module.VillagerTrader;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static dev.zenith.trader.VillagerTraderPlugin.PLUGIN_CONFIG;
import static dev.zenith.trader.module.VillagerTrader.VillagerProfession;

public class VillagerTraderCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("trader")
            .category(CommandCategory.MODULE)
            .description("""
              Buys items from villagers with emeralds.
              
              Automatically restocks emeralds, trades with villagers, and stores the bought items
              
              `restockStacks` -> how many stacks of emeralds/emerald blocks to restock. Emerald blocks are crafted down to emeralds.
              `villagerTradeRestockWait` -> seconds it waits after all villagers are out of stock. 1200 = 1 minecraft day
              `maxSpendPerTrade` -> max emeralds to spend per trade
              `buyItemStoreStacksThreshold` -> how many stacks/slots of items to buy before it stores them
              `waitForInteractTimeout` -> timeout for server interactions like opening villager trade window
              `desiredEnchantments` -> specific enchantments to buy on enchanted books
              `onlyBuyDesiredEnchantments` -> whether to only buy enchanted books with desired enchantments
              `onlyBuyMaxLevelEnchantments` -> whether to only buy enchanted books at maximum levels
              """)
            .usageLines(
                "on/off",
                "professions add/del <profession>",
                "professions clear",
                "buyItems add/del <item>",
                "buyItems clear",
                "restockStacks <stacks>",
                "restockEmeraldCountThreshold <amount>",
                "restockChest <x> <y> <z>",
                "storeChest <x> <y> <z>",
                "villagerTradeRestockWait <seconds>",
                "maxSpendPerTrade <amount>",
                "buyItemStoreStacksThreshold <stacks>",
                "waitForInteractTimeout <ticks>",
                "desiredEnchantments add <enchantment> <level>",
                "desiredEnchantments del <enchantment>",
                "desiredEnchantments clear",
                "desiredEnchantments list",
                "desiredEnchantments available",
                "onlyBuyDesiredEnchantments <true/false>",
                "onlyBuyMaxLevelEnchantments <true/false>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("trader")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                MODULE.get(VillagerTrader.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Villager Trader " + toggleStrCaps(PLUGIN_CONFIG.enabled))
                    .primaryColor();
            }))
            .then(literal("professions")
                .then(literal("add").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                    var profStr = getString(c, "profession");
                    VillagerProfession prof;
                    try {
                        prof = VillagerProfession.valueOf(profStr.toUpperCase());
                    } catch (Exception e) {
                        c.getSource().getEmbed()
                            .title("Invalid Profession")
                            .description("Valid professions: "
                                + Arrays.stream(VillagerProfession.values())
                                    .map(p -> p.name().toLowerCase())
                                    .collect(Collectors.joining(", ")));
                        return ERROR;
                    }
                    if (!PLUGIN_CONFIG.villagerProfessions.contains(prof)) {
                        PLUGIN_CONFIG.villagerProfessions.add(prof);
                    }
                    c.getSource().getEmbed()
                        .title("Profession Added");
                    return OK;
                })))
                .then(literal("del").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                    var profStr = getString(c, "profession");
                    VillagerProfession prof;
                    try {
                        prof = VillagerProfession.valueOf(profStr.toUpperCase());
                    } catch (Exception e) {
                        c.getSource().getEmbed()
                            .title("Invalid Profession")
                            .description("Valid professions: "
                                + Arrays.stream(VillagerProfession.values())
                                .map(p -> p.name().toLowerCase())
                                .collect(Collectors.joining(", ")));
                        return ERROR;
                    }
                    PLUGIN_CONFIG.villagerProfessions.remove(prof);
                    c.getSource().getEmbed()
                        .title("Profession Removed");
                    return OK;
                })))
                .then(literal("clear").executes(c -> {
                    PLUGIN_CONFIG.villagerProfessions.clear();
                    c.getSource().getEmbed()
                        .title("Professions Cleared");
                })))
            .then(literal("buyItems")
                .then(literal("add").then(argument("item", item()).executes(c -> {
                    var itemData = getItem(c, "item");
                    if (!PLUGIN_CONFIG.buyItems.contains(itemData.name())) {
                        PLUGIN_CONFIG.buyItems.add(itemData.name());
                    }
                    c.getSource().getEmbed()
                        .title("Item Added");
                    return OK;
                })))
                .then(literal("del").then(argument("item", item()).executes(c -> {
                    var itemData = getItem(c, "item");
                    PLUGIN_CONFIG.buyItems.remove(itemData.name());
                    c.getSource().getEmbed()
                        .title("Item Removed");
                    return OK;
                })))
                .then(literal("clear").executes(c -> {
                    PLUGIN_CONFIG.buyItems.clear();
                    c.getSource().getEmbed()
                        .title("Items Cleared");
                })))
            .then(literal("restockStacks").then(argument("stackCount", integer(1, 36)).executes(c -> {
                PLUGIN_CONFIG.restockStacks = getInteger(c, "stackCount");
                c.getSource().getEmbed()
                    .title("Restock Stacks Set");
            })))
            .then(literal("restockEmeraldCountThreshold").then(argument("amount", integer(1, 250)).executes(c -> {
                PLUGIN_CONFIG.restockEmeraldCountThreshold = getInteger(c, "amount");
                c.getSource().getEmbed()
                    .title("Restock Emerald Count Threshold Set");
            })))
            .then(literal("restockChest").then(argument("pos", blockPos()).executes(c -> {
                PLUGIN_CONFIG.restockChest = getBlockPos(c, "pos");
                c.getSource().getEmbed()
                    .title("Restock Chest Set");
            })))
            .then(literal("storeChest").then(argument("pos", blockPos()).executes(c -> {
                PLUGIN_CONFIG.storeChest = getBlockPos(c, "pos");
                c.getSource().getEmbed()
                    .title("Store Chest Set");
            })))
            .then(literal("villagerTradeRestockWait").then(argument("seconds", integer(1, (int) TimeUnit.MINUTES.toSeconds(30))).executes(c -> {
                PLUGIN_CONFIG.villagerTradeRestockWaitSeconds = getInteger(c, "seconds");
                c.getSource().getEmbed()
                    .title("Villager Trade Restock Wait Set");
            })))
            .then(literal("maxSpendPerTrade").then(argument("spend", integer(1, 1000)).executes(c -> {
                PLUGIN_CONFIG.maxSpendPerTrade = getInteger(c, "spend");
                c.getSource().getEmbed()
                    .title("Max Spend Per Trade Set");
            })))
            .then(literal("buyItemStoreStacksThreshold").then(argument("stackCount", integer(1, 36)).executes(c -> {
                PLUGIN_CONFIG.buyItemStoreStacksThreshold = getInteger(c, "stackCount");
                c.getSource().getEmbed()
                    .title("Buy Item Store Stacks Threshold Set");
            })))
            .then(literal("waitForInteractTimeout").then(argument("ticks", integer(1, 1000)).executes(c -> {
                PLUGIN_CONFIG.waitForInteractTimeoutTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Wait For Interact Timeout Set");
            })))
            .then(literal("desiredEnchantments")
                .then(literal("add").then(argument("enchantment", enumStrings(EnchantmentUtil.MAX_LEVEL_MAP.keySet().toArray(new String[0]))).then(argument("level", integer(1, 5)).executes(c -> {
                    String enchantment = getString(c, "enchantment");
                    int level = getInteger(c, "level");
                    Integer maxLevel = EnchantmentUtil.MAX_LEVEL_MAP.get(enchantment);
                    if (maxLevel == null) {
                        c.getSource().getEmbed()
                            .title("Invalid Enchantment")
                            .description("Enchantment not found: " + enchantment);
                        return ERROR;
                    }
                    if (level < 1 || level > maxLevel) {
                        c.getSource().getEmbed()
                            .title("Invalid Level")
                            .description("Level must be between 1 and " + maxLevel + " for " + enchantment);
                        return ERROR;
                    }
                    PLUGIN_CONFIG.desiredEnchantments.put(enchantment, level);
                    c.getSource().getEmbed()
                        .title("Enchantment Added")
                        .description("Added " + enchantment + " level " + level);
                    return OK;
                }))))
                .then(literal("del").then(argument("enchantment", enumStrings(EnchantmentUtil.MAX_LEVEL_MAP.keySet().toArray(new String[0]))).executes(c -> {
                    String enchantment = getString(c, "enchantment");
                    Integer removed = PLUGIN_CONFIG.desiredEnchantments.remove(enchantment);
                    if (removed == null) {
                        c.getSource().getEmbed()
                            .title("Enchantment Not Found")
                            .description("Enchantment not in desired list: " + enchantment);
                        return ERROR;
                    }
                    c.getSource().getEmbed()
                        .title("Enchantment Removed")
                        .description("Removed " + enchantment + " level " + removed);
                    return OK;
                })))
                .then(literal("clear").executes(c -> {
                    int count = PLUGIN_CONFIG.desiredEnchantments.size();
                    PLUGIN_CONFIG.desiredEnchantments.clear();
                    c.getSource().getEmbed()
                        .title("Enchantments Cleared")
                        .description("Cleared " + count + " enchantments");
                    return OK;
                })))
                .then(literal("list").executes(c -> {
                    if (PLUGIN_CONFIG.desiredEnchantments.isEmpty()) {
                        c.getSource().getEmbed()
                            .title("Configured Enchantments")
                            .description("No enchantments configured. Use `desiredEnchantments add <enchantment> <level>` to add enchantments.");
                        return OK;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Currently configured enchantments:\n\n");

                    // Group by level for better readability
                    Map<Integer, java.util.List<String>> enchantmentsByLevel = new java.util.TreeMap<>();

                    for (Map.Entry<String, Integer> entry : PLUGIN_CONFIG.desiredEnchantments.entrySet()) {
                        String enchantment = entry.getKey();
                        int level = entry.getValue();

                        enchantmentsByLevel.computeIfAbsent(level, k -> new java.util.ArrayList<>())
                            .add(enchantment);
                    }

                    for (Map.Entry<Integer, java.util.List<String>> entry : enchantmentsByLevel.entrySet()) {
                        int level = entry.getKey();
                        java.util.List<String> enchantments = entry.getValue();
                        enchantments.sort(String.CASE_INSENSITIVE_ORDER);

                        sb.append("**Level ").append(level).append("**: ")
                          .append(String.join(", ", enchantments))
                          .append("\n");
                    }

                    c.getSource().getEmbed()
                        .title("Configured Enchantments")
                        .description(sb.toString());
                    return OK;
                }))
                .then(literal("available").executes(c -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Available enchantments and their maximum levels:\n\n");

                    // Group enchantments by level for better readability
                    Map<Integer, java.util.List<String>> enchantmentsByLevel = new java.util.TreeMap<>();

                    for (Map.Entry<String, Integer> entry : EnchantmentUtil.MAX_LEVEL_MAP.entrySet()) {
                        String enchantment = entry.getKey();
                        int maxLevel = entry.getValue();

                        enchantmentsByLevel.computeIfAbsent(maxLevel, k -> new java.util.ArrayList<>())
                            .add(enchantment);
                    }

                    for (Map.Entry<Integer, java.util.List<String>> entry : enchantmentsByLevel.entrySet()) {
                        int level = entry.getKey();
                        java.util.List<String> enchantments = entry.getValue();
                        enchantments.sort(String.CASE_INSENSITIVE_ORDER);

                        sb.append("**Level ").append(level).append("**: ")
                          .append(String.join(", ", enchantments))
                          .append("\n");
                    }

                    c.getSource().getEmbed()
                        .title("Available Enchantments")
                        .description(sb.toString());
                    return OK;
                }))
            .then(literal("onlyBuyDesiredEnchantments").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.onlyBuyDesiredEnchantments = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Only Buy Desired Enchantments " + (PLUGIN_CONFIG.onlyBuyDesiredEnchantments ? "Enabled" : "Disabled"));
                return OK;
            })))
            .then(literal("onlyBuyMaxLevelEnchantments").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.onlyBuyMaxLevelEnchantments = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Only Buy Max Level Enchantments " + (PLUGIN_CONFIG.onlyBuyMaxLevelEnchantments ? "Enabled" : "Disabled"));
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        String enchantmentsStr = PLUGIN_CONFIG.desiredEnchantments.entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(", ", "[", "]"));

        embed
            .addField("Villager Trader", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Professions", PLUGIN_CONFIG.villagerProfessions.stream().map(p -> p.name().toLowerCase()).collect(Collectors.joining(", ", "[", "]")))
            .addField("Buy Items", "[" + String.join(", ", PLUGIN_CONFIG.buyItems) + "]")
            .addField("Restock Stacks", PLUGIN_CONFIG.restockStacks)
            .addField("Restock Emerald Count Threshold", PLUGIN_CONFIG.restockEmeraldCountThreshold)
            .addField("Restock Chest", "||" + (CONFIG.discord.reportCoords ? PLUGIN_CONFIG.restockChest : "Coords disabled") + "||")
            .addField("Store Chest", "||" + (CONFIG.discord.reportCoords ? PLUGIN_CONFIG.storeChest : "Coords disabled") + "||")
            .addField("Villager Trade Restock Wait", PLUGIN_CONFIG.villagerTradeRestockWaitSeconds + "s")
            .addField("Max Spend Per Trade", PLUGIN_CONFIG.maxSpendPerTrade)
            .addField("Buy Item Store Stacks Threshold", PLUGIN_CONFIG.buyItemStoreStacksThreshold + " stacks")
            .addField("Wait For Interact Timeout", PLUGIN_CONFIG.waitForInteractTimeoutTicks + " ticks")
            .addField("Desired Enchantments", PLUGIN_CONFIG.desiredEnchantments.isEmpty() ? "[None]" : enchantmentsStr)
            .addField("Only Buy Desired Enchantments", toggleStr(PLUGIN_CONFIG.onlyBuyDesiredEnchantments))
            .addField("Only Buy Max Level Enchantments", toggleStr(PLUGIN_CONFIG.onlyBuyMaxLevelEnchantments))
            .primaryColor();
    }
}
