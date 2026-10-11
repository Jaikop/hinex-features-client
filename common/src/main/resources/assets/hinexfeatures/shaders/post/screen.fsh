#version 330
#extension GL_ARB_separate_shader_objects : require

#include <hinexfeatures:noise.glsl>

// Экранные эффекты HinexFeatures. Один файл — много эффектов: мод собирает отдельный
// конвейер на каждый эффект с нужным #define (EFFECT_GLITCH, EFFECT_VHS, ...).

uniform sampler2D InSampler;

layout(std140) uniform HinexScreen {
    vec4 Params;   // сила 0..1, время (с), прогресс жизни эффекта 0..1, зерно
    vec4 Tint;     // цвет эффекта (rgb), доп. параметр
    vec4 Point;    // точка на экране (0..1), радиус (доля высоты экрана), сила искажения
    vec4 Screen;   // ширина, высота, соотношение сторон, скорость
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

#define INTENSITY (Params.x)
#define TIME (Params.y)
#define LIFE (Params.z)
#define SEED (Params.w)

vec3 sampleScene(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
}

float vignette(vec2 uv, float strength, float softness) {
    vec2 d = (uv - 0.5) * vec2(Screen.z, 1.0);
    return 1.0 - smoothstep(softness, softness + strength, length(d));
}

float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = texCoord;
    vec3 scene = sampleScene(uv);
    vec3 color = scene;
    float I = INTENSITY;
    float t = TIME;

#if defined(EFFECT_GLITCH)
    float tick = floor(t * 18.0 + SEED);
    float row = floor(uv.y * 26.0);
    if (hx_hash21(vec2(row, tick)) > 1.0 - 0.2 * I) {
        uv.x += (hx_hash21(vec2(tick, row)) - 0.5) * 0.16 * I;
    }
    vec2 blockId = floor(uv * vec2(9.0, 14.0));
    float block = step(1.0 - 0.07 * I, hx_hash21(blockId + tick));
    uv += block * (vec2(hx_hash21(vec2(tick, 3.0)), hx_hash21(vec2(tick, 7.0))) - 0.5) * 0.09 * I;
    float split = (0.003 + 0.014 * hx_hash11(tick)) * I;
    color.r = sampleScene(uv + vec2(split, 0.0)).r;
    color.g = sampleScene(uv).g;
    color.b = sampleScene(uv - vec2(split, split * 0.4)).b;
    color += (hx_hash21(uv * Screen.xy + t * 61.0) - 0.5) * 0.14 * I;
    if (block > 0.5 && hx_hash11(tick * 1.31) > 0.55) {
        color = 1.0 - color;
    }
    color *= 1.0 - 0.1 * I * step(0.5, fract(uv.y * Screen.y * 0.25));
#elif defined(EFFECT_VHS)
    float jitter = (hx_noise2(vec2(t * 8.0, uv.y * 40.0)) - 0.5) * 0.004 * I;
    float tracking = smoothstep(0.0, 0.03, abs(fract(uv.y - t * 0.13) - 0.5) - 0.44);
    uv.x += jitter + (1.0 - tracking) * 0.02 * I * (hx_hash21(vec2(floor(uv.y * 200.0), floor(t * 30.0))) - 0.5);
    float bleed = 0.0045 * I;
    vec3 blur = vec3(0.0);
    for (int i = -2; i <= 2; i++) {
        blur += sampleScene(uv + vec2(float(i) * 0.0016, 0.0));
    }
    blur /= 5.0;
    color = vec3(sampleScene(uv + vec2(bleed, 0.0)).r, blur.g, sampleScene(uv - vec2(bleed, 0.0)).b);
    color = mix(color, vec3(luminance(color)), 0.25 * I);
    color *= mix(vec3(1.0), vec3(1.05, 0.97, 0.9), I);
    color += (hx_hash21(uv * Screen.xy * 0.5 + t * 45.0) - 0.5) * 0.12 * I;
    color += (1.0 - tracking) * 0.18 * I;
    color *= 1.0 - 0.12 * I * (0.5 + 0.5 * sin(uv.y * Screen.y * 1.4));
    color *= mix(1.0, vignette(uv, 0.55, 0.35), I);
#elif defined(EFFECT_LENS)
    vec2 centered = (uv - 0.5) * vec2(Screen.z, 1.0);
    float r2 = dot(centered, centered);
    float k = 0.35 * I;
    vec2 base = centered * (1.0 - k * r2 * 0.8);
    vec2 toUv = vec2(1.0 / Screen.z, 1.0);
    float aberration = 0.012 * I;
    color.r = sampleScene(0.5 + base * (1.0 + aberration) * toUv).r;
    color.g = sampleScene(0.5 + base * toUv).g;
    color.b = sampleScene(0.5 + base * (1.0 - aberration) * toUv).b;
    color *= mix(1.0, vignette(uv, 0.6, 0.3), I);
    color += Tint.rgb * pow(max(0.0, 1.0 - length(centered - vec2(0.25, -0.2)) * 3.0), 3.0) * 0.25 * I;
#elif defined(EFFECT_HALLUCINATION)
    vec2 wobble = vec2(sin(uv.y * 9.0 + t * 1.9), cos(uv.x * 7.0 + t * 1.5)) * 0.018 * I;
    wobble += vec2(hx_noise2(uv * 3.0 + t * 0.4), hx_noise2(uv * 3.0 - t * 0.35)) * 0.02 * I;
    vec3 main = sampleScene(uv + wobble);
    vec3 ghost = sampleScene(uv - wobble * 2.5 + vec2(sin(t * 0.7), cos(t * 0.9)) * 0.025 * I);
    color = mix(main, ghost, 0.4 * I);
    color = mix(color, hx_hue_rotate(color, t * 1.3 + uv.x * 2.0 + uv.y), 0.75 * I);
    float sat = luminance(color);
    color = mix(vec3(sat), color, 1.0 + 0.8 * I);
    color += 0.08 * I * vec3(sin(t * 2.0 + uv.x * 6.0), sin(t * 2.3 + uv.y * 6.0 + 2.0), sin(t * 1.7 + 4.0)) * vignette(uv, -0.3, 0.6);
#elif defined(EFFECT_DRUNK)
    vec2 sway = vec2(sin(t * 0.9), cos(t * 0.7)) * 0.008 * I;
    vec2 twin = vec2(0.018 * sin(t * 0.8), 0.007 * cos(t * 1.1)) * I;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        float f = float(i) / 4.0 - 0.5;
        acc += sampleScene(uv + sway + vec2(f * 0.006 * I, sin(f + t) * 0.002 * I));
    }
    acc /= 5.0;
    color = mix(acc, sampleScene(uv + sway + twin), 0.45 * I);
    color *= mix(1.0, vignette(uv, 0.7, 0.25), I * 0.8);
