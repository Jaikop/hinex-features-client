#ifndef HINEX_NOISE_GLSL
#define HINEX_NOISE_GLSL

// Хэши и шумы для эффектов HinexFeatures (без текстур — всё процедурно)

float hx_hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hx_hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hx_hash31(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float hx_noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hx_hash21(i);
    float b = hx_hash21(i + vec2(1.0, 0.0));
    float c = hx_hash21(i + vec2(0.0, 1.0));
    float d = hx_hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float hx_noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hx_hash31(i);
    float n100 = hx_hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hx_hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hx_hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hx_hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hx_hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hx_hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hx_hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(
        mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
        mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
        f.z);
}

float hx_fbm2(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * hx_noise2(p);
        p = p * 2.03 + 17.1;
        amplitude *= 0.5;
    }
    return value / 0.9375;
}

float hx_fbm3(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * hx_noise3(p);
        p = p * 2.03 + 17.1;
        amplitude *= 0.5;
    }
    return value / 0.9375;
}

// Упорядоченный дизеринг 4×4: «полупрозрачность» без смешивания цветов
float hx_bayer4(vec2 fragCoord) {
    int x = int(mod(fragCoord.x, 4.0));
    int y = int(mod(fragCoord.y, 4.0));
    int index = x + y * 4;
    float m[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[index] + 0.5) / 16.0;
}

vec3 hx_hue_rotate(vec3 color, float angle) {
    const vec3 k = vec3(0.57735);
    float c = cos(angle);
    return color * c + cross(k, color) * sin(angle) + k * dot(k, color) * (1.0 - c);
}

#endif
