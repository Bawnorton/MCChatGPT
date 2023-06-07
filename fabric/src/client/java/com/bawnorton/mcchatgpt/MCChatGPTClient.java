package com.bawnorton.mcchatgpt;

import com.bawnorton.mcchatgpt.command.CommandHandler;
import com.bawnorton.mcchatgpt.config.Config;
import com.bawnorton.mcchatgpt.config.ConfigManager;
import com.bawnorton.mcchatgpt.store.SecureTokenStorage;
import com.bawnorton.mcchatgpt.util.Context;
import com.bawnorton.mcchatgpt.util.Conversation;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MCChatGPTClient implements ClientModInitializer {
    public static final String MOD_ID = "mcchatgpt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ExecutorService executor;

    private static OpenAiService service;
    private static List<Conversation> conversations;
    private static int conversationIndex = 0;

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

    public static List<Conversation> getConversations() {
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
        conversations.add(new Conversation());
        conversationIndex = conversations.size() - 1;
        conversations.get(conversationIndex).addMessage(new ChatMessage("system", "You are an AI assistant in the game Minecraft version 1.19.4. Limit your responses to 256 characters. Assume the player cannot access commands unless explicitly asked for them. You may be provided with player context when asked a question. Don't answer beyond what is asked. Don't mention the player context, just use it"));
        return true;
    }

    public static void previousConversation() {
        if (notAuthed()) throw new IllegalStateException("Not authenticated");
        if (conversationIndex > 0) {
            conversationIndex--;
        }
    }

	private static void addContext(Conversation conversation) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        HitResult target = client.crosshairTarget;
        Context.Builder contextBuilder = Context.builder();
		switch (Config.getInstance().contextLevel) {
            case 3:
                List<LivingEntity> nearbyEntities = player.getWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(64), entity -> entity != player);
                if (target instanceof EntityHitResult entityHitResult) {
                    Entity entity = entityHitResult.getEntity();
                    if (entity instanceof LivingEntity livingEntity) {
                        contextBuilder.addEntityTarget(livingEntity);
                    }
                }

                contextBuilder.addEntities(nearbyEntities);
            case 2:
                RegistryEntry<Biome> biome = player.getWorld().getBiome(player.getBlockPos());
                biome.getKey().ifPresent(biomeKey -> contextBuilder.addBiome(biomeKey.getValue().getPath()));
                Block block = null;
                if(target instanceof BlockHitResult blockHitResult) {
                    block = player.getWorld().getBlockState(blockHitResult.getBlockPos()).getBlock();
                }
                RegistryKey<DimensionType> dimension = player.getWorld().getDimensionKey();

                contextBuilder
                        .addBlockTarget(block)
                        .addDimension(dimension.getValue().getPath());
            case 1:
                List<ItemStack> playerInventory = player.getInventory().main;
                List<ItemStack> playerMainInventory = playerInventory.subList(9, playerInventory.size());
                List<ItemStack> playerHotbar = playerInventory.subList(0, 9);

                contextBuilder
                        .addInventory("Player", playerMainInventory)
                        .addHotbar(playerHotbar)
                        .addArmor(player.getArmorItems())
                        .addMainHand(player.getMainHandStack())
                        .addOffHand(player.getOffHandStack())
                        .addPlayerPosition(player.getBlockPos());
            default:
                ChatMessage contextMessage = new ChatMessage("system", contextBuilder.build(Config.getInstance().contextLevel).get());
                conversation.setContext(contextMessage);
		}
	}

    private static void askSync(String question) {
        if (conversations.size() == 0) {
            nextConversation();
        }

        Conversation conversation = conversations.get(conversationIndex);
        addContext(conversation);

        ChatMessage questionMessage = new ChatMessage("user", question);
        conversation.addMessage(questionMessage);
        conversation.setPreviewMessage(questionMessage);
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .messages(conversation.getMessages())
                .model(Config.getInstance().model)
                .temperature(Config.getInstance().temperature)
                .build();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        try {
            ChatCompletionResult reply = service.createChatCompletion(req);

            long tokensUsed = reply.getUsage().getTotalTokens();
            MathContext sigfigContext = new MathContext(1);
            BigDecimal costDecimal = BigDecimal.valueOf(tokensUsed * Config.getInstance().estimatedCostPerToken);
            costDecimal = costDecimal.round(sigfigContext);
            float cost = costDecimal.floatValue();

            LOGGER.info("Used {} tokens (${})", tokensUsed, cost);

            ChatMessage replyMessage = reply.getChoices().get(0).getMessage();
            conversation.addMessage(replyMessage);
            while (conversation.messageCount() > 10) {
                conversation.removeMessage(1); // don't remove the first message, as it's the minecraft context
            }
            player.sendMessage(Text.literal("<ChatGPT> " + replyMessage.getContent().replaceAll("^\\s+|\\s+$", "")).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("mcchatgpt.token.usage", tokensUsed, cost)))), false);
        } catch (RuntimeException e) {
            MCChatGPTClient.LOGGER.error("Error while communicating with OpenAI", e);
            if (e.getMessage().toLowerCase().contains("exceeded your current quota")) {
                player.sendMessage(Text.translatable("mcchatgpt.ask.quota").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/usage")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("https://platform.openai.com/account/usage")))));
            } else if (e.getMessage().toLowerCase().contains("maximum context length")) {
                player.sendMessage(Text.translatable("mcchatgpt.ask.excessive.context").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(e.getMessage())))));
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