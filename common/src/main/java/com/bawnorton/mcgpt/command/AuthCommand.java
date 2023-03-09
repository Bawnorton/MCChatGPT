package com.bawnorton.mcgpt.command;

import com.bawnorton.mcgpt.MCGPT;
import com.bawnorton.mcgpt.config.Config;
import com.bawnorton.mcgpt.config.ConfigManager;
import com.bawnorton.mcgpt.store.SecureTokenStorage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AuthCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("mcpgt-auth")
            .then(CommandManager.argument("token", StringArgumentType.string()).executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "token"))));
        dispatcher.register(builder);
    }

    private static int execute(ServerCommandSource source, String token) {
        if(token.length() != 51) {
            MCGPT.LOGGER.error("Invalid token length");
            source.sendFeedback(Text.translatable("mcchatgpt.auth.invalid.token"), false);
            return 0;
        }
        if(!token.startsWith("sk-")) {
            MCGPT.LOGGER.error("Invalid token prefix");
            source.sendFeedback(Text.translatable("mcchatgpt.auth.invalid.token"), false);
            return 0;
        }
        Config.getInstance().token = SecureTokenStorage.encrypt(token);
        ConfigManager.saveConfig();
        MCGPT.startService();
        source.sendFeedback(Text.translatable("mcchatgpt.auth.success"), false);
        return 1;
    }
}
