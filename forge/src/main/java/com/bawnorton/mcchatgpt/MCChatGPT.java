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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
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
    private static List<Conversation> conversations;
    private static int conversationIndex = 0;

    private static final double COST_PER_TOKEN = 2e-6; // $0.000002 per token (https://openai.com/pricing)

    static {
        executor = Executors.newFixedThreadPool(1);
    }

    public MCChatGPT() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void startService() {
        service = new OpenAiService(SecureTokenStorage.decrypt(Config.getInstance().secret, Config.getInstance().token));
    }

    public static boolean notAuthed() {
        return notAuthed(true);
    }

    public static boolean notAuthed(boolean prompt) {
        if (service == null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && prompt) {
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.message1"), false);
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.message2"), false);
                player.displayClientMessage(Component.literal("§chttps://platform.openai.com/account/api-keys").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/api-keys"))), false);
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
        conversations.get(conversationIndex).addMessage(new ChatMessage("system", "Context: You are an AI assistant in the game Minecraft. Limit your responses to 256 characters. Assume the player cannot access commands unless explicitly asked for them. Do not simulate conversations"));
        return true;
    }

    public static void previousConversation() {
        if (notAuthed()) throw new IllegalStateException("Not authenticated");
        if (conversationIndex > 0) {
            conversationIndex--;
        }
    }

    private static void addContext(Conversation conversation) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;
        HitResult target = client.crosshairPickEntity.pick(client.gameMode.getPickRange(), 1.0f, false);
        Context.Builder contextBuilder = Context.builder();
        switch (Config.getInstance().contextLevel) {
            case 3:
                List<LivingEntity> nearbyEntities = player.getLevel().getNearbyEntities(LivingEntity.class, TargetingConditions.DEFAULT.selector(entity -> entity != player), player, player.getBoundingBox().inflate(64));
                if (target instanceof EntityHitResult entityHitResult) {
                    Entity entity = entityHitResult.getEntity();
                    if (entity instanceof LivingEntity livingEntity) {
                        contextBuilder.addEntityTarget(livingEntity);
                    }
                }

                contextBuilder.addEntities(nearbyEntities);
            case 2:
                Holder<Biome> biome = player.getLevel().getBiome(player.blockPosition());
                biome.unwrapKey().ifPresent(biomeKey -> contextBuilder.addBiome(biomeKey.registry().getPath()));
                Block block = null;
                if(target instanceof BlockHitResult blockHitResult) {
                    block = player.getLevel().getBlockState(blockHitResult.getBlockPos()).getBlock();
                }
                contextBuilder.addBlockTarget(block);
                Holder<DimensionType> dimension = player.getLevel().dimensionTypeRegistration();
                dimension.unwrapKey().ifPresent(dimensionKey -> contextBuilder.addDimension(dimensionKey.registry().getPath()));

            case 1:
                List<ItemStack> playerInventory = player.getInventory().items;
                List<ItemStack> playerMainInventory = playerInventory.subList(9, playerInventory.size());
                List<ItemStack> playerHotbar = playerInventory.subList(0, 9);

                contextBuilder
                        .addInventory("Player", playerMainInventory)
                        .addHotbar(playerHotbar)
                        .addArmor(player.getArmorSlots())
                        .addMainHand(player.getMainHandItem())
                        .addOffHand(player.getOffhandItem())
                        .addPlayerPosition(player.blockPosition());

                ChatMessage contextMessage = new ChatMessage("system", contextBuilder.build().get());
                conversation.setContext(contextMessage);
            default:
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
        ChatCompletionRequest req = ChatCompletionRequest.builder().messages(conversation.getMessages()).model("gpt-3.5-turbo").build();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        try {
            ChatCompletionResult reply = service.createChatCompletion(req);

            long tokensUsed = reply.getUsage().getTotalTokens();
            MathContext sigfigContext = new MathContext(1);
            BigDecimal costDecimal = BigDecimal.valueOf((float) (tokensUsed * COST_PER_TOKEN));
            costDecimal = costDecimal.round(sigfigContext);
            float cost = costDecimal.floatValue();

            LOGGER.info("Used {} tokens (${})", tokensUsed, cost);

            ChatMessage replyMessage = reply.getChoices().get(0).getMessage();
            conversation.addMessage(replyMessage);
            while (conversation.messageCount() > 10 * (Config.getInstance().contextLevel + 1)) {
                conversation.removeMessage(1); // don't remove the first message, as it's the minecraft context
            }
            player.displayClientMessage(Component.literal("<ChatGPT> " + replyMessage.getContent().replaceAll("^\\s+|\\s+$", "")).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("mcchatgpt.token.usage", tokensUsed, cost)))), false);
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
        if (notAuthed()) return;
        executor.execute(() -> {
            try {
                askSync(question);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
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
            if (!notAuthed(false)) {
                LocalPlayer player = event.getPlayer();
                player.displayClientMessage(Component.translatable("mcchatgpt.auth.success"), false);
            }
        }
    }
}
