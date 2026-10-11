#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <hinexfeatures:noise.glsl>

// Формы — те же номера, что в VfxShape.java
#define SHAPE_GLOW 0
#define SHAPE_SQUARE 1
#define SHAPE_SPARK 2
#define SHAPE_RING 3
#define SHAPE_STAR 4
#define SHAPE_BEAM 5
#define SHAPE_RUNES 6
#define SHAPE_SMOKE 7
#define SHAPE_FLAKE 8
#define SHAPE_FRAME 9
#define SHAPE_FLAT 10
#define SHAPE_HOLE 11
#define SHAPE_SWIRL 12
#define SHAPE_BOLT 13
#define SHAPE_WAVE 14
#define SHAPE_DOT 15
#define SHAPE_SHARD 16
#define SHAPE_GRID 17
#define SHAPE_PORTAL 18

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec3 texCoord0;
layout(location = 4) flat in int shape;
layout(location = 5) flat in float seed;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;

void main() {
    float time = TextureMat[0].x;
    vec2 uv = texCoord0.xy;
    vec2 c = uv * 2.0 - 1.0;
    float r = length(c);
    float angle = atan(c.y, c.x);
    vec3 color = vertexColor.rgb;
    float alpha = 0.0;

    if (shape == SHAPE_GLOW) {
        float g = max(0.0, 1.0 - r);
        alpha = g * g * 0.65 + pow(g, 7.0) * 0.9;
        color = mix(color, vec3(1.0), pow(g, 10.0) * 0.55);
    } else if (shape == SHAPE_SQUARE) {
        vec2 d = abs(c);
        float m = max(d.x, d.y);
        alpha = 1.0 - smoothstep(0.72, 1.0, m);
        color = mix(color, vec3(1.0), (1.0 - smoothstep(0.0, 0.5, m)) * 0.35);
    } else if (shape == SHAPE_SPARK) {
        float across = max(0.0, 1.0 - abs(c.x));
        float along = smoothstep(0.0, 0.3, uv.y) * (1.0 - smoothstep(0.75, 1.0, uv.y));
        alpha = pow(across, 2.6) * along;
        color = mix(color, vec3(1.0), pow(across, 9.0) * 0.75);
    } else if (shape == SHAPE_RING) {
        float d = abs(r - 0.84);
        alpha = pow(1.0 - smoothstep(0.0, 0.14, d), 2.0) + (1.0 - smoothstep(0.0, 0.03, d)) * 0.6;
    } else if (shape == SHAPE_STAR) {
        float rays = max(0.0, 1.0 - abs(c.x) * 9.0 - abs(c.y) * 0.85) + max(0.0, 1.0 - abs(c.y) * 9.0 - abs(c.x) * 0.85);
        float core = max(0.0, 1.0 - r * 2.4);
        alpha = clamp(rays * 0.85 + core * core * 1.3 + max(0.0, 1.0 - r) * 0.15, 0.0, 1.0) * (1.0 - smoothstep(0.85, 1.0, r));
        color = mix(color, vec3(1.0), core);
    } else if (shape == SHAPE_BEAM) {
        // u — поперёк, texCoord0.z — вдоль луча в блоках
        float along = texCoord0.z;
        float across = max(0.0, 1.0 - abs(c.x));
        float core = pow(across, 7.0);
        float body = pow(across, 1.7);
        float bands = 0.55 + 0.45 * hx_noise2(vec2(c.x * 2.5, along * 0.45 - time * 5.0));
        float sparkle = step(0.93, hx_noise2(vec2(c.x * 12.0, along * 3.0 - time * 12.0)));
        alpha = body * 0.55 * bands + core * 0.95 + sparkle * body * 0.4;
        color = mix(color, vec3(1.0), core * 0.85);
    } else if (shape == SHAPE_RUNES) {
        // Магический круг: кольца, руны, гексаграмма
        float spin = time * 0.35;
        float a = angle + spin;
        float ring1 = 1.0 - smoothstep(0.0, 0.018, abs(r - 0.96));
        float ring2 = 1.0 - smoothstep(0.0, 0.014, abs(r - 0.80));
        float ring3 = 1.0 - smoothstep(0.0, 0.012, abs(r - 0.43));
        float cells = (a + PI) / TAU * 36.0;
        float cellId = floor(cells);
        float band = step(0.83, r) * step(r, 0.935);
        float rune = band * step(0.42, hx_hash11(cellId + floor(seed))) * step(0.18, fract(cells)) * step(fract(cells), 0.82);
        rune *= step(0.35, fract(r * 38.0 + hx_hash11(cellId) * 2.0));
        float star = 0.0;
        for (int i = 0; i < 6; i++) {
            float th = float(i) * 1.0471976 + 0.5236 - spin * 1.7;
            vec2 dir = vec2(cos(th), sin(th));
            star = max(star, 1.0 - smoothstep(0.0, 0.012, abs(dot(c, dir) - 0.40)));
        }
        star *= step(r, 0.80);
        float spokes = (1.0 - smoothstep(0.0, 0.012, abs(sin((angle - spin * 0.6) * 12.0)) * r)) * step(0.43, r) * step(r, 0.8) * 0.45;
        float pulse = 0.8 + 0.2 * sin(time * 3.0 + r * 10.0);
        alpha = clamp(ring1 + ring2 + ring3 + rune * 0.95 + star * 0.9 + spokes, 0.0, 1.0) * step(r, 1.0) * pulse;
        alpha += (1.0 - smoothstep(0.0, 1.0, r)) * 0.07;
    } else if (shape == SHAPE_SMOKE) {
        float n = hx_fbm2(c * 1.7 + seed * 13.1 + vec2(0.0, time * 0.15));
        alpha = smoothstep(0.32, 0.72, n + (1.0 - r) * 0.65 - 0.38) * (1.0 - smoothstep(0.65, 1.0, r));
    } else if (shape == SHAPE_FLAKE) {
        float edge = 0.55 + 0.38 * hx_noise2(vec2(angle * 1.6 + seed * 3.7, seed));
        alpha = 1.0 - smoothstep(edge - 0.12, edge, r);
    } else if (shape == SHAPE_FRAME) {
        vec2 d = min(uv, 1.0 - uv);
        float e = min(d.x, d.y);
        alpha = (1.0 - smoothstep(0.0, 0.07, e)) + (1.0 - smoothstep(0.0, 0.4, e)) * 0.22 + 0.05;
        color = mix(color, vec3(1.0), 1.0 - smoothstep(0.0, 0.025, e));
    } else if (shape == SHAPE_FLAT) {
        alpha = 1.0;
    } else if (shape == SHAPE_HOLE) {
        // Горизонт событий: абсолютно чёрный диск и тёмное гало
        float core = 1.0 - smoothstep(0.56, 0.6, r);
        alpha = core + (1.0 - smoothstep(0.58, 1.0, r)) * 0.55 * (1.0 - core);
        color = vec3(0.0);
    } else if (shape == SHAPE_SWIRL) {
        // Аккреционный диск
        float n = hx_noise2(vec2(angle * 2.0 + r * 7.0 - time * 3.0, r * 5.0 - time * 0.6));
        float disk = smoothstep(0.3, 0.46, r) * (1.0 - smoothstep(0.7, 1.0, r));
        alpha = disk * (0.5 + 0.9 * n * n);
        color = mix(color, vec3(1.0, 0.95, 0.85), pow(n, 3.0) * disk * 0.8 + (1.0 - smoothstep(0.0, 0.08, abs(r - 0.52))) * 0.6);
    } else if (shape == SHAPE_BOLT) {
        float across = max(0.0, 1.0 - abs(c.x));
        alpha = pow(across, 3.0) * 0.8 + pow(across, 14.0);
        color = mix(color, vec3(1.0), pow(across, 10.0));
    } else if (shape == SHAPE_WAVE) {
        float ring = smoothstep(0.5, 0.93, r) * (1.0 - smoothstep(0.93, 1.0, r));
        alpha = ring * ring;
        color = mix(color, vec3(1.0), pow(ring, 6.0) * 0.6);
    } else if (shape == SHAPE_DOT) {
        alpha = (1.0 - smoothstep(0.25, 0.45, r)) + max(0.0, 1.0 - r) * 0.35;
        color = mix(color, vec3(1.0), 1.0 - smoothstep(0.0, 0.3, r));
    } else if (shape == SHAPE_SHARD) {
        // Осколок льда: вытянутый ромб с бликом
        float d = abs(c.x) * 1.8 + abs(c.y);
        alpha = 1.0 - smoothstep(0.85, 1.0, d);
        color = mix(color, vec3(1.0), (1.0 - smoothstep(0.0, 0.25, abs(c.x + c.y * 0.3))) * 0.6);
    } else if (shape == SHAPE_GRID) {
        // Кольцо проектора голограммы: концентрические линии и деления
        float lines = 1.0 - smoothstep(0.0, 0.03, abs(fract(r * 6.0 - time * 0.8) - 0.5) - 0.44);
        float ticks = step(0.9, r) * step(r, 0.98) * step(0.5, fract((angle + time * 0.4) / TAU * 48.0));
        float edge = 1.0 - smoothstep(0.0, 0.02, abs(r - 0.98));
        alpha = (lines * 0.35 + ticks * 0.8 + edge) * step(r, 1.0) * (0.4 + 0.6 * r);
    } else if (shape == SHAPE_PORTAL) {
        // Вертикальный портал: закрученный вихрь в эллипсе
        vec2 e = vec2(c.x * 1.6, c.y);
        float er = length(e);
        float ea = atan(e.y, e.x);
        float swirl = hx_fbm2(vec2(ea * 1.5 + er * 4.0 - time * 2.2, er * 3.0 - time * 0.5) + seed);
        float body = 1.0 - smoothstep(0.75, 1.0, er);
        float rim = 1.0 - smoothstep(0.0, 0.08, abs(er - 0.9));
        alpha = body * (0.25 + swirl * 0.9) + rim * 0.9;
        color = mix(color, vec3(1.0), rim * 0.5 + pow(swirl, 4.0) * body * 0.4);
    }

    alpha *= vertexColor.a;
    if (alpha <= 0.003) {
        discard;
    }

    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    vec4 result = vec4(color, clamp(alpha, 0.0, 1.0) * (1.0 - fog)) * ColorModulator;
#ifdef HINEX_GLOW
    // Предумноженная альфа: цвет прибавляется, фон под эффектом слегка приглушается —
    // светится в темноте и не выцветает до белого на ярком дне
    fragColor = vec4(result.rgb * result.a, result.a * 0.7);
#else
    fragColor = result;
#endif
}
