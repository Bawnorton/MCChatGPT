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
        int index = MCGPT.getConversationIndex();
        source.sendFeedback(Text.literal("§b[MCGPT]: §fContinuing conversation (" + (index + 1) + ")"), false);
        return 1;
    }
}
