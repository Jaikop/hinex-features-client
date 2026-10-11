package me.JAKOP.hinexFeatures.mod.dialog;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class DialogView {

    public final DialogSpec spec;
    public final RichText.Parsed text;
    public final Component title;
    private double shownAt;
    private double closingAt = -1.0;
    private boolean iconResolved;
    private @Nullable Identifier iconTexture;
    private @Nullable ResolvableProfile iconProfile;
    DialogRenderer.Layout layout;
    int layoutWidth = -1;

    public DialogView(DialogSpec spec) {
        this.spec = spec;
        this.text = RichText.parse(spec.text, spec.textColor, spec.speed, spec.appear);
        String plainTitle = RichText.parse(spec.title, spec.titleColor, 1000.0F, "instant").plain;
        this.title = Component.literal(plainTitle);
        this.shownAt = VfxClock.now();
    }

    public static DialogView of(JsonObject json) {
        return new DialogView(DialogSpec.parse(json));
    }

    public void restart() {
        shownAt = VfxClock.now();
        closingAt = -1.0;
    }

    public void setAge(float age) {
        shownAt = VfxClock.now() - age;
        closingAt = -1.0;
    }

    public float age() {
        return (float) (VfxClock.now() - shownAt);
    }

    public boolean typed() {
        return age() >= text.duration;
    }

    public void close() {
        if (closingAt < 0.0) closingAt = VfxClock.now();
    }

    public boolean closing() {
        return closingAt >= 0.0;
    }

    public boolean closed() {
        return closingAt >= 0.0 && VfxClock.now() - closingAt > 0.35;
    }

    public float closeFade() {
        return closingAt < 0.0 ? 1.0F : (float) Math.max(0.0, 1.0 - (VfxClock.now() - closingAt) / 0.3);
    }

    public boolean expired() {
        return !spec.hasAnswers() && age() > text.duration + spec.readTime(text.visible);
    }

    public float remaining() {
        if (spec.hasAnswers() || !typed()) return 1.0F;
        float read = spec.readTime(text.visible);
        return Math.max(0.0F, 1.0F - (age() - text.duration) / read);
    }

    public boolean hasIcon() {
        resolveIcon();
        return iconTexture != null || iconProfile != null;
    }

    public @Nullable Identifier iconTexture() {
        resolveIcon();
        return iconTexture;
    }

    public @Nullable ResolvableProfile iconProfile() {
        resolveIcon();
        return iconProfile;
    }

    private void resolveIcon() {
        if (iconResolved) return;
        iconResolved = true;
        String icon = spec.icon;
        if (icon.isEmpty()) return;
        String lower = icon.toLowerCase(Locale.ROOT);
        if (lower.startsWith("head:") || lower.startsWith("player:")) {
            String name = icon.substring(icon.indexOf(':') + 1).strip();
            if (!name.isEmpty()) iconProfile = ResolvableProfile.createUnresolved(name);
            return;
        }
        Identifier id;
        String path = lower.endsWith(".png") ? lower : lower + ".png";
        if (lower.contains(":")) {
            id = Identifier.tryParse(path);
        } else {
            id = Identifier.tryBuild("icons", path);
        }
        if (id == null) return;
        if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) iconTexture = id;
        else iconTexture = DialogRenderer.MISSING_ICON;
    }
}