#elif defined(EFFECT_FLASH)
    float bright = I;
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        bloom += sampleScene(uv + vec2(cos(a), sin(a)) * 0.012 * bright);
    }
    bloom /= 8.0;
    color = mix(scene, bloom, bright * 0.6) * (1.0 + bright * 2.5);
    color = mix(color, Tint.rgb, clamp(bright * 1.1 - 0.1, 0.0, 1.0));
#elif defined(EFFECT_NOIR)
    float l = luminance(scene);
    l = smoothstep(0.05, 0.95, l);
    l = pow(l, 1.15);
    color = vec3(l) * vec3(1.02, 1.0, 0.95);
    color += (hx_hash21(uv * Screen.xy + fract(t * 23.0) * 100.0) - 0.5) * 0.12;
    float scratch = step(0.997, hx_hash21(vec2(floor(uv.x * Screen.x * 0.5), floor(t * 12.0))));
    color += scratch * 0.25;
    color *= vignette(uv, 0.6, 0.25);
    color = mix(scene, color, I);
#elif defined(EFFECT_NIGHTVISION)
    vec3 boosted = pow(scene, vec3(0.55)) * 1.3;
    float l = luminance(boosted);
    color = vec3(0.1, 1.0, 0.25) * l + vec3(0.02, 0.06, 0.02);
    color += (hx_hash21(uv * Screen.xy * 0.7 + t * 57.0) - 0.5) * 0.18;
    color *= 0.9 + 0.1 * sin(uv.y * Screen.y * 1.2 + t * 20.0);
    color *= vignette(uv, 0.08, 0.46);
    color = mix(scene, color, I);
#elif defined(EFFECT_THERMAL)
    float l = luminance(sampleScene(uv) * 0.6 + sampleScene(uv + vec2(0.002, 0.0)) * 0.2 + sampleScene(uv - vec2(0.002, 0.0)) * 0.2);
    vec3 heat;
    if (l < 0.25) heat = mix(vec3(0.0, 0.0, 0.15), vec3(0.2, 0.0, 0.6), l / 0.25);
    else if (l < 0.5) heat = mix(vec3(0.2, 0.0, 0.6), vec3(0.9, 0.1, 0.3), (l - 0.25) / 0.25);
    else if (l < 0.75) heat = mix(vec3(0.9, 0.1, 0.3), vec3(1.0, 0.7, 0.0), (l - 0.5) / 0.25);
    else heat = mix(vec3(1.0, 0.7, 0.0), vec3(1.0, 1.0, 0.9), (l - 0.75) / 0.25);
    heat += (hx_hash21(uv * Screen.xy + t * 33.0) - 0.5) * 0.05;
    color = mix(scene, heat, I);
