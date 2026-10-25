package com.desastres.registry;

import com.desastres.DisastersMod;
import com.desastres.item.DisasterControllerItem;
import com.desastres.item.LaserPistolItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DisastersMod.MODID);

    public static final RegistryObject<Item> CONTROLLER = ITEMS.register("disaster_controller",
            () -> new DisasterControllerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Pistola laser: la durabilidad es la energia (120 disparos). */
    public static final RegistryObject<Item> LASER_PISTOL = ITEMS.register("laser_pistol",
            () -> new LaserPistolItem(new Item.Properties().durability(120).rarity(Rarity.RARE)));

    /** Celda de energia: recarga la pistola laser. Tambien la sueltan los alienigenas. */
    public static final RegistryObject<Item> ENERGY_CELL = ITEMS.register("energy_cell",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    private ModItems() {}
}
