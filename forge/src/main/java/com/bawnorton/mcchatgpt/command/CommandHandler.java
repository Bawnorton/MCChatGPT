package com.bawnorton.mcchatgpt.command;

import com.bawnorton.mcchatgpt.MCChatGPT;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.bawnorton.mcchatgpt.util.Conversation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
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
        registerSetContextLevelCommand(dispatcher);
        registerGetContextLevelCommand(dispatcher);
    }

    private static void registerAskCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("ask")
                .then(Commands.argument("question", StringArgumentType.greedyString()).executes(context -> {
                    ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                    String question = StringArgumentType.getString(context, "question");
                    source.sendSuccess(new TextComponent("§7<" + Minecraft.getInstance().player.getDisplayName().getString() + "> " + question), false);
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
                        source.sendSuccess(new TranslatableComponent("mcchatgpt.auth.invalid.token"), false);
                        return 0;
                    }
                    if(!token.startsWith("sk-")) {
                        MCChatGPT.LOGGER.error("Invalid token prefix");
                        source.sendSuccess(new TranslatableComponent("mcchatgpt.auth.invalid.token"), false);
                        return 0;
                    }
                    Config.getInstance().token = SecureTokenStorage.encrypt(token);
                    ConfigManager.saveConfig();
                    MCChatGPT.startService();
                    source.sendSuccess(new TranslatableComponent("mcchatgpt.auth.success"), false);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerListConversationsCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("listconversations").executes(context -> {
            ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
            List<Conversation> conversations = MCChatGPT.getConversations();
            source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.list"), false);
            for (int i = 0; i < conversations.size(); i++) {
                Conversation conversation = conversations.get(i);
                if(conversation.messageCount() < 2) continue;
                String lastQuestion = conversation.getPreviewMessage().getContent();
                source.sendSuccess(new TextComponent("§b[MCChatGPT]: §r" + (i + 1) + ": " + lastQuestion), false);
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
                    source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.new", index + 1), false);
                } else {
                    source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.continue", index + 1), false);
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
                source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.continue", index + 1), false);
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
                        source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.invalid"), false);
                        return 0;
                    }
                    MCChatGPT.setConversationIndex(index);
                    source.sendSuccess(new TranslatableComponent("mcchatgpt.conversation.continue", index + 1), false);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerSetContextLevelCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("setcontextlevel")
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 3)).executes(context -> {
                    ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
                    int level = IntegerArgumentType.getInteger(context, "level");
                    Config.getInstance().contextLevel = level;
                    ConfigManager.saveConfig();
                    source.sendSuccess(new TranslatableComponent("mcchatgpt.context.level.set", level, new TranslatableComponent("mcchatgpt.context.level." + level).getString()), false);
                    return 1;
                }));
        dispatcher.register(builder);
    }

    private static void registerGetContextLevelCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("getcontextlevel").executes(context -> {
            ClientCommandSourceStack source = (ClientCommandSourceStack) context.getSource();
            int level = Config.getInstance().contextLevel;
            source.sendSuccess(new TranslatableComponent("mcchatgpt.context.level.get", level, new TranslatableComponent("mcchatgpt.context.level." + level).getString()), false);
            return 1;
        });
        dispatcher.register(builder);
    }
}
