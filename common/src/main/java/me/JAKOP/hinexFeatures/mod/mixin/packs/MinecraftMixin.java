package me.JAKOP.hinexFeatures.mod.mixin.packs;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.packs.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"))
    private RepositorySource[] hinex$addHinexPacks(RepositorySource[] sources) {
        RepositorySource[] result = Arrays.copyOf(sources, sources.length + 1);
        result[sources.length] = consumer -> {
            PackManager packs = HinexFeaturesClient.packs();
            if (packs != null) packs.repositorySource().loadPacks(consumer);
        };
        return result;
    }
}