#elif defined(EFFECT_HEARTBEAT)
    float beat = fract(t * 1.2);
    float pulse = exp(-beat * 14.0) + 0.6 * exp(-max(0.0, beat - 0.22) * 16.0) * step(0.22, beat);
    vec2 centered = uv - 0.5;
    vec2 zoomed = 0.5 + centered * (1.0 - pulse * 0.012 * I);
    color = sampleScene(zoomed);
    float edge = 1.0 - vignette(uv, 0.55, 0.12 + (1.0 - pulse) * 0.08);
    color = mix(color, vec3(luminance(color)), edge * 0.6 * I);
    color = mix(color, Tint.rgb * (0.35 + pulse * 0.4), edge * (0.55 + pulse * 0.35) * I);
#elif defined(EFFECT_HEAT)
    float rise = t * 0.8;
    vec2 distortion = vec2(
        hx_noise2(vec2(uv.x * 14.0, uv.y * 9.0 + rise * 3.0)) - 0.5,
        hx_noise2(vec2(uv.x * 11.0 + 5.0, uv.y * 13.0 + rise * 4.0)) - 0.5
    );
    float strength = 0.012 * I * (0.4 + 0.6 * (1.0 - uv.y));
    color = sampleScene(uv + distortion * strength);
    color *= mix(vec3(1.0), vec3(1.08, 1.0, 0.9), I);
#elif defined(EFFECT_PIXEL)
    float cells = mix(Screen.y, 90.0, I);
    vec2 size = vec2(cells * Screen.z, cells);
    vec2 snapped = (floor(uv * size) + 0.5) / size;
    color = sampleScene(snapped);
    float levels = mix(64.0, 6.0, I);
    color = floor(color * levels + 0.5) / levels;
#elif defined(EFFECT_CHROMATIC)
    vec2 dir = (uv - 0.5);
    float amount = 0.02 * I * (0.6 + 0.4 * sin(t * 3.0));
    color.r = sampleScene(uv + dir * amount).r;
    color.g = sampleScene(uv).g;
    color.b = sampleScene(uv - dir * amount).b;
#elif defined(EFFECT_FEAR)
    float closing = 0.42 - 0.2 * I - 0.03 * sin(t * 2.2);
    float shake = (hx_noise2(vec2(t * 18.0, 1.0)) - 0.5) * 0.004 * I;
    color = sampleScene(uv + vec2(shake, -shake));
    color = mix(color, vec3(luminance(color)) * vec3(0.8, 0.9, 1.0), 0.7 * I);
    float v = vignette(uv, 0.3, closing);
    color *= mix(1.0, v, I);
    color += (hx_hash21(uv * Screen.xy + t * 29.0) - 0.5) * 0.07 * I;
#elif defined(EFFECT_UNDERWATER)
    vec2 wave = vec2(sin(uv.y * 20.0 + t * 2.0), cos(uv.x * 18.0 + t * 1.7)) * 0.004 * I;
    color = sampleScene(uv + wave);
    float caustic = pow(hx_noise2(uv * 12.0 + vec2(t * 0.6, t * 0.4)) * hx_noise2(uv * 9.0 - t * 0.5), 2.0);
    color = mix(color, color * vec3(0.45, 0.8, 1.0) + Tint.rgb * 0.08, I * 0.8);
    color += vec3(0.3, 0.6, 0.8) * caustic * 0.4 * I;
    color *= mix(1.0, vignette(uv, 0.7, 0.3), I);
#elif defined(EFFECT_LENSING)
    // Гравитационное линзирование вокруг чёрной дыры
    vec2 d = (uv - Point.xy) * vec2(Screen.z, 1.0);
    float dist = length(d);
    float rs = Point.z;
    float bend = Point.w * rs * rs / max(dist, rs * 0.7);
    vec2 offset = normalize(d + 1e-6) * bend / vec2(Screen.z, 1.0);
    color = sampleScene(uv - offset);
    float ring = 1.0 - smoothstep(0.0, rs * 0.25, abs(dist - rs * 1.35));
    color += Tint.rgb * ring * 0.35 * I;
    color = mix(scene, color, I);
#elif defined(EFFECT_RIPPLE)
    // Волна от удара: кольцо искажения расходится от точки
    vec2 d = (uv - Point.xy) * vec2(Screen.z, 1.0);
    float dist = length(d);
    float radius = Point.z;
    float width = 0.06 + radius * 0.15;
    float x = (dist - radius) / width;
    float wave = sin(clamp(x, -1.0, 1.0) * 3.14159) * (1.0 - smoothstep(0.7, 1.0, abs(x)));
    vec2 offset = normalize(d + 1e-6) * wave * Point.w / vec2(Screen.z, 1.0);
    color = sampleScene(uv - offset);
    color += Tint.rgb * max(0.0, wave) * 0.15 * I;
    color = mix(scene, color, I);
#endif

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
