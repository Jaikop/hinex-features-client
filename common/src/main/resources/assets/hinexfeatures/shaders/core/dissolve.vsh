#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <hinexfeatures:noise.glsl>
#include <hinexfeatures:dissolve_params.glsl>

// Модель игрока, которая исчезает или появляется (ваниш). Геометрия та же, что у обычной модели,
// вершинный шейдер лишь деформирует её для некоторых стилей.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;
layout(location = 5) out vec3 localPos;
layout(location = 6) out vec3 worldNormal;
layout(location = 7) out vec3 relPos;

vec2 rotate2(vec2 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

void main() {
    vec3 local = Position - DS_BASE;
    float height = max(DS_HEIGHT, 0.05);
    float progress = clamp(DS_PROGRESS, 0.0, 1.0);
    float h = clamp(local.y / height, 0.0, 1.0);
    int style = DS_STYLE;
    vec3 deformed = local;

    if (style == STYLE_HOLOGRAM) {
        // Как выключение старого монитора: модель сплющивается в горизонтальную линию
        float squash = smoothstep(0.55, 0.92, progress);
        float mid = height * 0.5;
        deformed.y = mid + (local.y - mid) * mix(1.0, 0.015, squash);
        deformed.xz *= 1.0 + squash * 0.7;
        // Горизонтальные «срывы» строк
        float time = floor(DS_TIME * 14.0);
        float band = floor(local.y * 10.0);
        float glitch = step(0.86, hx_hash21(vec2(band, time)));
        deformed.x += (hx_hash21(vec2(time, band + 5.0)) - 0.5) * 0.18 * glitch * (0.25 + progress);
    } else if (style == STYLE_GLITCH) {
        float time = floor(DS_TIME * 16.0);
        float band = floor(local.y * 8.0);
        float jump = step(0.72, hx_hash21(vec2(band, time)));
        vec2 offset = vec2(hx_hash21(vec2(time, band + 3.0)), hx_hash21(vec2(band, time + 7.0))) - 0.5;
        deformed.xz += offset * 0.35 * jump * (0.15 + progress);
    } else if (style == STYLE_VOID) {
        // Затягивает в точку над головой, закручивая вокруг оси
        float pull = smoothstep(0.15, 1.0, progress);
        float angle = pull * pull * 7.0 * (1.25 - h);
        deformed.xz = rotate2(local.xz, angle) * (1.0 - pull * 0.97);
        deformed.y = mix(local.y, height * 0.62, pull * pull * 0.95);
    } else if (style == STYLE_ASH) {
        // Лёгкое «вздутие» сгорающей части перед распадом
        float swell = smoothstep(0.0, 1.0, progress) * 0.03;
        deformed.xz *= 1.0 + swell * h;
    }

    vec3 pos = DS_BASE + deformed;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    localPos = local;
    worldNormal = Normal;
    relPos = pos;
}
