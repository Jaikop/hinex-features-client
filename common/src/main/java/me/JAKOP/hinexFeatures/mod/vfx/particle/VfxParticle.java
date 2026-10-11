package me.JAKOP.hinexFeatures.mod.vfx.particle;

import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;

public final class VfxParticle {

    public double x, y, z;
    public float vx, vy, vz;
    public float age;
    public float life = 1.0F;
    public float size = 0.1F;
    public float endSize = -1.0F;
    public int color = 0xFFFFFFFF;
    public int endColor = 0;
    public int shape = VfxShape.GLOW;
    public int layer = VfxLayer.GLOW;
    public int seed;
    public float gravity;
    public float drag;
    public float rotation;
    public float spin;
    public float stretch;
    public float flicker;
    public double attractX, attractY, attractZ;
    public float attract;
    public float swirl;
    public double floorY = Double.NaN;
    public float fadeIn = 0.1F;
    public float fadeOut = 0.35F;
    public boolean flat;
    public Object owner;

    public VfxParticle flat() {
        this.flat = true;
        return this;
    }

    public VfxParticle at(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public VfxParticle velocity(double vx, double vy, double vz) {
        this.vx = (float) vx;
        this.vy = (float) vy;
        this.vz = (float) vz;
        return this;
    }

    public VfxParticle life(float seconds) {
        this.life = Math.max(0.05F, seconds);
        return this;
    }

    public VfxParticle size(float start, float end) {
        this.size = start;
        this.endSize = end;
        return this;
    }

    public VfxParticle color(int start, int end) {
        this.color = start;
        this.endColor = end;
        return this;
    }

    public VfxParticle look(int shape, int layer) {
        this.shape = shape;
        this.layer = layer;
        return this;
    }

    public VfxParticle physics(float gravity, float drag) {
        this.gravity = gravity;
        this.drag = drag;
        return this;
    }

    public VfxParticle spin(float rotation, float spin) {
        this.rotation = rotation;
        this.spin = spin;
        return this;
    }

    public VfxParticle stretch(float stretch) {
        this.stretch = stretch;
        return this;
    }

    public VfxParticle flicker(float flicker) {
        this.flicker = flicker;
        return this;
    }

    public VfxParticle attract(double x, double y, double z, float strength, float swirl) {
        this.attractX = x;
        this.attractY = y;
        this.attractZ = z;
        this.attract = strength;
        this.swirl = swirl;
        return this;
    }

    public VfxParticle floor(double floorY) {
        this.floorY = floorY;
        return this;
    }

    public VfxParticle fade(float in, float out) {
        this.fadeIn = in;
        this.fadeOut = out;
        return this;
    }
}
