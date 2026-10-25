package com.desastres;

import com.desastres.entity.AlienEntity;
import com.desastres.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del bus del mod que necesitan cliente y servidor. */
@Mod.EventBusSubscriber(modid = DisastersMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CommonModEvents {

    private CommonModEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ALIEN.get(), AlienEntity.createAttributes().build());
    }
}
