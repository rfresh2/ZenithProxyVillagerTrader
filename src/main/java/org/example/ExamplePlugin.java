package org.example;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.example.command.ExampleCommand;
import org.example.command.ExampleESPCommand;
import org.example.module.ExampleESPModule;
import org.example.module.ExampleModule;

@Plugin(
    id = "example-plugin",
    version = BuildConstants.VERSION,
    description = "ZenithProxy Example Plugin",
    url = "https://github.com/rfresh2/ZenithProxyExamplePlugin",
    authors = {"rfresh2"},
    mcVersions = {"1.21.0"} // to indicate any MC version: @Plugin(mcVersions = "*")
                            // if you touch packet classes, you almost certainly need to pin to a single mc version
)
public class ExamplePlugin implements ZenithProxyPlugin {
    // public static for simple access from modules and commands
    // or alternatively, you could pass these around in constructors
    public static ExampleConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Example Plugin loading...");
        // initialize any configurations before modules or commands might need to read them
        PLUGIN_CONFIG = pluginAPI.registerConfig("example-plugin", ExampleConfig.class);
        pluginAPI.registerModule(new ExampleModule());
        pluginAPI.registerModule(new ExampleESPModule());
        pluginAPI.registerCommand(new ExampleCommand());
        pluginAPI.registerCommand(new ExampleESPCommand());
        LOG.info("Example Plugin loaded!");
    }
}
