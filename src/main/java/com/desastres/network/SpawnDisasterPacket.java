package com.desastres.network;

import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.DisasterType;
import com.desastres.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor. type = indice del desastre, -1 = detener todos, -2 = desastre aleatorio. */
public class SpawnDisasterPacket {
    public final int type;
    public final int intensity;
    public final int target;

    public SpawnDisasterPacket(int type, int intensity, int target) {
        this.type = type;
        this.intensity = intensity;
        this.target = target;
    }

    public static void encode(SpawnDisasterPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.type);
        buf.writeByte(msg.intensity);
        buf.writeByte(msg.target);
    }

    public static SpawnDisasterPacket decode(FriendlyByteBuf buf) {
        return new SpawnDisasterPacket(buf.readByte(), buf.readByte(), buf.readByte());
    }

    public static void handle(SpawnDisasterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) {
                return;
            }
            if (!DisasterManager.canUse(player)) {
                player.displayClientMessage(Component.translatable("message.disasters.no_permission"), true);
                return;
            }
            boolean holding = player.getMainHandItem().is(ModItems.CONTROLLER.get())
                    || player.getOffhandItem().is(ModItems.CONTROLLER.get());
            if (!holding) {
                return;
            }
            if (msg.type < 0) {
                int n = DisasterManager.stopAll();
                player.displayClientMessage(Component.translatable("message.disasters.stopped", n), true);
                return;
            }
            DisasterType type;
            if (msg.type == -2) {
                DisasterType[] all = DisasterType.values();
                type = all[player.getRandom().nextInt(all.length)];
            } else {
                type = DisasterType.byIndex(msg.type);
            }
            if (type == null) {
                return;
            }
            int intensity = Mth.clamp(msg.intensity, 1, 3);
            DisasterManager.spawn(player, type, intensity, msg.target);
        });
        c.setPacketHandled(true);
    }
}
