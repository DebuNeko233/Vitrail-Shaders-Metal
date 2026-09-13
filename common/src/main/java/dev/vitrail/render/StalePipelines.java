package dev.vitrail.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.List;
import java.util.function.Predicate;

/**
 * Selective pipeline-cache eviction for backend state that is compiled from a {@link RenderPipeline}
 * but can change while the Java pipeline object itself stays alive.
 * <p>
 * Minecraft 26.2 exposes only a full {@code clearPipelineCache()} on {@code GpuDeviceBackend}. That
 * is too wide for a live entity-mesh format transition: every matching cache key must stop serving
 * new draws immediately, while the old native pipeline must stay alive long enough for already
 * recorded GPU work to finish. Each backend therefore owns how evicted native objects are deferred
 * to a safe destruction point; Vitrail owns only the predicate describing which pipeline keys are
 * stale.
 */
public interface StalePipelines {

	/**
	 * Removes cached pipelines matching {@code predicate} without immediately destroying their
	 * compiled backend objects, and returns the keys that actually left the cache so the caller can
	 * precompile replacements against the state now in force.
	 */
	List<RenderPipeline> vitrail$dropPipelines(Predicate<RenderPipeline> predicate);
}
