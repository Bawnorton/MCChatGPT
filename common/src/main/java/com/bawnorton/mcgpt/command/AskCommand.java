package com.bawnorton.mcgpt.command;

import com.bawnorton.mcgpt.MCGPT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class AskCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("ask")
                .then(CommandManager.argument("question", StringArgumentType.greedyString()).executes(context -> execute(StringArgumentType.getString(context, "question"))));
        dispatcher.register(builder);
    }

    private static int execute(String question) {
        MCGPT.ask(question);
        return 1;
    }
}
