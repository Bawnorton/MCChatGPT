package com.bawnorton.mcchatgpt;

import com.bawnorton.mcchatgpt.command.CommandHandler;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MCChatGPT.MODID)
public class MCChatGPT {
    public static final String MODID = "mcchatgpt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static final ExecutorService executor;

    private static OpenAiService service;
    private static List<List<ChatMessage>> conversations;
    private static int conversationIndex = 0;

    static {
        executor = Executors.newFixedThreadPool(1);
    }

    public MCChatGPT() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    public static void startService() {
        service = new OpenAiService(SecureTokenStorage.decrypt(Config.getInstance().secret, Config.getInstance().token));
    }

    public static boolean notAuthed() {
        return notAuthed(true);
    }

    public static boolean notAuthed(boolean prompt) {
        if(service == null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if(player != null && prompt) {
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.message1"), false);
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.message2"), false);
                player.displayClientMessage(Component.literal("§chttps://platform.openai.com/account/api-keys").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/api-keys"))), false);
            }
            return true;
        }
        return false;
    }

    public static List<List<ChatMessage>> getConversations() {
        return conversations;
    }

    public static int getConversationIndex() {
        return conversationIndex;
    }

    public static void setConversationIndex(int index) {
        if(index >= 0 && index < conversations.size()) {
            conversationIndex = index;
        }
    }

    public static boolean nextConversation() {
        if(notAuthed()) throw new IllegalStateException("Not authenticated");
        if(conversationIndex < conversations.size() - 1) {
            conversationIndex++;
            return false;
        }
        conversations.add(new ArrayList<>());
        conversationIndex = conversations.size() - 1;
        conversations.get(conversationIndex).add(new ChatMessage("system", "Context: You are an AI assistant in the game Minecraft. Limit your responses to 256 characters. Assume the player cannot access commands unless explicitly asked for them. Do not simulate conversations"));
        return true;
    }

    public static void previousConversation() {
        if(notAuthed()) throw new IllegalStateException("Not authenticated");
        if(conversationIndex > 0) {
            conversationIndex--;
        }
    }

    private static void askSync(String question) {
        if(conversations.size() == 0) {
            nextConversation();
        }
        List<ChatMessage> conversation = conversations.get(conversationIndex);
        conversation.add(new ChatMessage("user", question));
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .messages(conversation)
                .model("gpt-3.5-turbo")
                .build();
        ChatMessage reply;
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null) return;
        try {
            reply = service.createChatCompletion(req).getChoices().get(0).getMessage();
            conversation.add(reply);
            if(conversation.size() > 10) {
                conversation.remove(1); // don't remove the first message, as it's the minecraft context
            }
            player.displayClientMessage(Component.literal("<ChatGPT> " + reply.getContent().replaceAll("^\\s+|\\s+$", "")), false);
        } catch (RuntimeException e) {
            MCChatGPT.LOGGER.error("Error while communicating with OpenAI", e);
            if(e.getMessage().toLowerCase().contains("exceeded your current quota")) {
                player.displayClientMessage(Component.translatable("mcchatgpt.ask.quota").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/usage")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("https://platform.openai.com/account/usage")))), false);
            } else {
                player.displayClientMessage(Component.translatable("mcchatgpt.ask.error").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(e.getMessage())))), false);
            }
        }
    }

    public static void ask(String question) {
        if(notAuthed()) return;
        executor.execute(() -> {
            try {
                askSync(question);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            conversations = new ArrayList<>();

            ConfigManager.loadConfig();

            if (!Config.getInstance().token.isEmpty()) {
                startService();
            }

            MinecraftForge.EVENT_BUS.addListener(MCChatGPT.ClientModEvents::onClientJoin);
            MinecraftForge.EVENT_BUS.addListener(CommandHandler::registerCommands);
        }

        public static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
            if(!notAuthed(false)) {
                LocalPlayer player = event.getPlayer();
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.success"), false);
            }
        }
    }
}
