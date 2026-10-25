package com.desastres.disaster;

import com.desastres.DisastersMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /disaster spawn <tipo> [intensidad 1-3]   |   /disaster stop */
@Mod.EventBusSubscriber(modid = DisastersMod.MODID)
public final class DisasterCommands {

    private DisasterCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("disaster")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("stop").executes(ctx -> {
                            int n = DisasterManager.stopAll();
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.disasters.stopped", n), true);
                            return n;
                        }))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DisasterType.ids(), builder))
                                        .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "type"), 2))
                                        .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> spawn(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "intensity")))))));
    }

    private static int spawn(CommandSourceStack src, String id, int intensity) throws CommandSyntaxException {
        DisasterType type = DisasterType.byId(id);
        if (type == null) {
            src.sendFailure(Component.translatable("message.disasters.unknown_type", id));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();
        return DisasterManager.spawn(player, type, intensity, 0) ? 1 : 0;
    }
}
