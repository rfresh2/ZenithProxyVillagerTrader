package org.example.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import org.example.module.ExampleModule;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static org.example.ExamplePlugin.PLUGIN_CONFIG;

public class ExampleCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("examplePlugin")
            .category(CommandCategory.MODULE)
            .description("""
            Example plugin command
            """)
            .usageLines(
                "on/off",
                "delay <ticks>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("examplePlugin")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.exampleModule.enabled = getToggle(c, "toggle");
                // make sure to sync so the module is actually toggled
                MODULE.get(ExampleModule.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    // if no title is set, no embed response will be sent
                    // other properties like fields can be left unset without issues
                    .title("Example Plugin " + toggleStrCaps(PLUGIN_CONFIG.exampleModule.enabled));
            }))
            .then(literal("delay").then(argument("ticks", integer(0)).executes(c -> {
                PLUGIN_CONFIG.exampleModule.delayTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Delay Set");
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.exampleModule.enabled))
            .addField("Delay", PLUGIN_CONFIG.exampleModule.delayTicks + " ticks");
    }
}
