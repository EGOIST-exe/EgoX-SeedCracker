package de.egox.seedcracker;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
public final class CommandBuilders {
    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(name);
    }
    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument(name, type);
    }
}
