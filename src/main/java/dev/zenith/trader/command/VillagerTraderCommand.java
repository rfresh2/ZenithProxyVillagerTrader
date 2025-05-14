package dev.zenith.trader.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import dev.zenith.trader.module.VillagerTrader;

import java.util.Arrays;
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
              Trades with villagers
              """)
            .usageLines(
                "on/off",
                "professions add/del <profession>",
                "professions clear",
                "buyItems add/del <item>",
                "buyItems clear",
                "restockStacks <int>",
                "restockChest <x> <y> <z>",
                "storeChest <x> <y> <z>",
                "villagerTradeRestockWait <seconds>"
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
            .then(literal("villagerTradeRestockWait").then(argument("seconds", integer(1, 3600)).executes(c -> {
                PLUGIN_CONFIG.villagerTradeRestockWaitSeconds = getInteger(c, "seconds");
                c.getSource().getEmbed()
                    .title("Villager Trade Restock Wait Set");
            })))
            .then(literal("maxSpendPerTrade").then(argument("spend", integer(1, 1000)).executes(c -> {
                PLUGIN_CONFIG.maxSpendPerTrade = getInteger(c, "spend");
                c.getSource().getEmbed()
                    .title("Max Spend Per Trade Set");
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Villager Trader", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Professions", PLUGIN_CONFIG.villagerProfessions.stream().map(p -> p.name().toLowerCase()).collect(Collectors.joining(", ", "[", "]")))
            .addField("Buy Items", "[" + String.join(", ", PLUGIN_CONFIG.buyItems) + "]")
            .addField("Restock Stacks", PLUGIN_CONFIG.restockStacks)
            .addField("Restock Chest", "||" + (CONFIG.discord.reportCoords ? PLUGIN_CONFIG.restockChest : "Coords disabled") + "||")
            .addField("Store Chest", "||" + (CONFIG.discord.reportCoords ? PLUGIN_CONFIG.storeChest : "Coords disabled") + "||")
            .addField("Villager Trade Restock Wait", PLUGIN_CONFIG.villagerTradeRestockWaitSeconds + "s")
            .addField("Max Spend Per Trade", PLUGIN_CONFIG.maxSpendPerTrade)
            .primaryColor();
    }
}
