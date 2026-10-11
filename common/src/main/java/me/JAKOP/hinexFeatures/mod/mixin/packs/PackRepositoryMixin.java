package me.JAKOP.hinexFeatures.mod.mixin.packs;

import com.google.common.collect.ImmutableList;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.packs.PackManager;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void hinex$lockOrder(Collection<String> selectedNames, CallbackInfoReturnable<List<Pack>> cir) {
        PackManager packs = HinexFeaturesClient.packs();
        if (packs == null) return;

        List<Pack> selected = cir.getReturnValue();
        List<Pack> ours = selected.stream()
                .filter(packs::isHinexPack)
                .sorted(Comparator.comparingInt(packs::order).reversed())
                .toList();
        if (ours.isEmpty()) return;

        List<Pack> result = new ArrayList<>(selected);
        result.removeAll(ours);
        int insertAt = result.size();
        while (insertAt > 0 && result.get(insertAt - 1).getPackSource() == PackSource.SERVER) insertAt--;
        result.addAll(insertAt, ours);
        cir.setReturnValue(ImmutableList.copyOf(result));
    }
}
