package me.JAKOP.hinexFeatures.mod.prints;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class PrintsClient {

    private static long lastAttack;

    private PrintsClient() {
    }

    private static String id(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("hinexfeatures_item", "");
    }

    public static boolean isBrush(ItemStack stack) {
        return !stack.isEmpty() && ClientNetwork.state() != null && id(stack).endsWith("brush");
    }

    public static boolean isFilledSample(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.copyTag().getStringOr("hinexfeatures_prints", "").isEmpty();
    }

    public static void attack(BlockPos pos, Direction face) {
        long now = Util.getMillis();
        if (now - lastAttack < 250) return;
        lastAttack = now;
        JsonObject message = Protocol.message(Protocol.PRINTS);
        message.addProperty("x", pos.getX());
        message.addProperty("y", pos.getY());
        message.addProperty("z", pos.getZ());
        message.addProperty("face", face.get3DDataValue());
        ClientNetwork.send(message);
    }
}
