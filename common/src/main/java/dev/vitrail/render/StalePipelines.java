package dev.vitrail.render;

import dev.vitrail.mixin.access.RenderPipelineAccessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pipeline-cache operations for backend state that is compiled from a {@link RenderPipeline} but
 * can change while the Java pipeline object itself stays alive.
 * <p>
 * Minecraft 26.2 exposes only a full {@code clearPipelineCache()} on {@code GpuDeviceBackend}. That
 * is too wide for a live entity-mesh format transition: every matching cache key must stop serving
 * new draws immediately, while the old native pipeline must stay alive long enough for already
 * recorded GPU work to finish. Each backend therefore owns how evicted native objects are deferred
 * to a safe destruction point; Vitrail owns only the predicate describing which pipeline keys are
 * stale.
 * <p>
 * An earlier shape of this interface also carried an adoption step, for a pipeline a background
 * worker had compiled outside the backend's cache and the render thread then handed in. That step
 * existed only for the detached warm-up, which is gone: the warm-up now compiles through the
 * backend's own public precompile road, so the cache owns the pipeline from the call and there is
 * nothing to adopt.
 */
public interface StalePipelines {

	/**
	 * Removes cached pipelines matching {@code predicate} without immediately destroying their
	 * compiled backend objects, and returns the keys that actually left the cache so the caller can
	 * precompile replacements against the state now in force.
	 */
	List<RenderPipeline> vitrail$dropPipelines(Predicate<RenderPipeline> predicate);

	/**
	 * Vitrail policy for the current live mesh transition: drop the game's entity pipelines, read
	 * from their declared formats rather than the getter that Vitrail itself rewrites while the
	 * wider mesh is active. Backends receive only the predicate and remain unaware of entity rules.
	 */
	default List<RenderPipeline> vitrail$dropEntityPipelines() {
		return vitrail$dropPipelines(StalePipelines::vitrail$declaresGameEntity);
	}

	private static boolean vitrail$declaresGameEntity(RenderPipeline pipeline) {
		@Nullable VertexFormat[] declared = ((RenderPipelineAccessor) pipeline).vitrail$declaredFormats();
		for (VertexFormat format : declared) {
			@SuppressWarnings("ReferenceEquality")
			boolean entity = format == DefaultVertexFormat.ENTITY;
			if (entity) {
				return true;
			}
		}

		return false;
	}
}
