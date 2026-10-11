package me.JAKOP.hinexFeatures.mod.selftest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PackTestScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");
    private static final int CELL = 21;

    private record Sample(String label, ItemStack stack) {
    }

    private final String packId;
    private final List<Sample> samples = new ArrayList<>();

    PackTestScreen(String packId) {
        super(Component.literal("pack test"));
        this.packId = packId;
    }

    @Override
    protected void init() {
        samples.clear();
        Map<Identifier, Resource> items = new TreeMap<>(minecraft.getResourceManager()
                .listResources("items", id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> entry : items.entrySet()) {
            if (!entry.getValue().sourcePackId().equals(packId)) continue;
            String path = entry.getKey().getPath();
            Identifier model = Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(),
                    path.substring("items/".length(), path.length() - ".json".length()));

            List<Float> values = new ArrayList<>();
            values.add(null);
            try (Reader reader = entry.getValue().openAsReader()) {
                collectValues(JsonParser.parseReader(reader), values);
            } catch (Exception e) {
                LOGGER.error("[selftest] items/{}: {}", model.getPath(), e.toString());
            }
            if (minecraft.level == null) {
                LOGGER.info("[selftest] items/{}: cmd {}", model.getPath(), values.subList(1, values.size()));
                continue;
            }
            for (Float value : values) {
                ItemStack stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, model);
                if (value != null) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(value), List.of(), List.of(), List.of()));
                samples.add(new Sample(model.getPath() + (value != null ? " cmd=" + value : ""), stack));
            }
        }
        for (int i = 0; i < samples.size(); i++) LOGGER.info("[selftest] item #{} {}", i, samples.get(i).label());
    }

    private static void collectValues(JsonElement element, List<Float> values) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectValues(child, values);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("threshold")) values.add(object.get("threshold").getAsFloat());
            if (object.has("when") && object.get("when").isJsonObject() && object.getAsJsonObject("when").has("floats")) {
                JsonArray floats = object.getAsJsonObject("when").getAsJsonArray("floats");
                if (!floats.isEmpty()) values.add(floats.get(0).getAsFloat());
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) collectValues(child.getValue(), values);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0xFF20222C);
        g.text(font, "Эмодзи (U+4E00..U+4E2C):", 6, 4, 0xFFAAAAAA, false);

        g.pose().pushMatrix();
        g.pose().translate(6, 16);
        g.pose().scale(1.5f, 1.5f);
        int x = 0;
        int y = 0;
        for (int cp = 0x4E00; cp <= 0x4E2C; cp++) {
            g.text(font, Character.toString(cp), x, y, 0xFFFFFFFF, false);
            x += 13;
            if (x > (width - 20) / 1.5f) {
                x = 0;
                y += 13;
            }
        }
        g.pose().popMatrix();

        int top = 16 + (int) ((y + 14) * 1.5f) + 6;
        g.text(font, "Предметы пака (номер -> лог):", 6, top, 0xFFAAAAAA, false);
        int perRow = Math.max(1, (width - 12) / CELL);
        for (int i = 0; i < samples.size(); i++) {
            int cx = 6 + (i % perRow) * CELL;
            int cy = top + 12 + (i / perRow) * (CELL + 5);
            g.fill(cx, cy, cx + 18, cy + 18, 0xFF30323F);
            g.item(samples.get(i).stack(), cx + 1, cy + 1);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy + 18.5f);
            g.pose().scale(0.5f, 0.5f);
            g.text(font, String.valueOf(i), 0, 0, 0xFFFFFF55, false);
            g.pose().popMatrix();
        }
    }
}
