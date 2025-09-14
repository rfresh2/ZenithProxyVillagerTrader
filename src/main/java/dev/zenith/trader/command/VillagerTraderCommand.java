package dev.zenith.trader.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import dev.zenith.trader.VillagerTraderConfig;
import dev.zenith.trader.module.VillagerTrader;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.RegistryDataArgument.enchantment;
import static com.zenith.command.brigadier.RegistryDataArgument.getEnchantment;
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
              Automatically restocks, trades with villagers, and stores the bought items
              
              `villagerTradeRestockWait` -> seconds it waits after all villagers are out of stock. 1200 = 1 minecraft day
              `waitForInteractTimeout` -> timeout for server interactions like opening villager trade window
              """)
            .usageLines(
                "on/off",
                "add",
                "add <profession> <inputItem1> <inputItem2> <outputItem> <inputItem1ChestPos> <inputItem2ChestPos> <outputChestPos>",
                "add <profession> <inputItem1> <outputItem> <inputItem1ChestPos> <outputChestPos>",
                "del <index>",
                "clear",
                "list",
                "set <index> profession <profession>",
                "set <index> inputItem1 <item>",
                "set <index> inputItem2 <item>",
                "set <index> outputItem <item>",
                "set <index> inputItem1Chest <x> <y> <z>",
                "set <index> inputItem2Chest <x> <y> <z>",
                "set <index> outputChest <x> <y> <z>",
                "set <index> maxInput1PerTrade <count>",
                "set <index> maxInput2PerTrade <count>",
                "set <index> outputEnchants add <enchantment> <level>",
                "set <index> outputEnchants del <enchantment>",
                "set <index> outputEnchants clear",
                "set <index> outputEnchants list",
                "set <index> postTradeStore <none/to_restock/to_overflow>",
                "set <index> overflowChest <x> <y> <z>",
                "villagerTradeRestockWait <seconds>",
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
            .then(literal("add")
                  .then(argument("profession", enumStrings(VillagerProfession.values())).then(argument("inputItem1", item()).then(argument("buyItem", item()).then(argument("inputItem1Pos", blockPos()).then(argument("storeChestPos", blockPos()).executes(c -> {
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
                      PLUGIN_CONFIG.trades.add(trade);
                      var index = PLUGIN_CONFIG.trades.indexOf(trade);
                      c.getSource().getEmbed()
                          .title("Trade Added")
                          .addField("Index", index)
                          .description(printTrade(trade));
                  }))))))
                  .then(argument("profession", enumStrings(VillagerProfession.values())).then(argument("inputItem1", item()).then(argument("inputItem2", item()).then(argument("buyItem", item()).then(argument("inputItem1Pos", blockPos()).then(argument("inputItem2Pos", blockPos()).then(argument("storeChestPos", blockPos()).executes(c -> {
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
                      PLUGIN_CONFIG.trades.add(trade);
                      var index = PLUGIN_CONFIG.trades.indexOf(trade);
                      c.getSource().getEmbed()
                          .title("Trade Added")
                          .addField("Index", index)
                          .description(printTrade(trade));
                  })))))))))
            .then(literal("set").then(argument("index", integer(0))
                  .then(literal("inputItem1").then(argument("inputItem1", item()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var inputItem1 = getItem(c, "inputItem1");
                      trade.inputItem1 = inputItem1.name();
                      c.getSource().getEmbed()
                          .title("Input Item 1 Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("profession").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      trade.villagerProfession = VillagerProfession.valueOf(getString(c, "profession").toUpperCase());
                      c.getSource().getEmbed()
                          .title("Profession Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("inputItem2").then(argument("inputItem2", item()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var inputItem2 = getItem(c, "inputItem2");
                      trade.inputItem2 = inputItem2.name();
                      c.getSource().getEmbed()
                          .title("Input Item 2 Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("outputItem").then(argument("outputItem", item()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var outputItem = getItem(c, "outputItem");
                      trade.outputItem = outputItem.name();
                      c.getSource().getEmbed()
                          .title("Output Item Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("inputItem1Chest").then(argument("inputItem1Chest", blockPos()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var inputItem1Chest = getBlockPos(c, "inputItem1Chest");
                      trade.inputItem1Chest = inputItem1Chest;
                      c.getSource().getEmbed()
                          .title("Input Item 1 Chest Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("inputItem2Chest").then(argument("inputItem2Chest", blockPos()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var inputItem2Chest = getBlockPos(c, "inputItem2Chest");
                      trade.inputItem2Chest = inputItem2Chest;
                      c.getSource().getEmbed()
                          .title("Input Item 2 Chest Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("outputChest").then(argument("outputChest", blockPos()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var outputChest = getBlockPos(c, "outputChest");
                      trade.outputChest = outputChest;
                      c.getSource().getEmbed()
                          .title("Output Chest Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("maxInput1PerTrade").then(argument("maxInput1PerTrade", integer(1)).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var maxInput1PerTrade = getInteger(c, "maxInput1PerTrade");
                      trade.maxInput1PerTrade = maxInput1PerTrade;
                      c.getSource().getEmbed()
                          .title("Max Input 1 Trade Set Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("maxInput2PerTrade").then(argument("maxInput2PerTrade", integer(1)).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var maxInput2PerTrade = getInteger(c, "maxInput2PerTrade");
                      trade.maxInput2PerTrade = maxInput2PerTrade;
                      c.getSource().getEmbed()
                          .title("Max Input 2 Trade Set Set")
                          .description(printTrade(trade));
                      return OK;
                  })))
                  .then(literal("outputEnchants")
                            .then(literal("add").then(argument("enchant", enchantment()).then(argument("level", integer(1)).executes(c -> {
                                var index = getInteger(c, "index");
                                if (index >= PLUGIN_CONFIG.trades.size()) {
                                    c.getSource().getEmbed()
                                        .title("Trade Index Not Found")
                                        .description(printAllTrades());
                                    c.getSource().getData().put("list", true);
                                    return ERROR;
                                }
                                var trade = PLUGIN_CONFIG.trades.get(index);
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
                                var index = getInteger(c, "index");
                                if (index >= PLUGIN_CONFIG.trades.size()) {
                                    c.getSource().getEmbed()
                                        .title("Trade Index Not Found")
                                        .description(printAllTrades());
                                    c.getSource().getData().put("list", true);
                                    return ERROR;
                                }
                                var trade = PLUGIN_CONFIG.trades.get(index);
                                var enchant = getEnchantment(c, "enchant");
                                trade.outputItemEnchantments.remove(enchant.name());
                                c.getSource().getEmbed()
                                    .title("Output Enchantment Removed")
                                    .description(printTradeEnchantments(trade));
                                c.getSource().getData().put("list", true);
                                return OK;
                            })))
                            .then(literal("clear").executes(c -> {
                                var index = getInteger(c, "index");
                                if (index >= PLUGIN_CONFIG.trades.size()) {
                                    c.getSource().getEmbed()
                                        .title("Trade Index Not Found")
                                        .description(printAllTrades());
                                    c.getSource().getData().put("list", true);
                                    return ERROR;
                                }
                                var trade = PLUGIN_CONFIG.trades.get(index);
                                trade.outputItemEnchantments.clear();
                                c.getSource().getEmbed()
                                    .title("Enchantments Cleared")
                                    .description(printTradeEnchantments(trade));
                                c.getSource().getData().put("list", true);
                                return OK;
                            }))
                            .then(literal("list").executes(c -> {
                                var index = getInteger(c, "index");
                                if (index >= PLUGIN_CONFIG.trades.size()) {
                                    c.getSource().getEmbed()
                                        .title("Trade Index Not Found");
                                    return ERROR;
                                }
                                var trade = PLUGIN_CONFIG.trades.get(index);
                                c.getSource().getEmbed()
                                    .title("Enchantment List")
                                    .description(printTradeEnchantments(trade));
                                c.getSource().getData().put("list", true);
                                return OK;
                            })))
                  .then(literal("postTradeStore").then(argument("postTradeStoreMode", enumStrings(VillagerTraderConfig.Trade.PostTradeStoreMode.values())).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var mode = VillagerTraderConfig.Trade.PostTradeStoreMode.valueOf(getString(c, "postTradeStoreMode").toUpperCase());
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      trade.postTradeStoreMode = mode;
                      c.getSource().getEmbed()
                          .title("Post Trade Store Mode Set");
                      return OK;
                  })))
                  .then(literal("overflowChest").then(argument("overflowChestPos", blockPos()).executes(c -> {
                      var index = getInteger(c, "index");
                      if (index >= PLUGIN_CONFIG.trades.size()) {
                          c.getSource().getEmbed()
                              .title("Trade Index Not Found")
                              .description(printAllTrades());
                          c.getSource().getData().put("list", true);
                          return ERROR;
                      }
                      var trade = PLUGIN_CONFIG.trades.get(index);
                      var pos = getBlockPos(c, "overflowChestPos");
                      trade.overflowChestPos = pos;
                      c.getSource().getEmbed()
                          .title("Overflow Chest Set");
                      return OK;
                  })))
            ))
            .then(literal("del").then(argument("index", integer(0)).executes(c -> {
                var index = getInteger(c, "index");
                if (index >= PLUGIN_CONFIG.trades.size()) {
                    c.getSource().getEmbed()
                        .title("Trade Index Not Found")
                        .description(printAllTrades());
                    c.getSource().getData().put("list", true);;
                    return ERROR;
                }
                PLUGIN_CONFIG.trades.remove(index);
                c.getSource().getEmbed()
                    .title("Trade Removed")
                    .description(printAllTrades());
                c.getSource().getData().put("list", true);;
                return OK;
            })))
            .then(literal("clear").executes(c -> {
                PLUGIN_CONFIG.trades.clear();
                c.getSource().getEmbed()
                    .title("Trades Cleared");
            }))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed()
                    .title("Trade List")
                    .description(printAllTrades());
                c.getSource().getData().put("list", true);
            }))
            .then(literal("villagerTradeRestockWait").then(argument("seconds", integer(1, (int) TimeUnit.MINUTES.toSeconds(30))).executes(c -> {
                PLUGIN_CONFIG.villagerTradeRestockWaitSeconds = getInteger(c, "seconds");
                c.getSource().getEmbed()
                    .title("Villager Trade Restock Wait Set");
            })))
            .then(literal("waitForInteractTimeout").then(argument("ticks", integer(1, 1000)).executes(c -> {;
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
                .addField("Villager Trade Restock Wait", PLUGIN_CONFIG.villagerTradeRestockWaitSeconds + "s")
                .addField("Wait For Interact Timeout", PLUGIN_CONFIG.waitForInteractTimeoutTicks + " ticks");
        }
        ctx.getEmbed()
            .primaryColor();
    }

    public String printTrade(VillagerTraderConfig.Trade trade) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(trade.villagerProfession.name().toLowerCase());
        sb.append("] ");
        sb.append("`").append(trade.inputItem1).append("`");
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
        ArrayList<VillagerTraderConfig.Trade> trades = PLUGIN_CONFIG.trades;
        for (int i = 0; i < trades.size(); i++) {
            final var trade = trades.get(i);
            sb.append(i);
            sb.append(": ");
            sb.append(printTrade(trade));
            sb.append("\n");
        }
        return sb.toString();
    }
}
