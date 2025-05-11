package org.example.command;

import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import org.example.ExamplePlugin;
import org.example.module.ExampleESPModule;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ExampleESPCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("esp")
            .category(CommandCategory.MODULE)
            .description("Renders the spectral effect around all entities")
            .usageLines(
                "on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("esp")
            .then(argument("toggle", toggle()).executes(c -> {
                ExamplePlugin.PLUGIN_CONFIG.esp = getToggle(c, "toggle");
                // make sure to sync so the module is actually toggled
                MODULE.get(ExampleESPModule.class).syncEnabledFromConfig();
                // array of sessions with the controlling player and any spectators
                var sessions = Proxy.getInstance().getActiveConnections().getArray();
                if (sessions.length > 0) {
                    // resend entity metadata for every cached entity
                    // if the module is now enabled, our outbound packet handler will modify the packets and add the metadata value
                    // otherwise, this is resyncing the original metadata to players (i.e. removing the effect)
                    CACHE.getEntityCache().getEntities().values().forEach(e -> {
                        EntityMetadata<?, ?> toSend;
                        toSend = e.getMetadata().get(0);
                        if (toSend == null)
                            toSend = new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0);
                        for (int i = 0; i < sessions.length; i++) {
                            sessions[i].sendAsync(new ClientboundSetEntityDataPacket(e.getEntityId(), Lists.newArrayList(toSend)));
                        }
                    });
                }
                c.getSource().getEmbed()
                    .title("ESP " + toggleStrCaps(ExamplePlugin.PLUGIN_CONFIG.esp))
                    .primaryColor();
            }));
    }
}
