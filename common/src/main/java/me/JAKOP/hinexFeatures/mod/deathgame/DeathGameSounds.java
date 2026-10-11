package me.JAKOP.hinexFeatures.mod.deathgame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class DeathGameSounds {

    private DeathGameSounds() {
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void impact() {
        play(SoundEvents.BONE_BLOCK_HIT, 0.8F + (float) Math.random() * 0.3F, 0.35F);
    }

    static void dot(int collected) {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F + (collected % 5) * 0.08F, 0.18F);
    }

    static void countdown(boolean last) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, last ? 1.6F : 1.0F));
    }

    static void death() {
        play(SoundEvents.SKELETON_DEATH, 0.7F, 0.5F);
    }

    static void win() {
        play(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F);
    }

    static void allowed() {
        play(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.4F);
    }
}
