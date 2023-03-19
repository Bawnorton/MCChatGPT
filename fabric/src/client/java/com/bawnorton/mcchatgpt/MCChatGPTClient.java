package com.bawnorton.mcchatgpt;

import com.bawnorton.mcchatgpt.command.CommandHandler;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MCChatGPTClient implements ClientModInitializer {
	public static final String MOD_ID = "mcchatgpt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ExecutorService executor;

	private static OpenAiService service;
	private static List<List<ChatMessage>> conversations;
	private static int conversationIndex = 0;

	static {
		executor = Executors.newFixedThreadPool(1);
	}

	@Override
	public void onInitializeClient() {
		conversations = new ArrayList<>();

		CommandHandler.registerCommands();
		ConfigManager.loadConfig();

		if(!Config.getInstance().token.isEmpty()) {
			startService();
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if(!notAuthed(false)) {
				assert client.player != null;
				client.player.sendMessage(Text.translatable("mcchatgpt.auth.success"));
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
				player.sendMessage(Text.translatable("mcchatgpt.auth.message1"));
				player.sendMessage(Text.translatable("mcchatgpt.auth.message2"));
				player.sendMessage(Text.literal("§chttps://platform.openai.com/account/api-keys").styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/api-keys"))));
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
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if(player == null) return;
		try {
			reply = service.createChatCompletion(req).getChoices().get(0).getMessage();
			conversation.add(reply);
			if(conversation.size() > 10) {
				conversation.remove(1); // don't remove the first message, as it's the minecraft context
			}
			player.sendMessage(Text.of("<ChatGPT> " + reply.getContent().replaceAll("^\\s+|\\s+$", "")), false);
		} catch (RuntimeException e) {
			MCChatGPTClient.LOGGER.error("Error while communicating with OpenAI", e);
			if(e.getMessage().toLowerCase().contains("exceeded your current quota")) {
				player.sendMessage(Text.translatable("mcchatgpt.ask.quota").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/usage")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("https://platform.openai.com/account/usage")))));
			} else {
				player.sendMessage(Text.translatable("mcchatgpt.ask.error").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(e.getMessage())))));
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
}