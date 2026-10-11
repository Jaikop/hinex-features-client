package me.JAKOP.hinexFeatures.mod.vfx.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;

import java.lang.reflect.Method;

public final class ShaderCompat {

    private static boolean looked;
    private static Object api;
    private static Method inUse;
    private static double checkedAt = -1.0;
    private static boolean active;

    private ShaderCompat() {
    }

    public static boolean shadersActive() {
        double now = VfxClock.now();
        if (checkedAt >= 0.0 && now - checkedAt < 0.5 && now >= checkedAt) return active;
        checkedAt = now;
        active = query();
        return active;
    }

    private static boolean query() {
        if (!looked) {
            looked = true;
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                inUse = apiClass.getMethod("isShaderPackInUse");
                registerPipelines(apiClass);
                HinexFeaturesClient.LOGGER.info("Iris/Oculus найден: с шейдерами эффекты рисуются поверх кадра, ваниш — без своего шейдера");
            } catch (ClassNotFoundException e) {
                api = null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                api = null;
                HinexFeaturesClient.LOGGER.warn("API Iris не отвечает — считаем, что шейдеров нет: {}", e.toString());
            }
        }
        if (api == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void registerPipelines(Class<?> apiClass) {
        try {
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Method assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
            Object entitiesTranslucent = programClass.getMethod("valueOf", String.class).invoke(null, "ENTITIES_TRANSLUCENT");
            assign.invoke(api, HinexPipelines.DISSOLVE, entitiesTranslucent);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HinexFeaturesClient.LOGGER.debug("Iris: assignPipeline недоступен: {}", e.toString());
        }
    }
}
