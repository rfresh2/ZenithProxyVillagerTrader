package dev.zenith.trader.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.command.brigadier.CustomStringArgumentType;
import dev.zenith.trader.VillagerTraderConfig;
import dev.zenith.trader.module.VillagerTrader;

import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.RegistryDataArgument.enchantment;
import static com.zenith.command.brigadier.RegistryDataArgument.getEnchantment;
import static com.zenith.command.brigadier.TimeArgument.time;
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
              Automatically restocks, trades with villagers, and stores the bought items.
              
              Multiple trades can be configured, each for different villager professions and items.
              
              See `set help` for additional commands that modify trade settings, like enchantments, post trade storage modes, and restock amounts.
              
              `waitForInteractTimeout` -> timeout for server interactions like opening villager trade window
              """)
            .usageLines(
                "on/off",
                "add <id> <profession> <inputItem1> <outputItem> <inputItem1ChestPos> <outputChestPos>",
                "add <id> <profession> <inputItem1> <inputItem2> <outputItem> <inputItem1ChestPos> <inputItem2ChestPos> <outputChestPos>",
                "del <id>",
                "clear",
                "list",
                "set help",
                "waitForInteractTimeout <ticks>"
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
                    .title("Villager Trader " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("add").then(argument("id", wordWithChars())
                .then(argument("profession", enumStrings(VillagerProfession.values())).then(argument("inputItem1", item()).then(argument("buyItem", item()).then(argument("inputItem1Pos", blockPos()).then(argument("storeChestPos", blockPos()).executes(c -> {
                    var id = getString(c, "id");
                    var profession = VillagerProfession.valueOf(getString(c, "profession").toUpperCase());
                    var inputItem1 = getItem(c, "inputItem1");
                    var buyItem = getItem(c, "buyItem");
                    var inputItem1Pos = getBlockPos(c, "inputItem1Pos");
                    var storeChestPos = getBlockPos(c, "storeChestPos");
                    var trade = new VillagerTraderConfig.Trade();
                    trade.villagerProfession = profession;
                    trade.inputItem1 = inputItem1.name();
                    trade.outputItem = buyItem.name();
                    trade.inputItem1Chest = inputItem1Pos;
                    trade.outputChest = storeChestPos;
                    inEventLoop(() -> {
                        PLUGIN_CONFIG.trades.put(id, trade);
                        MODULE.get(VillagerTrader.class).onTradeListChange();
                    });
                    c.getSource().getEmbed()
                        .title("Trade Added")
                        .description(printTrade(id, trade));
                }))))))
                .then(argument("profession", enumStrings(VillagerProfession.values())).then(argument("inputItem1", item()).then(argument("inputItem2", item()).then(argument("buyItem", item()).then(argument("inputItem1Pos", blockPos()).then(argument("inputItem2Pos", blockPos()).then(argument("storeChestPos", blockPos()).executes(c -> {
                    var id = getString(c, "id");
                    var profession = VillagerProfession.valueOf(getString(c, "profession").toUpperCase());
                    var inputItem1 = getItem(c, "inputItem1");
                    var inputItem2 = getItem(c, "inputItem2");
                    var buyItem = getItem(c, "buyItem");
                    var inputItem1Pos = getBlockPos(c, "inputItem1Pos");
                    var inputItem2Pos = getBlockPos(c, "inputItem2Pos");
                    var storeChestPos = getBlockPos(c, "storeChestPos");
                    var trade = new VillagerTraderConfig.Trade();
                    trade.villagerProfession = profession;
                    trade.inputItem1 = inputItem1.name();
                    trade.inputItem2 = inputItem2.name();
                    trade.outputItem = buyItem.name();
                    trade.inputItem1Chest = inputItem1Pos;
                    trade.inputItem2Chest = inputItem2Pos;
                    trade.outputChest = storeChestPos;
                    inEventLoop(() -> {
                        PLUGIN_CONFIG.trades.put(id, trade);
                        MODULE.get(VillagerTrader.class).onTradeListChange();
                    });
                    c.getSource().getEmbed()
                        .title("Trade Added")
                        .description(printTrade(id, trade));
                }))))))))))
            .then(literal("set")
                .then(literal("help").executes(c -> {
                    List<String> setCommands = List.of(
                        "set <id> profession <profession>",
                        "set <id> inputItem1 <item>",
                        "set <id> inputItem2 <item>",
                        "set <id> outputItem <item>",
                        "set <id> inputItem1Chest <x> <y> <z>",
                        "set <id> inputItem2Chest <x> <y> <z>",
                        "set <id> outputChest <x> <y> <z>",
                        "set <id> maxInput1PerTrade <count>",
                        "set <id> maxInput2PerTrade <count>",
                        "set <id> inputItem1RestockStacks <count>",
                        "set <id> inputItem2RestockStacks <count>",
                        "set <id> inputItem1RestockCountThreshold <count>",
                        "set <id> inputItem2RestockCountThreshold <count>",
                        "set <id> outputItemStoreCountThreshold <count>",
                        "set <id> outputEnchants add <enchantment> <level>",
                        "set <id> outputEnchants del <enchantment>",
                        "set <id> outputEnchants clear",
                        "set <id> outputEnchants list",
                        "set <id> postTradeStore <none/to_restock/to_overflow>",
                        "set <id> overflowChest <x> <y> <z>"
                    );
                    c.getSource().getEmbed()
                        .title("Trade Settings")
                        .description(setCommands.stream().map(a -> "`" + a + "`").reduce((a, b) -> a + "\n" + b).orElse(""));
                    c.getSource().getData().put("list", true);
                }))
                .then(argument("id", wordWithChars())
                    .then(literal("inputItem1").then(argument("inputItem1", item()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var inputItem1 = getItem(c, "inputItem1");
                        trade.inputItem1 = inputItem1.name();
                        c.getSource().getEmbed()
                            .title("Input Item 1 Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("profession").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.villagerProfession = VillagerProfession.valueOf(getString(c, "profession").toUpperCase());
                        c.getSource().getEmbed()
                            .title("Profession Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem2").then(argument("inputItem2", item()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var inputItem2 = getItem(c, "inputItem2");
                        trade.inputItem2 = inputItem2.name();
                        c.getSource().getEmbed()
                            .title("Input Item 2 Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("outputItem").then(argument("outputItem", item()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var outputItem = getItem(c, "outputItem");
                        trade.outputItem = outputItem.name();
                        c.getSource().getEmbed()
                            .title("Output Item Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem1Chest").then(argument("inputItem1Chest", blockPos()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var inputItem1Chest = getBlockPos(c, "inputItem1Chest");
                        trade.inputItem1Chest = inputItem1Chest;
                        c.getSource().getEmbed()
                            .title("Input Item 1 Chest Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem2Chest").then(argument("inputItem2Chest", blockPos()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var inputItem2Chest = getBlockPos(c, "inputItem2Chest");
                        trade.inputItem2Chest = inputItem2Chest;
                        c.getSource().getEmbed()
                            .title("Input Item 2 Chest Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("outputChest").then(argument("outputChest", blockPos()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var outputChest = getBlockPos(c, "outputChest");
                        trade.outputChest = outputChest;
                        c.getSource().getEmbed()
                            .title("Output Chest Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("maxInput1PerTrade").then(argument("maxInput1PerTrade", integer(1)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var maxInput1PerTrade = getInteger(c, "maxInput1PerTrade");
                        trade.maxInput1PerTrade = maxInput1PerTrade;
                        c.getSource().getEmbed()
                            .title("Max Input 1 Trade Set Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("maxInput2PerTrade").then(argument("maxInput2PerTrade", integer(1)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var maxInput2PerTrade = getInteger(c, "maxInput2PerTrade");
                        trade.maxInput2PerTrade = maxInput2PerTrade;
                        c.getSource().getEmbed()
                            .title("Max Input 2 Trade Set Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem1RestockStacks").then(argument("inputItem1RestockStacks", integer(1, 35)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.inputItem1RestockStacks = getInteger(c, "inputItem1RestockStacks");
                        c.getSource().getEmbed()
                            .title("Input Item 1 Restock Stacks Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem2RestockStacks").then(argument("inputItem2RestockStacks", integer(1, 35)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.inputItem2RestockStacks = getInteger(c, "inputItem2RestockStacks");
                        c.getSource().getEmbed()
                            .title("Input Item 2 Restock Stacks Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem1RestockCountThreshold").then(argument("inputItem1RestockCount", integer(1)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.inputItem1RestockCountThreshold = getInteger(c, "inputItem1RestockCount");
                        c.getSource().getEmbed()
                            .title("Input Item 1 Restock Count Threshold Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("inputItem2RestockCountThreshold").then(argument("inputItem2RestockCount", integer(1)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.inputItem2RestockCountThreshold = getInteger(c, "inputItem2RestockCount");
                        c.getSource().getEmbed()
                            .title("Input Item 2 Restock Count Threshold Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("outputItemStoreCountThreshold").then(argument("outputItemStoreCount", integer(1)).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.outputItemStoreCountThreshold = getInteger(c, "outputItemStoreCount");
                        c.getSource().getEmbed()
                            .title("Output Item Store Count Threshold Set")
                            .description(printTrade(id, trade));
                        return OK;
                    })))
                    .then(literal("outputEnchants")
                        .then(literal("add").then(argument("enchant", enchantment()).then(argument("level", integer(1)).executes(c -> {
                            var id = CustomStringArgumentType.getString(c, "id");
                            if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                                c.getSource().getEmbed()
                                    .title("Trade ID Not Found")
                                .addField("ID", id)
                                    .description(printAllTrades());
                                c.getSource().getData().put("list", true);
                                return ERROR;
                            }
                            var trade = PLUGIN_CONFIG.trades.get(id);
                            var enchant = getEnchantment(c, "enchant");
                            var level = getInteger(c, "level");
                            trade.outputItemEnchantments.put(enchant.name(), level);
                            c.getSource().getEmbed()
                                .title("Output Enchantment Added")
                                .description(printTradeEnchantments(trade));
                            c.getSource().getData().put("list", true);
                            return OK;
                        }))))
                        .then(literal("del").then(argument("enchant", enchantment()).executes(c -> {
                            var id = CustomStringArgumentType.getString(c, "id");
                            if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                                c.getSource().getEmbed()
                                    .title("Trade ID Not Found")
                                .addField("ID", id)
                                    .description(printAllTrades());
                                c.getSource().getData().put("list", true);
                                return ERROR;
                            }
                            var trade = PLUGIN_CONFIG.trades.get(id);
                            var enchant = getEnchantment(c, "enchant");
                            trade.outputItemEnchantments.remove(enchant.name());
                            c.getSource().getEmbed()
                                .title("Output Enchantment Removed")
                                .description(printTradeEnchantments(trade));
                            c.getSource().getData().put("list", true);
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            var id = CustomStringArgumentType.getString(c, "id");
                            if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                                c.getSource().getEmbed()
                                    .title("Trade ID Not Found")
                                .addField("ID", id)
                                    .description(printAllTrades());
                                c.getSource().getData().put("list", true);
                                return ERROR;
                            }
                            var trade = PLUGIN_CONFIG.trades.get(id);
                            trade.outputItemEnchantments.clear();
                            c.getSource().getEmbed()
                                .title("Enchantments Cleared")
                                .description(printTradeEnchantments(trade));
                            c.getSource().getData().put("list", true);
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            var id = CustomStringArgumentType.getString(c, "id");
                            if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                                c.getSource().getEmbed()
                                    .title("Trade ID Not Found")
                                .addField("ID", id);
                                return ERROR;
                            }
                            var trade = PLUGIN_CONFIG.trades.get(id);
                            c.getSource().getEmbed()
                                .title("Enchantment List")
                                .description(printTradeEnchantments(trade));
                            c.getSource().getData().put("list", true);
                            return OK;
                        })))
                    .then(literal("postTradeStore").then(argument("postTradeStoreMode", enumStrings(VillagerTraderConfig.Trade.PostTradeStoreMode.values())).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var mode = VillagerTraderConfig.Trade.PostTradeStoreMode.valueOf(getString(c, "postTradeStoreMode").toUpperCase());
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        trade.postTradeStoreMode = mode;
                        c.getSource().getEmbed()
                            .title("Post Trade Store Mode Set");
                        return OK;
                    })))
                    .then(literal("overflowChest").then(argument("overflowChestPos", blockPos()).executes(c -> {
                        var id = CustomStringArgumentType.getString(c, "id");
                        if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                            c.getSource().getEmbed()
                                .title("Trade ID Not Found")
                                .addField("ID", id)
                                .description(printAllTrades());
                            c.getSource().getData().put("list", true);
                            return ERROR;
                        }
                        var trade = PLUGIN_CONFIG.trades.get(id);
                        var pos = getBlockPos(c, "overflowChestPos");
                        trade.overflowChestPos = pos;
                        c.getSource().getEmbed()
                            .title("Overflow Chest Set");
                        return OK;
                    })))
                ))
            .then(literal("del").then(argument("id", wordWithChars()).executes(c -> {
                var id = CustomStringArgumentType.getString(c, "id");
                if (!PLUGIN_CONFIG.trades.containsKey(id)) {
                    c.getSource().getEmbed()
                        .title("Trade ID Not Found")
                        .addField("ID", id)
                        .description(printAllTrades());
                    c.getSource().getData().put("list", true);;
                    return ERROR;
                }
                inEventLoop(() -> {
                    PLUGIN_CONFIG.trades.remove(id);
                    MODULE.get(VillagerTrader.class).onTradeListChange();
                });
                c.getSource().getEmbed()
                    .title("Trade Removed")
                    .description(printAllTrades());
                c.getSource().getData().put("list", true);
                return OK;
            })))
            .then(literal("clear").executes(c -> {
                inEventLoop(() -> {
                    PLUGIN_CONFIG.trades.clear();
                    MODULE.get(VillagerTrader.class).onTradeListChange();
                });
                c.getSource().getEmbed()
                    .title("Trades Cleared");
            }))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed()
                    .title("Trade List")
                    .description(printAllTrades());
                c.getSource().getData().put("list", true);
            }))
            .then(literal("waitForInteractTimeout").then(argument("ticks", time()).executes(c -> {;
                PLUGIN_CONFIG.waitForInteractTimeoutTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Wait For Interact Timeout Set");
            })));
    }

    @Override
    public void defaultHandler(CommandContext ctx) {
        if (!ctx.getData().containsKey("list")) {
            ctx.getEmbed()
                .addField("Villager Trader", toggleStr(PLUGIN_CONFIG.enabled))
                .addField("Wait For Interact Timeout", PLUGIN_CONFIG.waitForInteractTimeoutTicks + " ticks");
        }
        ctx.getEmbed()
            .primaryColor();
    }

    void inEventLoop(Runnable task) {
        try {
            var client = Proxy.getInstance().getClient();
            if (client != null) {
                var eventLoop = client.getClientEventLoop();
                if (eventLoop.inEventLoop() || eventLoop.isShuttingDown()) {
                    task.run();
                    return;
                }
                eventLoop.submit(task).await();
            } else {
                task.run();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String printTrade(String id, VillagerTraderConfig.Trade trade) {
        StringBuilder sb = new StringBuilder();
        sb
            .append("`")
            .append(id)
            .append("`: [")
            .append(trade.villagerProfession.name().toLowerCase())
            .append("] ")
            .append("`").append(trade.inputItem1).append("`");
        if (trade.has2InputTrade()) {
            sb.append(" + ");
            sb.append("`").append(trade.inputItem2).append("`");
        }
        sb.append(" -> ");
        sb.append("`").append(trade.outputItem).append("`");
        return sb.toString();
    }

    public String printTradeEnchantments(VillagerTraderConfig.Trade trade) {
        StringBuilder sb = new StringBuilder();
        if (!trade.outputItemEnchantments.isEmpty()) {
            for (var entry : trade.outputItemEnchantments.object2IntEntrySet()) {
                sb.append(entry.getKey());
                sb.append(" -> ");
                sb.append(entry.getIntValue());
                sb.append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

    public String printAllTrades() {
        StringBuilder sb = new StringBuilder();
        var trades = PLUGIN_CONFIG.trades;
        for (var entry : trades.entrySet()) {
            var id = entry.getKey();
            var trade = entry.getValue();
            sb
                .append(printTrade(id, trade))
                .append("\n");
        }
        return sb.toString();
    }
}
