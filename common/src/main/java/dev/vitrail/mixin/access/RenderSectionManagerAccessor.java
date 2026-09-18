package dev.vitrail.mixin.access;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the walk state the shadow stage has to move without going through
 * {@code prepareRender}.
 * <p>
 * The shadow stage temporarily replaces Sodium's camera render lists with a light walk, draws
 * the map, then rebuilds the camera contents before the world's chunk draws. The list/tree/task
 * references are snapshotted and restored around that scope so the extra traversal does not
 * become Sodium's state for the next frame.
 * <p>
 * The light walk uses a shadow-only frame token rather than advancing Sodium's real frame. Region
 * lists reset on a frame-token change; using the next real frame number would leave light-only
 * regions looking already visited when that next frame arrives.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public interface RenderSectionManagerAccessor {

	@Accessor("renderLists")
	SortedRenderLists vitrail$getRenderLists();

	@Accessor("renderLists")
	void vitrail$setRenderLists(SortedRenderLists value);

	@Accessor("renderTree")
	@Nullable SectionTree vitrail$getRenderTree();

	@Accessor("renderTree")
	void vitrail$setRenderTree(@Nullable SectionTree value);

	@Accessor("taskLists")
	@Nullable DeferredTaskList vitrail$getTaskLists();

	@Accessor("taskLists")
	void vitrail$setTaskLists(@Nullable DeferredTaskList value);

	@Accessor("frame")
	int vitrail$getFrame();

	@Accessor("frame")
	void vitrail$setFrame(int frame);

	@Invoker("readRenderListFromTree")
	void vitrail$readRenderListFromTree(Viewport viewport, FogParameters fog);

	@Invoker("renderOutOfGraph")
	void vitrail$renderOutOfGraph(Viewport viewport, FogParameters fog);
}
