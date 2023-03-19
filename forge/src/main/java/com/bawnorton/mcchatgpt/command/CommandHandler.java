package com.bawnorton.mcchatgpt.command;

import com.bawnorton.mcchatgpt.MCChatGPT;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theokanning.openai.completion.chat.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ClientCommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CommandHandler {

    public static void registerCommands(RegisterClientCommandsEvent event) {
        MCChatGPT.LOGGER.info("Registering commands");
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerAskCommand(dispatcher);
        registerAuthCommand(dispatcher);
        registerListConversationsCommand(dispatcher);
        registerNextConversationCommand(dispatcher);
        registerPreviousConversationCommand(dispatcher);
        registerSetConversationCommand(dispatcher);
    }

    private static void registerAskCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("ask")
                .then(Commands.argument("question", StringArgumentType.greedyString()).executes(context -> {
                    ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                    String question = StringArgumentType.getString(context, "question");
                    source.sendSuccess(Component.literal("§7<" + Minecraft.getInstance().player.getDisplayName().getString() + "> " + question), false);
                    MCChatGPT.ask(question);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerAuthCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("mcchatgpt-auth")
                .then(Commands.argument("token", StringArgumentType.string()).executes(context -> {
                    ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                    String token = StringArgumentType.getString(context, "token");
                    if(token.length() != 51) {
                        MCChatGPT.LOGGER.error("Invalid token length");
                        source.sendSuccess(Component.translatable("mcchatgpt.auth.invalid.token"), false);
                        return 0;
                    }
                    if(!token.startsWith("sk-")) {
                        MCChatGPT.LOGGER.error("Invalid token prefix");
                        source.sendSuccess(Component.translatable("mcchatgpt.auth.invalid.token"), false);
                        return 0;
                    }
                    Config.getInstance().token = SecureTokenStorage.encrypt(token);
                    ConfigManager.saveConfig();
                    MCChatGPT.startService();
                    source.sendSuccess(Component.translatable("mcchatgpt.auth.success"), false);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerListConversationsCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("listconversations").executes(context -> {
            ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
            List<List<ChatMessage>> conversations = MCChatGPT.getConversations();
            source.sendSuccess(Component.translatable("mcchatgpt.conversation.list"), false);
            for (int i = 0; i < conversations.size(); i++) {
                List<ChatMessage> conversation = conversations.get(i);
                if(conversation.size() < 2) continue;
                String lastQuestion = conversation.get(conversation.size() - 2).getContent();
                source.sendSuccess(Component.literal("§b[MCChatGPT]: §r" + (i + 1) + ": " + lastQuestion), false);
            }
            return 1;
        });
        dispatcher.register(builder);
    }

    private static void registerNextConversationCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("nextconversation").executes(context -> {
            try {
                ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                boolean newConversation = MCChatGPT.nextConversation();
                int index = MCChatGPT.getConversationIndex();
                if(newConversation) {
                    source.sendSuccess(Component.translatable("mcchatgpt.conversation.new", index + 1), false);
                } else {
                    source.sendSuccess(Component.translatable("mcchatgpt.conversation.continue", index + 1), false);
                }
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
        dispatcher.register(builder);
    }

    private static void registerPreviousConversationCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("previousconversation").executes(context -> {
            try {
                ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                MCChatGPT.previousConversation();
                int index = MCChatGPT.getConversationIndex();
                source.sendSuccess(Component.translatable("mcchatgpt.conversation.continue", index + 1), false);
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
        dispatcher.register(builder);
    }

    private static void registerSetConversationCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("setconversation")
                .then(Commands.argument("index", IntegerArgumentType.integer()).executes(context -> {
                    ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                    int index = IntegerArgumentType.getInteger(context, "index") - 1;
                    if (index >= MCChatGPT.getConversations().size()) {
                        source.sendSuccess(Component.translatable("mcchatgpt.conversation.invalid"), false);
                        return 0;
                    }
                    MCChatGPT.setConversationIndex(index);
                    source.sendSuccess(Component.translatable("mcchatgpt.conversation.continue", index + 1), false);
                    return 1;
                }));
        dispatcher.register(builder);
    }
}
