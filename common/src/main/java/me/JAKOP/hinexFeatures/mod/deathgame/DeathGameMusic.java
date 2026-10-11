package me.JAKOP.hinexFeatures.mod.deathgame;

import me.JAKOP.hinexFeatures.mod.HinexConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public final class DeathGameMusic extends AbstractTickableSoundInstance {

    public static final SoundEvent TRACK = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(HinexConstants.MOD_ID, "death_game"));

    private static @Nullable DeathGameMusic current;

    private float target;
    private final float fadeSpeed;

    private DeathGameMusic(float target, float fadeSpeed) {
        super(TRACK, SoundSource.MASTER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0001F;
        this.target = target;
        this.fadeSpeed = fadeSpeed;
    }

    public static void play(float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (current != null && !current.isStopped() && minecraft.getSoundManager().isActive(current)) {
            current.target = volume;
            return;
        }
        current = new DeathGameMusic(volume, 0.02F);
        minecraft.getSoundManager().play(current);
    }

    public static void volume(float volume) {
        if (current != null) current.target = volume;
    }

    public static void fadeOut() {
        if (current != null) current.target = 0.0F;
    }

    public static void stopNow() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    public static boolean playing() {
        return current != null && !current.isStopped();
    }

    @Override
    public void tick() {
        float step = fadeSpeed * (target > volume ? 1.6F : 1.0F);
        if (Math.abs(target - volume) <= step) volume = target;
        else volume += Math.signum(target - volume) * step;
        if (target <= 0.0F && volume <= 0.001F) {
            stop();
            if (current == this) current = null;
        }
        volume = Math.max(volume, 0.0001F);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
