package com.bawnorton.mcgpt.command;

import com.bawnorton.mcgpt.MCGPT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AskCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("ask")
                .then(CommandManager.argument("question", StringArgumentType.greedyString()).executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "question"))));
        dispatcher.register(builder);
    }

    private static int execute(ServerCommandSource source, String question) {
        source.sendFeedback(Text.literal("§7<" + source.getPlayer().getDisplayName().getString() + "> " + question), false);
        MCGPT.ask(question);
        return 1;
    }
}
