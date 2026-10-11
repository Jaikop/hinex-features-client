#ifndef HINEX_DISSOLVE_PARAMS_GLSL
#define HINEX_DISSOLVE_PARAMS_GLSL

// Параметры эффекта растворения передаются в матрице TextureMat (DynamicTransforms),
// которую мод заполняет своими числами для каждой исчезающей модели.
//   [0] = основание модели относительно камеры (xyz), высота модели
//   [1] = прогресс 0..1 (1 — модели нет), время в секундах, зерно шума, ширина модели
//   [2] = основной цвет эффекта (rgb), сила свечения
//   [3] = второй цвет эффекта (rgb), стиль
#define DS_BASE      (TextureMat[0].xyz)
#define DS_HEIGHT    (TextureMat[0].w)
#define DS_PROGRESS  (TextureMat[1].x)
#define DS_TIME      (TextureMat[1].y)
#define DS_SEED      (TextureMat[1].z)
#define DS_WIDTH     (TextureMat[1].w)
#define DS_COLOR     (TextureMat[2].rgb)
#define DS_GLOW      (TextureMat[2].w)
#define DS_COLOR2    (TextureMat[3].rgb)
#define DS_STYLE     (int(TextureMat[3].w + 0.5))

#define STYLE_STANDARD 0
#define STYLE_ASH 1
#define STYLE_HOLOGRAM 2
#define STYLE_GLOW 3
#define STYLE_GLITCH 4
#define STYLE_VOID 5
#define STYLE_FROST 6
#define STYLE_VANISHED 9

#endif
