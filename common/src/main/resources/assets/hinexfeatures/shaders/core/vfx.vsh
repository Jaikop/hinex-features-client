#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Геометрия эффектов в мире (частицы, лучи, круги). Форма задаётся не текстурой, а номером:
// UV0.x = форма * 2 + u, UV0.y = зерно * 2 + v (у лучей и молний UV0.y — длина вдоль луча).

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec3 texCoord0;
layout(location = 4) flat out int shape;
layout(location = 5) flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;

    float shapeValue = floor(UV0.x * 0.5);
    float seedValue = floor(UV0.y * 0.5);
    shape = int(shapeValue + 0.5);
    seed = seedValue;
    texCoord0 = vec3(UV0.x - shapeValue * 2.0, UV0.y - seedValue * 2.0, UV0.y);
}
