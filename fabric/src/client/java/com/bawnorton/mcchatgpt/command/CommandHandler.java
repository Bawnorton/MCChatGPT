package com.bawnorton.mcchatgpt.command;

import com.bawnorton.mcchatgpt.MCChatGPTClient;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.bawnorton.mcchatgpt.util.Conversation;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.util.List;

public class CommandHandler {
    public static void registerCommands() {
        ClientCommandManager.DISPATCHER.register(askCommand());
        ClientCommandManager.DISPATCHER.register(authCommand());
        ClientCommandManager.DISPATCHER.register(listConversationsCommand());
        ClientCommandManager.DISPATCHER.register(nextConversationCommand());
        ClientCommandManager.DISPATCHER.register(previousConversationCommand());
        ClientCommandManager.DISPATCHER.register(setConversationCommand());
        ClientCommandManager.DISPATCHER.register(setContextLevelCommand());
        ClientCommandManager.DISPATCHER.register(getContextLevelCommand());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> askCommand() {
        return ClientCommandManager.literal("ask")
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString()).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    String question = StringArgumentType.getString(context, "question");
                    source.sendFeedback(new LiteralText("§7<" + source.getPlayer().getDisplayName().getString() + "> " + question));
                    MCChatGPTClient.ask(question);
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> authCommand() {
        return ClientCommandManager.literal("mcchatgpt-auth")
                .then(ClientCommandManager.argument("token", StringArgumentType.string()).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    String token = StringArgumentType.getString(context, "token");
                    if(token.length() != 51) {
                        MCChatGPTClient.LOGGER.error("Invalid token length");
                        source.sendFeedback(new TranslatableText("mcchatgpt.auth.invalid.token"));
                        return 0;
                    }
                    if(!token.startsWith("sk-")) {
                        MCChatGPTClient.LOGGER.error("Invalid token prefix");
                        source.sendFeedback(new TranslatableText("mcchatgpt.auth.invalid.token"));
                        return 0;
                    }
                    Config.getInstance().token = SecureTokenStorage.encrypt(token);
                    ConfigManager.saveConfig();
                    MCChatGPTClient.startService();
                    source.sendFeedback(new TranslatableText("mcchatgpt.auth.success"));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listConversationsCommand() {
        return ClientCommandManager.literal("listconversations").executes(context -> {
            FabricClientCommandSource source = context.getSource();
            List<Conversation> conversations = MCChatGPTClient.getConversations();
            source.sendFeedback(new TranslatableText("mcchatgpt.conversation.list"));
            for (int i = 0; i < conversations.size(); i++) {
                Conversation conversation = conversations.get(i);
                if(conversation.messageCount() < 2) continue;
                String lastQuestion = conversation.getPreviewMessage().getContent();
                source.sendFeedback(Text.of("§b[MCChatGPT]: §r" + (i + 1) + ": " + lastQuestion));
            }
            return 1;
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> nextConversationCommand() {
        return ClientCommandManager.literal("nextconversation").executes(context -> {
            try {
                FabricClientCommandSource source = context.getSource();
                boolean newConversation = MCChatGPTClient.nextConversation();
                int index = MCChatGPTClient.getConversationIndex();
                if(newConversation) {
                    source.sendFeedback(new TranslatableText("mcchatgpt.conversation.new", index + 1));
                } else {
                    source.sendFeedback(new TranslatableText("mcchatgpt.conversation.continue", index + 1));
                }
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> previousConversationCommand() {
        return ClientCommandManager.literal("previousconversation").executes(context -> {
            try {
                FabricClientCommandSource source = context.getSource();
                MCChatGPTClient.previousConversation();
                int index = MCChatGPTClient.getConversationIndex();
                source.sendFeedback(new TranslatableText("mcchatgpt.conversation.continue", index + 1));
                return 1;
            } catch (IllegalStateException e) {
                return 0;
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> setConversationCommand() {
        return ClientCommandManager.literal("setconversation")
                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1)).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    int index = IntegerArgumentType.getInteger(context, "index") - 1;
                    if(index >= MCChatGPTClient.getConversations().size()) {
                        source.sendFeedback(new TranslatableText("mcchatgpt.conversation.invalid"));
                        return 0;
                    }
                    MCChatGPTClient.setConversationIndex(index);
                    source.sendFeedback(new TranslatableText("mcchatgpt.conversation.continue", index + 1));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> setContextLevelCommand() {
        return ClientCommandManager.literal("setcontextlevel")
                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 3)).executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    int level = IntegerArgumentType.getInteger(context, "level");
                    Config.getInstance().contextLevel = level;
                    ConfigManager.saveConfig();
                    source.sendFeedback(new TranslatableText("mcchatgpt.context.level.set", level, new TranslatableText("mcchatgpt.context.level." + level).getString()));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> getContextLevelCommand() {
        return ClientCommandManager.literal("getcontextlevel").executes(context -> {
            FabricClientCommandSource source = context.getSource();
            int level = Config.getInstance().contextLevel;
            source.sendFeedback(new TranslatableText("mcchatgpt.context.level.get", level, new TranslatableText("mcchatgpt.context.level." + level).getString()));
            return 1;
        });
    }
}
