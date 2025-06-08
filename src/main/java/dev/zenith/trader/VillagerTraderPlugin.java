package dev.zenith.trader;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import dev.zenith.trader.command.VillagerTraderCommand;
import dev.zenith.trader.module.VillagerTrader;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = "villager-trader",
    version = BuildConstants.VERSION,
    description = "ZenithProxy Villager Trader",
    url = "https://github.com/rfresh2/ZenithProxyVillagerTrader",
    authors = {"rfresh2"},
    mcVersions = {"1.21.0", "1.21.4", "1.21.5"}
)
public class VillagerTraderPlugin implements ZenithProxyPlugin {
    public static ComponentLogger LOG;
    public static VillagerTraderConfig PLUGIN_CONFIG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        PLUGIN_CONFIG = pluginAPI.registerConfig("villager-trader", VillagerTraderConfig.class);
        pluginAPI.registerModule(new VillagerTrader());
        pluginAPI.registerCommand(new VillagerTraderCommand());
    }
}
