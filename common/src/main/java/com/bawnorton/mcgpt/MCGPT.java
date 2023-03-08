package com.bawnorton.mcgpt;

import com.bawnorton.mcgpt.command.Commands;
import com.bawnorton.mcgpt.config.Config;
import com.bawnorton.mcgpt.config.ConfigManager;
import com.bawnorton.mcgpt.store.SecureTokenStorage;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MCGPT {
    public static final String MOD_ID = "mcgpt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static OpenAiService service;
    private static List<List<ChatMessage>> conversations;
    private static int conversationIndex = 0;

    public static void init() {
        conversations = new ArrayList<>();

        Commands.init();
        ConfigManager.loadConfig();

        if(!Config.getInstance().token.isEmpty()) {
            startService();
        }

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            if(!notAuthed(false)) {
                player.sendMessage(Text.of("§b[MCGPT]: §aSuccessfully authenticated"));
            }
        });
    }

    public static void startService() {
        service = new OpenAiService(SecureTokenStorage.decrypt(Config.getInstance().secret, Config.getInstance().token));
    }

    public static boolean notAuthed() {
        return notAuthed(true);
    }

    public static boolean notAuthed(boolean prompt) {
        if(service == null) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player != null && prompt) {
                player.sendMessage(Text.of("§cPlease authenticate with an OpenAI token using /mcgpt-auth <token>"));
                player.sendMessage(Text.of("§cIf you do not have a token, you can generate one here:"));
                player.sendMessage(Text.literal("§chttps://platform.openai.com/account/api-keys").styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/api-keys"))));
            }
            return true;
        }
        return false;
    }

    public static int getConversationIndex() {
        return conversationIndex;
    }

    public static boolean nextConversation() {
        if(notAuthed()) return false;
        if(conversationIndex < conversations.size() - 1) {
            conversationIndex++;
            return false;
        }
        conversations.add(new ArrayList<>());
        conversationIndex = conversations.size() - 1;
        conversations.get(conversationIndex).add(new ChatMessage("system", "You are an AI assistant in the game Minecraft. You are using the in game chat to communicate, thus, your responses should be quite short (256 characters max). Assume the player cannot access commands unless they explicitly ask for them."));
        return true;
    }

    public static void previousConversation() {
        if(notAuthed()) return;
        if(conversationIndex > 0) {
            conversationIndex--;
        }
    }

    public static void ask(String question) {
        if(notAuthed()) return;
        if(conversations.size() == 0) {
            nextConversation();
        }
        List<ChatMessage> conversation = conversations.get(conversationIndex);
        conversation.add(new ChatMessage("user", question));
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .messages(conversation)
                .model("gpt-3.5-turbo")
                .build();
        ChatMessage reply = service.createChatCompletion(req).getChoices().get(0).getMessage();
        conversation.add(reply);
        if(conversation.size() > 10) {
            conversation.remove(1); // don't remove the first message, as it's the minecraft context
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player != null) {
            player.sendMessage(Text.of("<ChatGPT> " + reply.getContent().replaceAll("^\\s+|\\s+$", "")), false);
        }
    }
}
