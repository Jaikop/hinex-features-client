package me.JAKOP.hinexFeatures.mod.mixin.dialog;

import me.JAKOP.hinexFeatures.mod.admin.AdminButton;
import me.JAKOP.hinexFeatures.mod.admin.AdminKeys;
import me.JAKOP.hinexFeatures.mod.dialog.HistoryButton;
import me.JAKOP.hinexFeatures.mod.essence.EssenceClient;
import me.JAKOP.hinexFeatures.mod.essence.KnowledgeButton;
import me.JAKOP.hinexFeatures.mod.plus.PlusButton;
import me.JAKOP.hinexFeatures.mod.plus.PlusClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Shadow
    public abstract boolean showsPauseMenu();

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hinex$historyButton(CallbackInfo ci) {
        if (!showsPauseMenu()) return;
        for (GuiEventListener child : children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents key
                    && key.getKey().equals("menu.returnToGame")) {
                addRenderableWidget(new HistoryButton(button.getX() - 24, button.getY(), this));
                if (AdminKeys.allowed()) addRenderableWidget(new AdminButton(button.getX() - 48, button.getY()));
                int right = button.getX() + button.getWidth() + 4;
                if (PlusClient.available()) {
                    addRenderableWidget(new PlusButton(right, button.getY(), this));
                    right += 24;
                }
                if (EssenceClient.available()) addRenderableWidget(new KnowledgeButton(right, button.getY(), this));
                return;
            }
        }
    }
}
