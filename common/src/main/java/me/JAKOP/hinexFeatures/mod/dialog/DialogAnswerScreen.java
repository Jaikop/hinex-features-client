package me.JAKOP.hinexFeatures.mod.dialog;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.Locale;

public final class DialogAnswerScreen extends Screen {

    private final DialogView view;
    private EditBox input;
    private int hover;
    private boolean done;

    public DialogAnswerScreen(DialogView view) {
        super(Component.literal("Диалог"));
        this.view = view;
    }

    public DialogView view() {
        return view;
    }

    private DialogClient.Placement placement() {
        return DialogClient.placement(view, width, height);
    }

    @Override
    protected void init() {
        hover = view.spec.answerType.equals("choice") ? 0 : -1;
        if (view.spec.answerType.equals("text")) {
            int[] field = fieldRect();
            input = new EditBox(font, field[0] + 4, field[1] + (field[3] - 8) / 2, Math.max(20, field[2] - 8), 10, Component.literal("Ответ"));
            input.setBordered(false);
            input.setMaxLength(view.spec.maxLength);
            input.setTextColor(ARGB.opaque(view.spec.inputColor));
            input.setHint(Component.literal(view.spec.placeholder.isEmpty() ? "Ваш ответ…" : view.spec.placeholder)
                    .withColor(ARGB.scaleRGB(view.spec.inputColor | 0xFF000000, 0.6F) & 0xFFFFFF));
            addRenderableWidget(input);
            setInitialFocus(input);
        }
    }

    private int[] fieldRect() {
        DialogClient.Placement placement = placement();
        int[] field = placement.layout().input;
        int x = (int) placement.screenX(field[0]);
        int y = (int) placement.screenY(field[1]);
        return new int[]{x, y, Math.round(field[2] * placement.scale()), Math.round(field[3] * placement.scale())};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, height / 2, width, height, 0x00000000, 0x70000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int optionHover = optionAt(mouseX, mouseY);
        if (optionHover >= 0) hover = optionHover;
        DialogClient.Placement placement = DialogClient.draw(graphics, view, DialogRenderer.Mode.ANSWER, hover);

        if (input != null) {
            int[] field = fieldRect();
            DialogRenderer.inputField(graphics, view.spec, field[0], field[1], field[2], field[3], 1.0F);
        }
        drawHint(graphics, placement);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawHint(GuiGraphicsExtractor graphics, DialogClient.Placement placement) {
        String hint = "";
        if (view.spec.hint) {
            hint = !view.spec.hintText.isEmpty() ? view.spec.hintText
                    : view.spec.answerType.equals("choice") ? "1–" + view.spec.options.size() + " или клик · Esc — без ответа"
                    : "Enter — отправить · Esc — без ответа";
        }
        if (view.spec.timeout > 0.0F) {
            float left = view.text.duration + view.spec.timeout - view.age();
            String timer = String.format(Locale.ROOT, "%d с", (int) Math.ceil(Math.max(0.0F, left)));
            hint = hint.isEmpty() ? timer : hint + " · " + timer;
        }
        if (hint.isEmpty()) return;
        float scale = 0.75F;
        int bottom = (int) placement.screenY(placement.layout().height);
        boolean above = view.spec.position.equals("bottom") || bottom + 12 >= height;
        float hintY = above ? placement.y() - 10 : bottom + 3;
        float hintWidth = font.width(hint) * scale;
        graphics.fill((int) (width / 2.0F - hintWidth / 2 - 3), (int) hintY - 2, (int) (width / 2.0F + hintWidth / 2 + 3),
                (int) (hintY + 8), 0x90000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, hintY);
        graphics.pose().scale(scale, scale);
        graphics.centeredText(font, hint, 0, 0, 0xFFD0C0B0);
        graphics.pose().popMatrix();
    }

    private int optionAt(double mouseX, double mouseY) {
        if (!view.spec.answerType.equals("choice")) return -1;
        DialogClient.Placement placement = placement();
        DialogRenderer.Layout layout = placement.layout();
        for (int i = 0; i < layout.options.size(); i++) {
            int[] rect = layout.options.get(i);
            double x0 = placement.screenX(rect[0]);
            double y0 = placement.screenY(layout.tabHeight + rect[1]);
            double x1 = placement.screenX(rect[0] + rect[2]);
            double y1 = placement.screenY(layout.tabHeight + rect[1] + rect[3]);
            if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int option = optionAt(event.x(), event.y());
        if (option >= 0 && view.typed()) {
            choose(option);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (view.spec.answerType.equals("choice")) {
            int count = view.spec.options.size();
            if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + count) {
                choose(key - InputConstants.KEY_1);
                return true;
            }
            if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
                hover = (hover - 1 + count) % count;
                return true;
            }
            if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) {
                hover = (hover + 1) % count;
                return true;
            }
            if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) && hover >= 0) {
                choose(hover);
                return true;
            }
        } else if (input != null && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            String text = input.getValue().strip();
            if (!text.isEmpty()) {
                done = true;
                DialogClient.answer(view, -1, text, null);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(int option) {
        if (done) return;
        done = true;
        DialogClient.answer(view, option, null, null);
    }

    @Override
    public void onClose() {
        if (!done) {
            done = true;
            DialogClient.answer(view, -1, null, "dismissed");
        }
        closeSilently();
    }

    public void closeSilently() {
        done = true;
        if (minecraft != null && minecraft.gui.screen() == this) minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
