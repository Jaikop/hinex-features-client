package me.JAKOP.hinexFeatures.mod.essence;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class KnowledgeScreen extends Screen {

    private static final int NODE = 22;
    private static final int GOLD = 0xFFFFC857;
    private static final int REACHABLE = 0xFF9A7FD6;
    private static final int SLOT = 17;
    private static final int CARD_W = SLOT * 3 + 36;
    private static final int CARD_H = SLOT * 3 + 6;

    private final @Nullable Screen parent;
    private @Nullable String selected;
    private int seenVersion = -1;
    private double recipeScroll;
    private int recipeContent;

    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private int panelX;
    private int panelW;
    private int gaugeX;
    private int recipesTop;

    public KnowledgeScreen(@Nullable Screen parent) {
        super(Component.literal("Древо знаний"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 6;
        panelW = Math.max(150, Math.min(210, (int) (width * 0.42F)));
        canvasX = margin;
        canvasY = 24;
        canvasW = width - panelW - margin * 3;
        canvasH = height - canvasY - margin;
        panelX = canvasX + canvasW + margin;
        gaugeX = canvasX + canvasW - 12;
    }

    @Override
    public void tick() {
        if (EssenceClient.version() == seenVersion) return;
        seenVersion = EssenceClient.version();
        KnowledgeData tree = EssenceClient.tree();
        if (tree == null || tree.nodes.isEmpty()) return;
        if (selected == null || tree.node(selected) == null) {
            KnowledgeData.Node next = tree.nodes.stream().filter(n -> !n.unlocked && tree.reachable(n)).findFirst().orElse(tree.nodes.getFirst());
            selected = next.id;
        }
    }

    private record Bounds(int minX, int minY, int cellX, int cellY, int originX, int bottomY) {
    }

    private Bounds bounds(KnowledgeData tree) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (KnowledgeData.Node node : tree.nodes) {
            minX = Math.min(minX, node.x);
            maxX = Math.max(maxX, node.x);
            minY = Math.min(minY, node.y);
            maxY = Math.max(maxY, node.y);
        }
        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        int treeW = gaugeX - canvasX - 26;
        int cellX = Math.max(NODE + 4, Math.min(48, (treeW - NODE - 10) / spanX));
        int cellY = Math.max(NODE + 6, Math.min(40, (canvasH - NODE - 18) / spanY));
        int originX = canvasX + 8 + NODE / 2 + (treeW - NODE - 10 - spanX * cellX) / 2;
        int bottomY = canvasY + canvasH - (canvasH - spanY * cellY) / 2;
        return new Bounds(minX, minY, cellX, cellY, originX, bottomY);
    }

    private static int cx(Bounds b, KnowledgeData.Node node) {
        return b.originX() + (node.x - b.minX()) * b.cellX();
    }

    private static int cy(Bounds b, KnowledgeData.Node node) {
        return b.bottomY() - (node.y - b.minY()) * b.cellY();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        KnowledgeData tree = EssenceClient.tree();
        drawHeader(g, tree, mouseX, mouseY);
        Draw.panel(g, canvasX, canvasY, canvasW, canvasH, 4, Theme.PANEL, Theme.PANEL_BORDER);
        Draw.panel(g, panelX, canvasY, panelW, canvasH, 4, Theme.PANEL, Theme.PANEL_BORDER);
        if (tree == null) {
            g.centeredText(font, "Загрузка…", canvasX + canvasW / 2, canvasY + canvasH / 2 - 4, Theme.TEXT_MUTED);
            super.extractRenderState(g, mouseX, mouseY, a);
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        drawTree(g, tree, mouseX, mouseY, tooltip);
        drawGauge(g, tree, mouseX, mouseY, tooltip);
        drawPanel(g, tree, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
        if (!tooltip.isEmpty()) g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor g, @Nullable KnowledgeData tree, int mouseX, int mouseY) {
        Component title = Draw.gradientText((tree == null ? "Древо знаний" : tree.title.getString()).toUpperCase(),
                EssenceHud.ESSENCE, 0xFFB46CFF, true);
        g.text(font, title, 8, 8, 0xFFFFFFFF, true);
        int closeX = width - 16;
        boolean hover = mouseX >= closeX - 3 && mouseX < closeX + 10 && mouseY >= 4 && mouseY < 20;
        g.text(font, "✕", closeX, 8, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
        String own = EssenceClient.essence() + " ✦";
        g.text(font, own, closeX - 12 - font.width(own), 8, EssenceHud.ESSENCE, true);
    }

    private enum State { UNLOCKED, REACHABLE, LOCKED }

    private static State state(KnowledgeData tree, KnowledgeData.Node node) {
        if (node.unlocked) return State.UNLOCKED;
        return tree.reachable(node) ? State.REACHABLE : State.LOCKED;
    }

    private void drawTree(GuiGraphicsExtractor g, KnowledgeData tree, int mouseX, int mouseY, List<Component> tooltip) {
        Bounds b = bounds(tree);
        for (KnowledgeData.Node node : tree.nodes) {
            for (String id : node.requires) {
                KnowledgeData.Node from = tree.node(id);
                if (from == null) continue;
                int color = from.unlocked && node.unlocked ? GOLD : from.unlocked ? 0xFF6C5AA0 : 0xFF2A2E4A;
                elbow(g, cx(b, from), cy(b, from), cx(b, node), cy(b, node), color);
            }
        }
        for (KnowledgeData.Node node : tree.nodes) {
            int x = cx(b, node) - NODE / 2;
            int y = cy(b, node) - NODE / 2;
            State state = state(tree, node);
            int border = switch (state) {
                case UNLOCKED -> GOLD;
                case REACHABLE -> REACHABLE;
                case LOCKED -> Theme.PANEL_BORDER;
            };
            int fill = switch (state) {
                case UNLOCKED -> 0xFF2A2210;
                case REACHABLE -> 0xFF1B1830;
                case LOCKED -> 0xFF121424;
            };
            if (node.id.equals(selected)) Draw.roundRect(g, x - 3, y - 3, NODE + 6, NODE + 6, 6, 0x60FFFFFF);
            Draw.roundRect(g, x - 1, y - 1, NODE + 2, NODE + 2, 5, border);
            Draw.roundRect(g, x, y, NODE, NODE, 4, fill);
            g.item(node.icon, x + 3, y + 3);
            if (!node.unlocked) {
                int bar = NODE * Math.min(node.required, tree.essence) / Math.max(1, node.required);
                g.fill(x, y + NODE + 2, x + NODE, y + NODE + 4, 0xFF2A2E4A);
                g.fill(x, y + NODE + 2, x + bar, y + NODE + 4, state == State.REACHABLE ? EssenceHud.ESSENCE : 0xFF4A4460);
            }
            if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE) {
                tooltip.add(node.name);
                tooltip.add(status(tree, node));
                tooltip.addAll(node.description);
            }
        }
    }

    private Component status(KnowledgeData tree, KnowledgeData.Node node) {
        if (node.unlocked) return Component.literal("Изучено").withColor(0x7CFF8C);
        return Component.literal(Math.min(tree.essence, node.required) + " / " + node.required + " ✦").withColor(EssenceHud.ESSENCE & 0xFFFFFF);
    }

    private static void elbow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int mid = (y1 + y2) / 2;
        g.fill(x1 - 1, Math.min(y1, mid), x1 + 1, Math.max(y1, mid) + 1, color);
        g.fill(Math.min(x1, x2) - 1, mid - 1, Math.max(x1, x2) + 1, mid + 1, color);
        g.fill(x2 - 1, Math.min(mid, y2), x2 + 1, Math.max(mid, y2) + 1, color);
    }

    private void drawGauge(GuiGraphicsExtractor g, KnowledgeData tree, int mouseX, int mouseY, List<Component> tooltip) {
        if (tree.globals.isEmpty()) return;
        int top = canvasY + 14;
        int bottom = canvasY + canvasH - 14;
        int max = Math.max(1, tree.globals.getLast().required());
        int filled = (int) ((bottom - top) * Math.min(1.0, tree.global / (double) max));
        g.fill(gaugeX, top, gaugeX + 3, bottom, 0xFF2A2E4A);
        g.fill(gaugeX, bottom - filled, gaugeX + 3, bottom, EssenceHud.GLOBAL);
        for (KnowledgeData.GlobalLevel level : tree.globals) {
            int y = bottom - (int) ((bottom - top) * (level.required() / (double) max));
            boolean reached = tree.global >= level.required();
            int x = gaugeX - 21;
            Draw.roundRect(g, x - 1, y - 9, 18, 18, 4, reached ? EssenceHud.GLOBAL : Theme.PANEL_BORDER);
            Draw.roundRect(g, x, y - 8, 16, 16, 3, reached ? 0xFF16243A : 0xFF121424);
            g.item(level.icon(), x, y - 8);
            g.fill(x + 16, y, gaugeX, y + 1, reached ? EssenceHud.GLOBAL : Theme.PANEL_BORDER);
            if (mouseX >= x - 1 && mouseX < gaugeX + 4 && mouseY >= y - 9 && mouseY < y + 9) {
                tooltip.add(level.name());
                tooltip.add(Component.literal(Math.min(tree.global, level.required()) + " / " + level.required() + " ✦ — весь сервер")
                        .withColor(EssenceHud.GLOBAL & 0xFFFFFF));
                tooltip.addAll(level.description());
            }
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, KnowledgeData tree, int mouseX, int mouseY) {
        KnowledgeData.Node node = selected == null ? null : tree.node(selected);
        if (node == null) return;
        int x = panelX + 7;
        int y = canvasY + 7;
        int inner = panelW - 14;
        g.item(node.icon, x, y);
        Draw.crispBold(g, font, node.name, x + 21, y, node.unlocked ? GOLD : Theme.TEXT, true);
        if (node.unlocked) {
            g.text(font, Component.literal("Изучено").withColor(0x7CFF8C), x + 21, y + 10, 0xFFFFFFFF, false);
        } else {
            int bar = (inner - 21) * Math.min(node.required, tree.essence) / Math.max(1, node.required);
            g.fill(x + 21, y + 11, x + inner, y + 13, 0xFF2A2E4A);
            g.fill(x + 21, y + 11, x + 21 + bar, y + 13, tree.reachable(node) ? EssenceHud.ESSENCE : 0xFF4A4460);
            String progress = Math.min(tree.essence, node.required) + " / " + node.required + " ✦";
            g.text(font, progress, x + inner - font.width(progress), y + 15, EssenceHud.ESSENCE, false);
            if (!tree.reachable(node)) {
                KnowledgeData.Node need = node.requires.isEmpty() ? null : tree.node(node.requires.getFirst());
                if (need != null) g.text(font, Component.literal("после: ").append(need.name), x + 21, y + 15, Theme.TEXT_MUTED, false);
            }
        }
        y += 28;
        for (Component line : node.description) {
            for (FormattedCharSequence part : font.split(line, inner)) {
                g.text(font, part, x, y, Theme.TEXT_DIM, false);
                y += 10;
            }
        }
        recipesTop = y + 2;
        drawRecipes(g, node, x, recipesTop, inner, canvasY + canvasH - 5, mouseX, mouseY);
    }

    private void drawRecipes(GuiGraphicsExtractor g, KnowledgeData.Node node, int x, int top, int inner, int bottom,
                             int mouseX, int mouseY) {
        int columns = Math.max(1, (inner + 6) / (CARD_W + 6));
        int rows = (node.recipes.size() + columns - 1) / columns;
        recipeContent = rows * (CARD_H + 6);
        recipeScroll = Math.max(0, Math.min(Math.max(0, recipeContent - (bottom - top)), recipeScroll));
        int gap = columns > 1 ? (inner - columns * CARD_W) / (columns - 1) : 0;
        long tick = Util.getMillis() / 1000;
        ItemStack hovered = null;
        g.enableScissor(panelX + 1, top, panelX + panelW - 1, bottom);
        for (int i = 0; i < node.recipes.size(); i++) {
            KnowledgeData.Recipe recipe = node.recipes.get(i);
            int cardX = x + (i % columns) * (CARD_W + gap);
            int cardY = top + (i / columns) * (CARD_H + 6) - (int) recipeScroll;
            if (cardY > bottom || cardY + CARD_H < top) continue;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = cardX + col * SLOT;
                    int sy = cardY + row * SLOT;
                    g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0xFF1B1E33);
                    if (row >= recipe.height() || col >= recipe.width()) continue;
                    int index = row * recipe.width() + col;
                    if (index >= recipe.slots().size()) continue;
                    List<ItemStack> options = recipe.slots().get(index);
                    if (options.isEmpty()) continue;
                    ItemStack stack = options.get((int) (tick % options.size()));
                    g.item(stack, sx, sy);
                    if (inside(mouseX, mouseY, sx, sy, SLOT - 1, top, bottom)) hovered = stack;
                }
            }
            int arrowX = cardX + SLOT * 3 + 3;
            int midY = cardY + SLOT + SLOT / 2 - 4;
            g.text(font, "→", arrowX, midY, Theme.TEXT_MUTED, false);
            int resultX = arrowX + 12;
            int resultY = cardY + SLOT - 2;
            Draw.roundRect(g, resultX - 2, resultY - 2, 21, 21, 3, node.unlocked ? 0xFF2A2210 : 0xFF1B1E33);
            g.item(recipe.result(), resultX, resultY);
            g.itemDecorations(font, recipe.result(), resultX, resultY);
            if (inside(mouseX, mouseY, resultX, resultY, 16, top, bottom)) hovered = recipe.result();
        }
        g.disableScissor();
        if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int size, int top, int bottom) {
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size && mouseY >= top && mouseY < bottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int closeX = width - 16;
        if (mx >= closeX - 3 && mx < closeX + 10 && my >= 4 && my < 20) {
            onClose();
            return true;
        }
        KnowledgeData tree = EssenceClient.tree();
        if (tree != null) {
            Bounds b = bounds(tree);
            for (KnowledgeData.Node node : tree.nodes) {
                int x = cx(b, node) - NODE / 2;
                int y = cy(b, node) - NODE / 2;
                if (mx >= x - 2 && mx < x + NODE + 2 && my >= y - 2 && my < y + NODE + 2) {
                    selected = node.id;
                    recipeScroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= recipesTop) {
            recipeScroll -= scrollY * 20;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
