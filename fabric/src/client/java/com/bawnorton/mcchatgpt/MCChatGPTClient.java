package com.bawnorton.mcchatgpt;

import com.bawnorton.mcchatgpt.command.CommandHandler;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.bawnorton.mcchatgpt.util.Context;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
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
    private static int contextLevel = 0;

    static {
        executor = Executors.newFixedThreadPool(1);
    }

    public static void startService() {
        service = new OpenAiService(SecureTokenStorage.decrypt(Config.getInstance().secret, Config.getInstance().token));
    }

    public static boolean notAuthed() {
        return notAuthed(true);
    }

    public static boolean notAuthed(boolean prompt) {
        if (service == null) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null && prompt) {
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
        if (index >= 0 && index < conversations.size()) {
            conversationIndex = index;
        }
    }

    public static boolean nextConversation() {
        if (notAuthed()) throw new IllegalStateException("Not authenticated");
        if (conversationIndex < conversations.size() - 1) {
            conversationIndex++;
            return false;
        }
        conversations.add(new ArrayList<>());
        conversationIndex = conversations.size() - 1;
        conversations.get(conversationIndex).add(new ChatMessage("system", "You are an AI assistant in the game Minecraft. Limit your responses to 256 characters. Assume the player cannot access commands unless explicitly asked for them"));
        return true;
    }

    public static void previousConversation() {
        if (notAuthed()) throw new IllegalStateException("Not authenticated");
        if (conversationIndex > 0) {
            conversationIndex--;
        }
    }

	private static List<ChatMessage> addContext(List<ChatMessage> conversation) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return conversation;
        HitResult target = client.crosshairTarget;
        Context.Builder contextBuilder = Context.builder();
		return switch (contextLevel) {
            case 3:
                RegistryEntry<Biome> biome = player.world.getBiome(player.getBlockPos());
                biome.getKey().ifPresent(biomeKey -> contextBuilder.addBiome(biomeKey.getValue().toString()));
                if(target instanceof BlockHitResult blockHitResult) {
                    Block block = player.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
                    contextBuilder.addBlockTarget(block);
                }
                RegistryKey<DimensionType> dimension = player.world.getDimensionKey();
                contextBuilder.addDimension(dimension.getValue().toString());

            case 2:
                List<LivingEntity> nearbyEntities = player.world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(64), entity -> entity != player);
                if (target instanceof EntityHitResult entityHitResult) {
                    Entity entity = entityHitResult.getEntity();
                    if (entity instanceof LivingEntity livingEntity) {
                        contextBuilder.addEntityTarget(livingEntity);
                    }
                }
                contextBuilder.addEntities(nearbyEntities);
            case 1:
                List<ItemStack> playerInventory = player.getInventory().main;
                List<ItemStack> playerMainInventory = playerInventory.subList(9, playerInventory.size());
                List<ItemStack> playerHotbar = playerInventory.subList(0, 9);

                contextBuilder
                        .addInventory("Player", playerMainInventory)
                        .addHotbar(playerHotbar)
                        .addArmor(player.getArmorItems())
                        .addMainHand(player.getMainHandStack())
                        .addOffHand(player.getOffHandStack());

                ChatMessage contextMessage = new ChatMessage("system", contextBuilder.build().get());
                conversation.add(contextMessage);
            default:
                yield conversation;
		};
	}

    private static void askSync(String question) {
        if (conversations.size() == 0) {
            nextConversation();
        }
        List<ChatMessage> conversation = conversations.get(conversationIndex);
        conversation = addContext(conversation);
        conversation.add(new ChatMessage("user", question));
        ChatCompletionRequest req = ChatCompletionRequest.builder().messages(conversation).model("gpt-3.5-turbo").build();
        ChatMessage reply;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        try {
            reply = service.createChatCompletion(req).getChoices().get(0).getMessage();
            conversation.add(reply);
            while (conversation.size() > 10 * (contextLevel + 1)) {
                conversation.remove(1); // don't remove the first message, as it's the minecraft context
            }
            player.sendMessage(Text.of("<ChatGPT> " + reply.getContent().replaceAll("^\\s+|\\s+$", "")), false);
        } catch (RuntimeException e) {
            MCChatGPTClient.LOGGER.error("Error while communicating with OpenAI", e);
            if (e.getMessage().toLowerCase().contains("exceeded your current quota")) {
                player.sendMessage(Text.translatable("mcchatgpt.ask.quota").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/usage")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("https://platform.openai.com/account/usage")))));
            } else {
                player.sendMessage(Text.translatable("mcchatgpt.ask.error").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(e.getMessage())))));
            }
        }
    }

    public static void ask(String question) {
        if (notAuthed()) return;
        executor.execute(() -> {
            try {
                askSync(question);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

	public static void setContextLevel(int level) {
		contextLevel = level;
	}

	public static int getContextLevel() {
		return contextLevel;
	}

    @Override
    public void onInitializeClient() {
        conversations = new ArrayList<>();

        CommandHandler.registerCommands();
        ConfigManager.loadConfig();

        if (!Config.getInstance().token.isEmpty()) {
            startService();
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!notAuthed(false)) {
                assert client.player != null;
                client.player.sendMessage(Text.translatable("mcchatgpt.auth.success"));
            }
        });
    }
}