package me.JAKOP.hinexFeatures.mod.typing;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.mixin.chat.ChatScreenAccessor;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TypingIndicators {

    private static final long REMOTE_EXPIRY_MS = 3500;
    private static final long KEEPALIVE_MS = 2000;
    private static final long IDLE_MS = 10_000;
    private static final int FRAMES = 4;
    private static final long FRAME_MS = 220;
    private static final Style BUBBLE_STYLE = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(HinexConstants.MOD_ID, "typing")));

    private static final Map<UUID, Long> REMOTE = new ConcurrentHashMap<>();

    private static ChatScreen trackedScreen;
    private static String lastText = "";
    private static long lastEditAt;
    private static boolean sentTyping;
    private static long lastSentAt;

    private TypingIndicators() {
    }

    public static void reset() {
        REMOTE.clear();
        sentTyping = false;
        trackedScreen = null;
    }

    public static void onRemote(UUID player, boolean typing) {
        if (typing) REMOTE.put(player, System.currentTimeMillis() + REMOTE_EXPIRY_MS);
        else REMOTE.remove(player);
    }

    public static boolean isTyping(UUID player) {
        Long expiresAt = REMOTE.get(player);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public static void tick(Minecraft minecraft) {
        ServerState state = ClientNetwork.state();
        if (state == null || minecraft.getConnection() == null) return;

        long now = System.currentTimeMillis();
        boolean typing = false;
        if (minecraft.gui.screen() instanceof ChatScreen chat) {
            EditBox input = ((ChatScreenAccessor) chat).hinex$input();
            String text = input != null ? input.getValue() : "";
            if (chat != trackedScreen) {
                trackedScreen = chat;
                lastText = text;
                lastEditAt = 0;
            } else if (!text.equals(lastText)) {
                lastText = text;
                lastEditAt = now;
            }
            typing = !text.isBlank() && !text.startsWith("/") && now - lastEditAt < IDLE_MS;
        } else {
            trackedScreen = null;
        }
        if (!state.typingIndicator()) typing = false;

        if (typing != sentTyping || typing && now - lastSentAt > KEEPALIVE_MS) {
            JsonObject message = Protocol.message(Protocol.TYPING);
            message.addProperty("typing", typing);
            ClientNetwork.send(message);
            sentTyping = typing;
            lastSentAt = now;
        }
    }

    public static void submitBubble(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Vec3 anchor,
                                    int linesAbove) {
        Font font = Minecraft.getInstance().font;
        int frame = (int) (Util.getMillis() / FRAME_MS % FRAMES);
        FormattedCharSequence glyph = Component.literal(Character.toString(0xE000 + frame)).withStyle(BUBBLE_STYLE).getVisualOrderText();

        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y + 0.5, anchor.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);
        float x = -font.width(glyph) / 2.0F;
        collector.submitText(poseStack, x, -2 - 11 * linesAbove, glyph, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, -1, 0, 0);
        poseStack.popPose();
    }
}
