package me.JAKOP.hinexFeatures.mod.deathgame;

import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class DeathGameScreen extends Screen {

    private final DeathGameSession session;
    private HinexButton respawn;

    public DeathGameScreen(DeathGameSession session) {
        super(Component.literal("Испытание"));
        this.session = session;
    }

    public DeathGameSession session() {
        return session;
    }

    @Override
    protected void init() {
        int buttonWidth = 170;
        int y = (int) (height * 0.72) - 4;
        respawn = addRenderableWidget(new HinexButton(width / 2 - buttonWidth / 2, y, buttonWidth, 22,
                Component.literal("Возродиться"), HinexButton.Kind.PRIMARY, button -> session.respawn()));
        updateWidgets();
    }

    private void updateWidgets() {
        if (respawn == null) return;
        DeathGameSession.Phase phase = session.phase();
        boolean result = phase == DeathGameSession.Phase.LOST || phase == DeathGameSession.Phase.WON;
        respawn.visible = result && session.allowed() && session.actionY() >= 0;
        respawn.active = session.canRespawn();
        if (session.actionY() >= 0) respawn.setY(session.actionY());
    }

    @Override
    public void tick() {
        updateWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        session.frame();
        session.draw(graphics, font, width, height);
        updateWidgets();
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int dir = switch (event.key()) {
            case InputConstants.KEY_W, InputConstants.KEY_UP -> TombLevel.UP;
            case InputConstants.KEY_D, InputConstants.KEY_RIGHT -> TombLevel.RIGHT;
            case InputConstants.KEY_S, InputConstants.KEY_DOWN -> TombLevel.DOWN;
            case InputConstants.KEY_A, InputConstants.KEY_LEFT -> TombLevel.LEFT;
            default -> -1;
        };
        if (dir >= 0) {
            session.input(dir);
            return true;
        }
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) && session.canRespawn()) {
            session.respawn();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isAllowedInPortal() {
        return true;
    }
}
