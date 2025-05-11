package org.example.module;

import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.network.server.ServerSession;
import org.example.ExamplePlugin;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.ArrayList;

public class ExampleESPModule extends Module {
    @Override
    public boolean enabledSetting() {
        return ExamplePlugin.PLUGIN_CONFIG.esp;
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("esp")
            .setPriority(1000)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                // packet classes can and will change between MC versions
                // if you want to have packet handlers you probably need separate plugin builds for each MC version
                .inbound(ClientboundSetEntityDataPacket.class, new GlowingEntityMetadataPacketHandler())
                // or with in-line lambda:
//                .outbound(ClientboundSetEntityDataPacket.class, (packet, session) -> {
//                    ...more impl...
//                    return packet;
//                })
                // beware there are different PacketHandler interfaces that would be trickier to declare as a lambda
                // i.e. to handle client packets on the tick loop you need to implement ClientEventLoopPacketHandler
                .build())
            .build();
    }


    // this can also be moved to a separate class file
    public static class GlowingEntityMetadataPacketHandler implements PacketHandler<ClientboundSetEntityDataPacket, ServerSession> {
        @Override
        public ClientboundSetEntityDataPacket apply(final ClientboundSetEntityDataPacket packet, final ServerSession session) {
            var metadata = packet.getMetadata();
            for (int i = 0; i < metadata.size(); i++) {
                final EntityMetadata<?, ?> entityMetadata = metadata.get(i);
                // https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Entity_metadata#Entity
                if (entityMetadata.getId() == 0 && entityMetadata instanceof ByteEntityMetadata byteMetadata) {
                    // found the metadata id we want to edit
                    var newMetadata = new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) (byteMetadata.getPrimitiveValue() | 0x40));
                    // copy to avoid mutating potentially cached data
                    var newMetadataList = new ArrayList<>(metadata);
                    newMetadataList.set(i, newMetadata);
                    return packet.withMetadata(newMetadataList);
                }
            }
            // metadata id wasn't present, so we need to add it
            var newMetadata = new ArrayList<>(packet.getMetadata());
            newMetadata.addFirst(new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x40));
            return packet.withMetadata(newMetadata);
        }
    }
}
