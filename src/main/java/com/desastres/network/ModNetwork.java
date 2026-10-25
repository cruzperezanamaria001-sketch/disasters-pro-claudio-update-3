package com.desastres.network;

import com.desastres.DisastersMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DisastersMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, SpawnDisasterPacket.class,
                SpawnDisasterPacket::encode,
                SpawnDisasterPacket::decode,
                SpawnDisasterPacket::handle);
    }

    private ModNetwork() {}
}
