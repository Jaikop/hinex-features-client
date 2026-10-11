package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import me.JAKOP.hinexFeatures.mod.admin.MacroRunner;
import me.JAKOP.hinexFeatures.mod.border.BorderFog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.injection.Redirect;
import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import me.JAKOP.hinexFeatures.mod.essence.EssenceHud;
import me.JAKOP.hinexFeatures.mod.vanish.VanishHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    public abstract boolean isHidden();

    @Redirect(method = "extractVignette", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D"))
    private double hinex$borderWarning(WorldBorder border, Entity entity) {
        return BorderFog.hidesWall() ? Double.MAX_VALUE : border.getDistanceToBorder(entity);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hinex$vanishBadge(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (isHidden()) return;
        VanishHud.extract(graphics);
        DialogClient.extractHud(graphics);
        MacroRunner.extractHud(graphics);
        EssenceHud.extract(graphics);
    }
}
