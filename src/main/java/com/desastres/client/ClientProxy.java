package com.desastres.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientProxy {
    public static void openControlScreen() {
        Minecraft.getInstance().setScreen(new DisasterControlScreen());
    }

    private ClientProxy() {}
}
