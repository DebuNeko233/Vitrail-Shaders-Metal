package dev.vitrail.mixin.metallum;

import dev.vitrail.render.StalePipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;

/**
 * Adapts Metallum's selective pipeline-cache eviction to Vitrail's backend-neutral cache command.
 * <p>
 * The companion Metallum implementation owns native pipeline lifetime: a matching cache entry is
 * removed immediately, while its compiled Metal object waits for the next full cache clear and the
 * GPU-completion wait that already guards that release point. This mixin supplies only the Vitrail
 * predicate; no Metal handle or compiled pipeline type crosses the boundary.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalDevice", remap = false)
public abstract class MetalDeviceMixin implements StalePipelines {

	@Shadow(remap = false)
	public abstract List<RenderPipeline> evictCachedPipelines(Predicate<RenderPipeline> predicate);

	@Override
	public List<RenderPipeline> vitrail$dropPipelines(Predicate<RenderPipeline> predicate) {
		return evictCachedPipelines(predicate);
	}
}
