#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <hinexfeatures:noise.glsl>
#include <hinexfeatures:dissolve_params.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 texCoord0;
layout(location = 5) in vec3 localPos;
layout(location = 6) in vec3 worldNormal;
layout(location = 7) in vec3 relPos;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    vec3 base = tex.rgb * vertexColor.rgb * ColorModulator.rgb;
    vec3 lit = base * lightMapColor.rgb;
    vec3 emission = vec3(0.0);

    float height = max(DS_HEIGHT, 0.05);
    float h = clamp(localPos.y / height, 0.0, 1.0);
    float p = clamp(DS_PROGRESS, 0.0, 1.0);
    float time = DS_TIME;
    float seed = DS_SEED;
    vec3 c1 = DS_COLOR;
    vec3 c2 = DS_COLOR2;
    float glow = DS_GLOW;
    int style = DS_STYLE;

    vec3 viewDir = normalize(-relPos);
    float facing = clamp(abs(dot(normalize(worldNormal), viewDir)), 0.0, 1.0);
    float fresnel = pow(1.0 - facing, 2.0);
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float dither = hx_bayer4(gl_FragCoord.xy);

    if (style == STYLE_ASH) {
        // Испепеление: фронт сгорания идёт сверху вниз по шуму, за ним — угли и пепел
        float n = hx_fbm3(localPos * 3.6 + seed);
        float field = mix(n, 1.0 - h, 0.58);
        float width = 0.07;
        float threshold = p * (1.0 + width * 3.0) - width;
        if (field < threshold) {
            discard;
        }
        float dist = field - threshold;
        float edge = 1.0 - smoothstep(0.0, width, dist);
        float charred = 1.0 - smoothstep(0.0, width * 4.0, dist);
        lit = mix(lit, lit * 0.25, smoothstep(0.0, 0.5, p) * 0.6);
        lit = mix(lit, vec3(0.03, 0.025, 0.02), charred * 0.92);
        float flicker = 0.75 + 0.25 * hx_noise3(localPos * 18.0 + time * 6.0);
        vec3 ember = mix(c2, c1, edge);
        emission += ember * pow(edge, 1.4) * 3.2 * glow * flicker;
        // Тлеющие прожилки в обугленной зоне
        float veins = smoothstep(0.62, 0.7, hx_noise3(localPos * 14.0 + seed * 3.0));
        emission += c2 * veins * charred * (1.0 - edge) * 0.9 * glow * flicker;
    } else if (style == STYLE_HOLOGRAM) {
        // Голограмма: бирюзовые строки развёртки, мерцание, «срывы», затем схлопывание в линию
        float appear = smoothstep(0.0, 0.22, p);
        float scan = 0.6 + 0.4 * sin(localPos.y * 150.0 - time * 14.0);
        float flicker = 0.82 + 0.18 * sin(time * 41.0) * sin(time * 13.0 + seed);
        // Насыщенный бирюзовый с яркой каймой по контуру и светлой бегущей полосой развёртки
        vec3 holo = c1 * (0.12 + luma * 0.55) * scan + c1 * fresnel * 1.1 + c2 * pow(fresnel, 4.0) * 0.9;
        lit = mix(lit, lit * 0.15, appear);
        emission += holo * flicker * appear * glow;
        float sweep = 1.0 - smoothstep(0.0, 0.03, abs(fract(localPos.y * 0.9 - time * 0.7) - 0.5));
        emission += mix(c1, vec3(1.0), 0.4) * sweep * 0.7 * appear;
        if (fract(localPos.y * 55.0 - time * 3.0) < 0.28 * appear) {
            discard;
        }
        float alpha = mix(1.0, 0.62, appear) * (1.0 - smoothstep(0.86, 1.0, p));
        if (dither > alpha) {
            discard;
        }
    } else if (style == STYLE_GLOW) {
        // Свечение → вспышка → тёмный силуэт, который рассыпается в дым
        float charge = smoothstep(0.0, 0.42, p);
        float dark = smoothstep(0.42, 0.54, p);
        float n = hx_fbm3(localPos * 4.2 + seed);
        float threshold = smoothstep(0.54, 1.0, p) * 1.12 - 0.06;
        if (n < threshold) {
            discard;
        }
        float edge = 1.0 - smoothstep(0.0, 0.07, n - threshold);
        vec3 radiant = mix(lit, c1 * 1.4 + vec3(0.35), charge);
        vec3 silhouette = vec3(0.015, 0.0, 0.03) + c2 * fresnel * 0.5;
        lit = mix(radiant, silhouette, dark);
        emission += (c1 * 1.3 + vec3(0.5)) * charge * (1.0 - dark) * (0.5 + fresnel) * glow;
        emission += c2 * edge * 2.4 * dark * glow;
    } else if (style == STYLE_GLITCH) {
        // Цифровой распад: модель рассыпается на кубики, каналы цвета сдвигаются
        vec3 cell = floor(localPos * 10.0);
        float tick = floor(time * 12.0);
        float r = hx_hash31(cell + seed);
        float threshold = p * 1.15 - 0.05;
        if (r < threshold) {
            discard;
        }
        if (hx_hash31(cell + tick * 1.37) > 0.93 - p * 0.25 && p > 0.03) {
            discard;
        }
        float k = hx_hash31(cell * 1.7 + tick);
        if (k > 0.78) {
            lit *= vec3(1.8, 0.25, 0.35);
        } else if (k > 0.58) {
            lit *= vec3(0.25, 1.4, 1.8);
        }
        float edge = 1.0 - smoothstep(0.0, 0.09, r - threshold);
        emission += mix(c1, c2, k) * edge * 1.8 * glow * step(0.001, p);
        emission += c1 * step(0.5, fract(localPos.y * 40.0 + tick * 0.5)) * 0.15 * p;
    } else if (style == STYLE_VOID) {
        // Бездна: модель темнеет, фиолетовый фронт идёт снизу вверх, её затягивает в точку
        float n = hx_fbm3(localPos * 3.2 + seed);
        float field = mix(n, h, 0.55);
        float threshold = p * 1.16 - 0.08;
        if (field < threshold) {
            discard;
        }
        float edge = 1.0 - smoothstep(0.0, 0.08, field - threshold);
        lit = mix(lit, lit * vec3(0.22, 0.1, 0.38), smoothstep(0.0, 0.45, p));
        emission += mix(c2, c1, edge) * pow(edge, 1.2) * 3.0 * glow;
        emission += c1 * fresnel * smoothstep(0.0, 0.5, p) * 0.9 * glow;
    } else if (style == STYLE_FROST) {
        // Лёд: модель покрывается инеем и трещинами, затем раскалывается
        float freeze = smoothstep(0.0, 0.45, p);
        float frostField = hx_fbm3(localPos * 5.0 + seed) - (1.0 - h) * 0.25;
        float frozen = smoothstep(0.0, 0.2, freeze * 1.3 - frostField);
        vec3 ice = mix(c2, c1, luma) * (0.55 + fresnel * 1.2);
        lit = mix(lit, ice * lightMapColor.rgb + c1 * 0.15, frozen);
        float cracks = 1.0 - smoothstep(0.0, 0.035, abs(hx_noise3(localPos * 9.0 + seed) - 0.5));
        emission += c1 * cracks * frozen * 1.4 * glow;
        emission += vec3(0.8, 0.95, 1.0) * pow(fresnel, 3.0) * frozen * 0.8;
        vec3 shard = floor(localPos * 6.0 + hx_noise3(localPos * 3.0) * 1.5);
        float shatter = smoothstep(0.5, 1.0, p);
        if (hx_hash31(shard + seed) < shatter * 1.1 - 0.05) {
            discard;
        }
    } else if (style == STYLE_VANISHED) {
        // «Призрак»: так администратор видит игроков в ванише
        float wave = 0.5 + 0.5 * sin(localPos.y * 22.0 - time * 3.0);
        lit = mix(lit, c1 * (0.3 + luma * 0.8), 0.45);
        emission += c1 * fresnel * 1.1 * glow + c2 * wave * fresnel * 0.4;
        if (dither > 0.58 + 0.12 * wave) {
            discard;
        }
    } else {
        // Стандарт: мягкое растворение с белой вспышкой
        float alpha = 1.0 - p;
        float whiten = smoothstep(0.0, 0.3, p) * (1.0 - smoothstep(0.55, 1.0, p));
        emission += (c1 + vec3(0.4)) * whiten * 0.9 * glow;
        if (dither > alpha) {
            discard;
        }
    }

    vec4 color = vec4(lit + emission, 1.0);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
