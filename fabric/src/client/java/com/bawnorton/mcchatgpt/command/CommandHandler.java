package com.bawnorton.mcchatgpt.command;

import com.bawnorton.mcchatgpt.MCChatGPTClient;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theokanning.openai.completion.chat.ChatMessage;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.List;

public class CommandHandler {
    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            registerAskCommand(dispatcher);
            registerAuthCommand(dispatcher);
            registerListConversationsCommand(dispatcher);
            registerNextConversationCommand(dispatcher);
            registerPreviousConversationCommand(dispatcher);
            registerSetConversationCommand(dispatcher);
            registerSetContextLevelCommand(dispatcher);
            registerGetContextLevelCommand(dispatcher);
        });
    }

    private static void registerAskCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("ask")
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString()).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    String question = StringArgumentType.getString(context, "question");
                    source.sendFeedback(Text.literal("§7<" + source.getPlayer().getDisplayName().getString() + "> " + question));
                    MCChatGPTClient.ask(question);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerAuthCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("mcchatgpt-auth")
                .then(ClientCommandManager.argument("token", StringArgumentType.string()).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    String token = StringArgumentType.getString(context, "token");
                    if(token.length() != 51) {
                        MCChatGPTClient.LOGGER.error("Invalid token length");
                        source.sendFeedback(Text.translatable("mcchatgpt.auth.invalid.token"));
                        return 0;
                    }
                    if(!token.startsWith("sk-")) {
                        MCChatGPTClient.LOGGER.error("Invalid token prefix");
                        source.sendFeedback(Text.translatable("mcchatgpt.auth.invalid.token"));
                        return 0;
                    }
                    Config.getInstance().token = SecureTokenStorage.encrypt(token);
                    ConfigManager.saveConfig();
                    MCChatGPTClient.startService();
                    source.sendFeedback(Text.translatable("mcchatgpt.auth.success"));
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerListConversationsCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("listconversations").executes(context -> {
            FabricClientCommandSource source = context.getSource();
            List<List<ChatMessage>> conversations = MCChatGPTClient.getConversations();
            source.sendFeedback(Text.translatable("mcchatgpt.conversation.list"));
            for (int i = 0; i < conversations.size(); i++) {
                List<ChatMessage> conversation = conversations.get(i);
                if(conversation.size() < 2) continue;
                String lastQuestion = conversation.get(conversation.size() - 2).getContent();
                source.sendFeedback(Text.of("§b[MCChatGPT]: §r" + (i + 1) + ": " + lastQuestion));
            }
            return 1;
        });
        dispatcher.register(builder);
    }

    private static void registerNextConversationCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("nextconversation").executes(context -> {
            try {
                FabricClientCommandSource source = context.getSource();
                boolean newConversation = MCChatGPTClient.nextConversation();
                int index = MCChatGPTClient.getConversationIndex();
                if(newConversation) {
                    source.sendFeedback(Text.translatable("mcchatgpt.conversation.new", index + 1));
                } else {
                    source.sendFeedback(Text.translatable("mcchatgpt.conversation.continue", index + 1));
                }
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
        dispatcher.register(builder);
    }

    private static void registerPreviousConversationCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("previousconversation").executes(context -> {
            try {
                FabricClientCommandSource source = context.getSource();
                MCChatGPTClient.previousConversation();
                int index = MCChatGPTClient.getConversationIndex();
                source.sendFeedback(Text.translatable("mcchatgpt.conversation.continue", index + 1));
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
        dispatcher.register(builder);
    }

    private static void registerSetConversationCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("setconversation")
                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1)).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    int index = IntegerArgumentType.getInteger(context, "index") - 1;
                    if(index >= MCChatGPTClient.getConversations().size()) {
                        source.sendFeedback(Text.translatable("mcchatgpt.conversation.invalid"));
                        return 0;
                    }
                    MCChatGPTClient.setConversationIndex(index);
                    source.sendFeedback(Text.translatable("mcchatgpt.conversation.continue", index + 1));
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerSetContextLevelCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("setcontextlevel")
                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 3)).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    int level = IntegerArgumentType.getInteger(context, "level");
                    MCChatGPTClient.setContextLevel(level);
                    source.sendFeedback(Text.translatable("mcchatgpt.context.level.set", level, Text.translatable("mcchatgpt.context.level." + level).getString()));
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerGetContextLevelCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommandManager.literal("getcontextlevel").executes(context -> {
            FabricClientCommandSource source = context.getSource();
            int level = MCChatGPTClient.getContextLevel();
            source.sendFeedback(Text.translatable("mcchatgpt.context.level.get", level, Text.translatable("mcchatgpt.context.level." + level).getString()));
            return 1;
        });
        dispatcher.register(builder);
    }
}
