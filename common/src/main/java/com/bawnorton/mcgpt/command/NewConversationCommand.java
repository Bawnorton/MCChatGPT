package com.bawnorton.mcgpt.command;

import com.bawnorton.mcgpt.MCGPT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class NewConversationCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("newconversation").executes(context -> execute(context.getSource()));
        dispatcher.register(builder);
    }

    private static int execute(ServerCommandSource source) {
        MCGPT.nextConversation();
        source.sendFeedback(Text.literal("Started a new conversation"), false);
        return 1;
    }
}
