package com.bawnorton.mcgpt.command;

import com.bawnorton.mcgpt.MCGPT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class PreviousConversationCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("previousconversation").executes(context -> execute(context.getSource()));
        dispatcher.register(builder);
    }

    private static int execute(ServerCommandSource source) {
        MCGPT.previousConversation();
        source.sendFeedback(Text.literal("Current conversation is now the previous one"), false);
        return 1;
    }
}
