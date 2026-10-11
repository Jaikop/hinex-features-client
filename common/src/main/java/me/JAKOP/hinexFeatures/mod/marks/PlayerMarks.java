package me.JAKOP.hinexFeatures.mod.marks;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMarks {

    public record Mark(String symbol, int color, float scale, float height) {
    }

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    private PlayerMarks() {
    }

    public static void reset() {
        MARKS.clear();
    }

    public static void handle(JsonObject message) {
        try {
            UUID uuid = UUID.fromString(message.get("uuid").getAsString());
            String symbol = message.has("symbol") ? message.get("symbol").getAsString() : "";
            if (symbol.isEmpty()) {
                MARKS.remove(uuid);
                return;
            }
            if (symbol.length() > 8) symbol = symbol.substring(0, 8);
            int color = message.has("color") ? message.get("color").getAsInt() & 0xFFFFFF : 0xFFFFFF;
            float scale = message.has("scale") ? Mth.clamp(message.get("scale").getAsFloat(), 0.2F, 5F) : 1F;
            float height = message.has("height") ? Mth.clamp(message.get("height").getAsFloat(), -2F, 3F) : 0.5F;
            MARKS.put(uuid, new Mark(symbol, color, scale, height));
        } catch (RuntimeException ignored) {
        }
    }

    public static @Nullable Mark get(UUID player) {
        return MARKS.get(player);
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Vec3 anchor,
                              Mark mark, int linesAbove) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence text = Component.literal(mark.symbol()).getVisualOrderText();
        float scale = 0.025F * mark.scale();

        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y + mark.height(), anchor.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(scale, -scale, scale);
        float x = -font.width(text) / 2.0F;
        float y = -font.lineHeight - 11F * linesAbove / mark.scale();
        collector.submitText(poseStack, x, y, text, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
                0xFF000000 | mark.color(), 0, 0);
        poseStack.popPose();
    }
}
