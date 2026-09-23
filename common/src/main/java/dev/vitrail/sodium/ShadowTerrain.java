package dev.vitrail.sodium;

import dev.vitrail.render.ShadowCensus;
import dev.vitrail.mixin.access.MixinSodiumWorldRenderer;
import dev.vitrail.mixin.access.RenderSectionManagerAccessor;
import dev.vitrail.pack.source.ShadowCasters;
import dev.vitrail.render.BlockStateIds;
import dev.vitrail.render.DistantDraw;
import dev.vitrail.render.PackChain;
import dev.vitrail.render.ShadowAmortisation;
import dev.vitrail.render.ShadowCullPlan;
import dev.vitrail.render.ShadowGeometry;
import dev.vitrail.render.TerrainDraw;
import dev.vitrail.render.timing.RingTimings;
import dev.vitrail.render.timing.ShadowFrameProbe;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FallbackVisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Draws the chunk renderer once more for the light at the head of the level frame, after Sodium
 * has culled for the camera and before Minecraft asks Sodium to prepare the camera's chunk batches.
 * <p>
 * The light walk and the camera walk share Sodium's persistent per-region render-list objects.
 * A shadow-only frame token makes the light traversal reset those objects without advancing
 * Sodium's real frame. The light lists are prepared and drawn immediately; then the exact camera
 * viewport captured from {@code SodiumWorldRenderer.setupTerrain} is traversed again to put the
 * camera contents back into every region the light overwrote. The manager's top-level render-list,
 * tree and deferred-task references and its flags are restored afterwards, so the extra walk is a
 * scoped operation rather than the state Sodium carries into the next frame.
 * <p>
 * Running here rather than at the end of the previous frame is required by voxelising packs. Iris
 * clears custom images, draws the shadow geometry and dispatches {@code shadowcomp} in one frame.
 * A pack may derive its voxel-grid origin from the current view direction as well as the camera
 * position; writing the identity volume under one frame's view and reading it under the next makes
 * that grid jump when the view-centred origin crosses an integer cell. Keeping writer and compute
 * in the same frame fixes the semantic mismatch without knowing how any pack computes its centre.
 * <p>
 * Nothing of the terrain draw itself is reimplemented here. {@code drawChunkLayer} is Sodium's
 * own public entry in the Minecraft 26.2 / Sodium 0.9.2 line; Vitrail only supplies the light's
 * render lists and changes its own shadow routing while the draw runs.
 * <p>
 * The matrices handed to Sodium remain the camera's deliberately. They feed Sodium's own
 * {@code u_Globals}; pack shadow programs read their shadow matrices from Vitrail's own block.
 */
public final class ShadowTerrain {

	/** The frame's own model view, taken where the frame graph was handed it. */
	private static final Matrix4f MODEL_VIEW = new Matrix4f();

	/** Scratch for the light's cull matrix, one per process rather than one per frame. */
	private static final Matrix4f LIGHT = new Matrix4f();

	/** The same, for the camera's volume and the light's direction the walk's shape is built from. */
	private static final Matrix4f CAMERA = new Matrix4f();
	private static final Vector3f LIGHT_VECTOR = new Vector3f();

	private static Vec3 camera;

	/** The exact camera traversal inputs Sodium used before the level render begins. */
	private static @Nullable Viewport cameraWalkViewport;
	private static @Nullable FogParameters cameraWalkFog;

	/**
	 * The block table the cull was last measured against, or -1 for none. Counted rather than
	 * latched: a flag of the process would report the first pack of the session and say nothing for
	 * any pack loaded after it, which is where the reading is worth having.
	 */
	private static int measured = -1;

	/**
	 * What the last walk for the light kept, drew and measured against, held for the F3 line.
	 * <p>
	 * Held rather than asked for where it is shown, because by then the render lists belong to the
	 * camera again: this stage temporarily walks them for the light and hands them straight back,
	 * so a count
	 * taken from the overlay would be the camera's under a shadow heading. Iris holds the same thing
	 * for the same reason, a string taken inside its shadow scope and read outside it
	 * ({@code shadows/ShadowRenderer.java:119} and {@code :606}).
	 * <p>
	 * A null {@link #walkCulling} means no walk has run under the pack now loaded, which the line
	 * says in those words rather than showing the numbers of a pack that is no longer drawn.
	 */
	private static int walkKept;

	private static int walkDrawn;

	private static int walkTotal;

	private static boolean walkTerrain;

	private static @Nullable String walkCulling;

	/**
	 * Diagnostic arms for the shadow stage, and nothing else.
	 * <p>
	 * <strong>NOT SEMANTICALLY CORRECT and never to be productionised.</strong> Each one takes one
	 * component of the stage out of the frame so that the component's own cost can be read off the
	 * only GPU time this backend really reports, which is the whole-frame figure the driver answers
	 * with: a per-pass GPU clock does not exist on the Metal path, because
	 * {@code MetalCommandEncoder.writeTimestamp} stores {@code System.nanoTime()} at record time.
	 * What an arm is for is a difference between two arms and never a setting to ship; the picture
	 * on either arm is the pack's picture with a piece of its shadow map missing.
	 */
	private static final boolean PROBE_NO_SHADOW_RASTER =
			Boolean.getBoolean("vitrail.probeNoShadowRaster");

	private static final boolean PROBE_NO_SHADOW_ENTITIES =
			Boolean.getBoolean("vitrail.probeNoShadowEntities");

	private static final boolean PROBE_NO_SHADOW_TRANSLUCENT =
			Boolean.getBoolean("vitrail.probeNoShadowTranslucent");

	private ShadowTerrain() {
	}

	/**
	 * Takes the frame's model view and camera position immediately before the same-frame shadow
	 * stage runs.
	 */
	public static void capture(Matrix4fc modelView, Vec3 cameraPosition) {
		MODEL_VIEW.set(modelView);
		camera = cameraPosition;
	}

	/**
	 * Takes the camera traversal Sodium has just completed. The captured state is consumed by
	 * the next shadow stage so a frame that never ran terrain setup cannot accidentally reuse an
	 * older camera viewport.
	 */
	public static void captureCameraWalk(Viewport viewport, FogParameters fog) {
		cameraWalkViewport = viewport;
		cameraWalkFog = fog;
	}

	/**
	 * Walks the world for the light and draws the shadow map in the frame whose state was captured.
	 * One draw per capture: a frame that never set a graph up draws no map.
	 * <p>
	 * Caught like every other entry point the bus calls into, and this was the one that was not. What
	 * it latches is the stage rather than the pack, see {@link TerrainDraw#shadowStageFailed}.
	 */
	public static void draw() {
		try {
			walk();
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			TerrainDraw.shadowStageFailed(e);
		}

		// The section counts the pass table cannot say, read off the walk this class already keeps for its own
		// overlay line: how much of the world the most expensive row of the frame walked to get there.
		Walk walked = lastWalk();
		if (walked != null) {
			ShadowCensus.walked(walked.kept(), walked.drawn(), walked.total(), walked.terrain(),
					walked.culling());
		}
	}

	private static void walk() {
		Vec3 camera = ShadowTerrain.camera;
		ShadowTerrain.camera = null;

		Viewport restoreViewport = cameraWalkViewport;
		FogParameters restoreFog = cameraWalkFog;
		cameraWalkViewport = null;
		cameraWalkFog = null;

		SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
		Minecraft minecraft = Minecraft.getInstance();
		if (camera == null || renderer == null || minecraft == null) {
			return;
		}

		// Without the exact camera viewport there is no safe way to give Sodium its per-region
		// lists back after a light walk. Clear the pack's transient images rather than keeping a
		// stale identity volume, and leave the stage closed.
		if (restoreViewport == null || restoreFog == null) {
			PackChain.clearCustomImages();
			Vitrail.logger().warn("The shadow stage has no captured Sodium camera traversal this "
					+ "frame, so it was skipped rather than leaving the world's render lists on "
					+ "the light");
			return;
		}

		// The same refusal the pipeline mixin makes, and it has to be made here too: under OpenGL
		// nothing of ours is ever served, so the stage would walk and draw the whole world a second
		// time with the game's own shader, into the game's own target. This is exactly the state a
		// failed Metal boot leaves the machine in.
		if (DrawBackend.BACKEND == DrawBackend.OPENGL) {
			return;
		}

		// The stage itself is never skipped any more. What a kept map buys is the OPAQUE world, and
		// that is decided inside draw below: the walk still runs, because it is what hands Sodium
		// the light's own occlusion tree, and everything that moves is still drawn, because a
		// caster one frame late is the only thing anybody could see.

		// Ordered so that a stage that cannot open leaves the render lists untouched: the walk
		// below hands them to the light, and from that point on the camera has to be given them
		// back whatever else happens.
		if (!TerrainDraw.openShadowStage()) {
			// Complementary Ultra still samples floodfill and WSR from gbuffers. The shadow
			// programs may be refused so the stage never opens, but the clear still has to run
			// or the voxel volume keeps stale writes; the compute itself runs at the head of
			// the frame, from the frame graph setup, whatever this stage does.
			PackChain.clearCustomImages();
			walkCulling = null;
			return;
		}

		RenderSectionManager manager =
				((MixinSodiumWorldRenderer) renderer).vitrail$renderSectionManager();
		Matrix4f light = TerrainDraw.shadowFrustum(LIGHT);
		// Read here, in the same breath as the light's own matrix and off the same frame: the shape
		// the terrain is walked against is built from the camera's volume and the light's direction,
		// and the record's own note says what taking one of them a frame later would keep and drop.
		ShadowCullPlan plan = TerrainDraw.shadowCullPlan(LIGHT_VECTOR, CAMERA);
		if (manager == null || light == null || plan == null) {
			return;
		}

		// Counted only on the frame that will print it. It has to be taken HERE, the walk below
		// replacing the camera's render lists with the light's, but every other frame was walking
		// those lists for a line printed once per block table.
		//
		// Read once rather than again at the print, so a table installed between the two is named
		// on the next frame instead of at once. That is the right way round: the count in hand was
		// taken against the table that was standing when it was taken.
		boolean measuring = measured != BlockStateIds.generation();
		int seen = measuring ? sections(manager.getRenderLists()) : 0;

		ShadowCasters casters = TerrainDraw.shadowCasters();

		RenderSectionManagerAccessor access = (RenderSectionManagerAccessor) manager;
		SortedRenderLists cameraLists = access.vitrail$getRenderLists();
		SectionTree cameraTree = access.vitrail$getRenderTree();
		DeferredTaskList cameraTasks = access.vitrail$getTaskLists();
		int cameraFrame = access.vitrail$getFrame();
		// Region lists reset when their last-visible frame differs from the collector's frame. The
		// light therefore needs a distinct token, but it must NOT be the next real frame: a region
		// visible only to the light would then look already visited when that frame arrives. Flip
		// the sign bit instead. It is unique for this real frame and the manager is restored before
		// anything else in Sodium reads its frame counter.
		int shadowFrame = cameraFrame ^ Integer.MIN_VALUE;
		if (RingTimings.keepSecondRotate()) {
			// Developer timing probe only: keep the old second ring rotation measurable without
			// letting prepareRender's increment become the collector token.
			manager.prepareRender();
		}
		access.vitrail$setFrame(shadowFrame);
		try {
			// The shape the pack asked for, and a box around the camera cut out of it wherever a
			// shadow distance bounds the walk. Distance, default and Advanced keep that box and
			// no planes. Advanced is the named divergence ShadowCullFrustum.of carries:
			// Complementary Low lands there and the sweep pops leaves. The safe zone still
			// sweeps. Whether a bound applies at all is the pack's business at least as often as
			// the player's, most of the corpus declaring a render multiplier and being held at
			// its own half plane whatever the slider says, and the plan carries the arbitration
			// already made.
			ShadowCullFrustum.Chosen cull = ShadowCullFrustum.of(plan);
			Viewport viewport = new Viewport(cull.frustum(),
					new Vector3d(camera.x, camera.y, camera.z));
			// No fog, whatever the camera's walk was bounded by. With Sodium's fog occlusion on, the
			// walk stops at the fog's cull distance, and the camera's fog is the wrong bound for the
			// light: rain or water shortens it, and a hill that still casts into the view would
			// leave the map with it. Iris turns fog occlusion off for both walks whenever a pack is
			// loaded (compat/sodium/mixin/MixinRenderSectionManager.java, iris$disableFogOcclusion);
			// the camera's walk here still keeps its own fog, a gap of the picture and not of the
			// map, and not this stage's to close.
			// The light always wants Sodium's synchronous fallback traversal. Call that builder
			// directly instead of finalizeRenderLists: the latter also advances camera timing
			// state, which belongs only to the real camera setup.
			access.vitrail$renderOutOfGraph(viewport, FogParameters.NONE);

			// The entities, now that the tree Sodium answers visibility from is the light's, and the
			// class note says what asking the camera's would drop. The block entities follow: this
			// is the first line of the stage at which the light has render lists of its own, and
			// they are what says which sections to ask. Sodium's door onto them is the one Iris
			// reaches through the game's extraction (shadows/ShadowRenderer.java:668, cancelled and
			// served by the same mixin); the game's own visible sections are never filled at all
			// under Sodium, so the walk that read them found a world with no chests in it.
			ShadowGeometry.gather(light, camera, casters);
			ClientLevel level = minecraft.level;
			if (level != null) {
				ShadowGeometry.gatherBlockEntities((state, partial) -> renderer.extractBlockEntities(
						minecraft.gameRenderer.mainCamera(), partial, level.destructionProgress(),
						state));
			}

			// Once per block table, and never on a frame where the camera saw nothing. Two equal
			// numbers mean the cull did not happen, and nothing on screen would say so. The table is
			// named because a second load of the same pack prints this again, word for word: it is
			// what tells the two readings apart, not a property of the cull itself.
			//
			// Two counts of the light's list rather than one, because they answer different
			// questions and only the second one compares with anything outside this engine. The
			// walked count is every section with something to render that the cull kept, which is
			// what says how tight the cull was; the drawn count leaves out those carrying no block
			// geometry, which are the ones a draw costs nothing for.
			count(manager.getRenderLists());
			walkTotal = manager.getTotalSections();
			walkTerrain = casters.terrain();
			walkCulling = cull.culling();

			if (measuring && seen > 0) {
				measured = BlockStateIds.generation();
				Vitrail.logger().info("shadow-cull {} kept={} camera={} drawn={} blocks={}",
						walkCulling, walkKept, seen, walkDrawn, measured);
			}

			// The anchor is NOT taken here. It moves where the opaque world is really drawn, inside
			// the call below, and a copy of that line left standing at this level is what made the
			// whole thing do nothing: it reset the interval on every frame, so the count never
			// elapsed and the map was filled again every time.
			draw(renderer, minecraft, camera);
		} finally {
			// Both counts are taken behind the probe's own question, so an unarmed frame pays one
			// field read and nothing else: the light's before the camera's lists are put back, the
			// camera's after, which is the pair the per-frame fork needs.
			boolean probing = ShadowFrameProbe.armed();
			int lightSections = probing ? sections(manager.getRenderLists()) : 0;

			restoreCameraWalk(access, restoreViewport, restoreFog, cameraFrame,
					cameraLists, cameraTree, cameraTasks);

			if (probing) {
				ShadowFrameProbe.frame(lightSections, sections(manager.getRenderLists()));
			}
		}
	}

	/**
	 * Rebuilds the contents of the persistent region lists the light overwrote, then restores the
	 * manager objects and flags Sodium had after its real camera setup.
	 * <p>
	 * The temporary camera traversal runs under a THIRD token, distinct from both the real camera
	 * token and the sign-bit-flipped shadow token. That distinction is required even for regions the
	 * light never touched: a full camera traversal visits them again, and reusing the real camera
	 * token would append every visible section to an already-full persistent region list instead of
	 * resetting it first. The third token therefore rebuilds every camera-visible region exactly
	 * once; the original top-level list can then be put back verbatim. Light-only regions keep the
	 * shadow token, and neither temporary token can masquerade as the next real frame.
	 */
	private static void restoreCameraWalk(RenderSectionManagerAccessor access,
			Viewport viewport, FogParameters fog, int frame, SortedRenderLists lists,
			@Nullable SectionTree tree, @Nullable DeferredTaskList tasks) {
		// Reusing the real camera token here is unsafe. Regions visible to the camera but not the
		// light still carry that token and already contain their complete list; walking the whole
		// camera tree again would append duplicate sections until ChunkRenderList reports
		// "Render list is full". Flip a different bit from the shadow token so every camera-visible
		// region resets before this repair traversal, while the manager's real frame is restored
		// immediately afterwards.
		int restoreFrame = frame ^ (1 << 30);
		try {
			access.vitrail$setFrame(restoreFrame);

			// Do NOT call finalizeRenderLists here. Its camera timing control updates
			// previousPosition/isSyncRendering every time it is asked, so a second call in one
			// frame changes Sodium's decision for the next frame. Rebuild only the list contents.
			//
			// The saved renderTree says which path the real camera setup actually took. A fallback
			// tree means it rendered synchronously/out of graph; otherwise the regular tree reader
			// reproduces the camera traversal from the unchanged cullResults.
			if (tree instanceof FallbackVisibleChunkCollector || tree == null) {
				access.vitrail$renderOutOfGraph(viewport, fog);
			} else {
				access.vitrail$readRenderListFromTree(viewport, fog);
			}
		} finally {
			access.vitrail$setFrame(frame);
			access.vitrail$setRenderLists(lists);
			access.vitrail$setRenderTree(tree);
			access.vitrail$setTaskLists(tasks);
		}
	}

	private static void draw(SodiumWorldRenderer renderer, Minecraft minecraft, Vec3 camera) {
		// Sodium's own source for it, so that what reaches u_Globals is what would have reached it
		// anyway: this one carries the walk bob and the camera state's does not.
		Matrix4fc projection =
				((GameRendererStorage) minecraft.gameRenderer).sodium$getProjectionMatrix();
		ChunkRenderMatrices matrices = new ChunkRenderMatrices(projection, MODEL_VIEW);

		// The game's own chunk sampler, mipmapped and clamped, and it is NOT what the pack's shadow
		// programs read the atlas through: the renderer hands this to begin, where the chunk
		// renderer mixin settles the pack's sampler for every pass, this one included, so the shadow
		// half binds the same NEAREST sampler as the gbuffer half, through TerrainSampler and never
		// through this argument.
		//
		// The legacy switch does NOT part the two halves the way it reads: it puts the game's LINEAR
		// back through LegacyTerrainFilter, which is the sampler the renderer hands begin, and the
		// argument on this line is NEAREST rather than the game's. So the gbuffer half gets the
		// game's filter back and the shadow half stays NEAREST either way, and an A/B taken with
		// the switch measures the gbuffer change alone.
		//
		// What this argument reaches is Sodium's own shader, on a pass handed back to it,
		// and there Iris hands NEAREST down the same road (shadows/ShadowRenderer.java:389), where
		// the gbuffers hand it the game's LINEAR: NEAREST here too, so that the one pass Sodium
		// draws itself filters as it does under Iris. Iris's SodiumShader.setupState binds its own
		// instead (pipeline/programs/SodiumShader.java:131), and so does ours.
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

		ShadowCasters casters = TerrainDraw.shadowCasters();

		// Before geometry writes, matching Iris clearing custom images at the head of the
		// shadow stage. Complementary's voxel volume is marked clear; the floodfill is not.
		PackChain.clearCustomImages();

		// The opaque world, and the one thing a kept map spares. Restored rather than drawn when the
		// setting says so and a map is in the store, the restore itself having already been encoded
		// where the clear would have been: the pack's own refusal still comes first, since a pack
		// that keeps the opaque world out of its map has nothing to keep.
		boolean drawTerrain = ShadowAmortisation.drawTerrainThisFrame() || !TerrainDraw.shadowMapKept();

		// Refused by the pack rather than skipped for cheapness.
		if (casters.terrain() && drawTerrain && !PROBE_NO_SHADOW_RASTER) {
			// Distant Horizons' far terrain goes first and INSIDE this word, both of which are
			// Iris's. DH hangs its LOD draws off the HEAD of ChunkSectionsToRender.renderGroup
			// (neoforge/mixins/client/MixinChunkSectionsToRender.java:67-74), and the only call to
			// that method in Iris's shadow stage is the one inside its own shadowTerrain test
			// (shadows/ShadowRenderer.java:508-511). So a pack that keeps the opaque world out of
			// its map keeps the far terrain out with it, and getting this wrong is not a nuance: a
			// pack that asked for neither would see LODs in its map here and none under Iris.
			DistantDraw.shadow(false, camera);
			TerrainDraw.shadowPass(() -> renderer.drawChunkLayer(ChunkSectionLayerGroup.OPAQUE,
					matrices, camera.x, camera.y, camera.z, sampler));
		}

		// The store is taken HERE and nowhere else: with the opaque world in the map and before the
		// first thing that moves goes into it. A store taken later would carry a mob, and every
		// frame restoring it would paint that mob's old place back under the new one. Taken only where
		// a later frame may put it back: with the reuse off the copy is the whole map and its colours
		// on every frame, read by nobody, and it cost the M4 about eight per cent of its frames.
		if (drawTerrain && casters.terrain()) {
			if (ShadowAmortisation.keepsDrawnMap()) {
				TerrainDraw.keepShadowMap();
			}
			ShadowAmortisation.drawn();
		}

		// Everything that moves, between the opaque world and the copy, which is where Iris puts it
		// (shadows/ShadowRenderer.java:584 then :588). It matters that it is before the copy and not
		// after: shadowtex1 is the map WITHOUT the translucent half, and a mob belongs in it. Drawn
		// after the copy, every caster that moves would be missing from the one name half the corpus
		// reads its shadows through.
		if (!PROBE_NO_SHADOW_ENTITIES) {
			ShadowGeometry.draw(camera);
		}

		// Between the translucent group and everything else, and nowhere else: this is the one moment
		// shadowtex0 and shadowtex1 hold different things, and what separates them is exactly the
		// draw that comes next. The renderer closes its own render pass before returning, and the
		// walk above closes its last one, so a copy here is outside one.
		TerrainDraw.copyShadowDepth();

		if (casters.translucent() && !PROBE_NO_SHADOW_TRANSLUCENT && !PROBE_NO_SHADOW_RASTER) {
			// And its water half here, after the copy and inside the word that governs the world's
			// own translucent group, for the two reasons the opaque half is where it is: DH's hook
			// is the head of this very call, and Iris makes it inside its own shadowTranslucent test
			// (shadows/ShadowRenderer.java:598-601). shadowtex1 is the map WITHOUT the translucents,
			// and far water belongs on the same side of it as near water.
			DistantDraw.shadow(true, camera);
			TerrainDraw.shadowPass(() -> renderer.drawChunkLayer(ChunkSectionLayerGroup.TRANSLUCENT,
					matrices, camera.x, camera.y, camera.z, sampler));
		}

		// And the chain last of all, on a map nothing else will write this frame, which is where
		// Iris fills it too (shadows/ShadowRenderer.java:613-615). After the copy and not before:
		// shadowtex1 takes its own chain over the base the copy just wrote.
		TerrainDraw.mipShadowMap();
	}

	/** Every section a walk kept, which is what says how tight the shape it measured against was. */
	private static int sections(SortedRenderLists lists) {
		int count = 0;
		var iterator = lists.iterator(false);
		while (iterator.hasNext()) {
			ChunkRenderList list = iterator.next();
			count += list.size();
		}

		return count;
	}

	/**
	 * Both counts of the light's list, in one pass because the F3 line asks for them every frame
	 * and not once a block table any more.
	 * <p>
	 * <strong>The drawn count is the one that compares with the reference, and the kept count is
	 * not.</strong> Only the sections carrying block geometry reach a draw, which walks
	 * {@code sectionsWithGeometryIterator} and steps over the rest
	 * ({@code render/chunk/DefaultChunkRenderer}), and it is that count Iris puts on the F3 screen:
	 * it takes {@code getSectionStatistics} inside its own shadow render list scope
	 * ({@code shadows/ShadowRenderer.java:606}, the scope opened at {@code :477}), which Sodium
	 * answers from {@code getSectionsWithGeometryCount} and not from the list's size
	 * ({@code render/chunk/RenderSectionManager.getVisibleChunkCount}).
	 * <p>
	 * What the two differ by is narrow, and naming it is what says how far apart the numbers may
	 * stand. A render list holds every section with anything to render at all, block entities and
	 * animated sprites counted in ({@code RenderSectionFlags.MASK_NEEDS_RENDER}); one bit of that
	 * mask is what reaches a draw. So a section with nothing in it is on neither side of the
	 * comparison, and the gap is the sections whose only content is a block entity or an animated
	 * sprite.
	 * <p>
	 * One pass and not two, where Iris pays one for the single count it shows: the two sums walk
	 * the same handful of per region lists, so asking for them apart would double a walk this
	 * stage now makes on every frame.
	 */
	private static void count(SortedRenderLists lists) {
		int kept = 0;
		int drawn = 0;
		var iterator = lists.iterator(false);
		while (iterator.hasNext()) {
			ChunkRenderList list = iterator.next();
			kept += list.size();
			drawn += list.getSectionsWithGeometryCount();
		}

		walkKept = kept;
		walkDrawn = drawn;
	}

	/**
	 * The last walk for the light, or null where none has run under the pack now loaded.
	 *
	 * @param kept    every section with something to render that the walk kept
	 * @param drawn   those of them carrying block geometry, which is the count Iris shows
	 * @param total   every section loaded, which is the denominator Sodium's own line carries
	 * @param terrain whether the pack takes the world's own geometry into its map at all. The walk
	 *                runs either way, the entities and the far terrain being decided apart from it,
	 *                so a count without this flag beside it would announce sections that no draw
	 *                ever reads. Iris says the same thing in the same place, {@code (no terrain)}
	 *                appended to its own line ({@code shadows/ShadowRenderer.java:776})
	 * @param culling the shape the walk measured against, as the overlay token
	 */
	public record Walk(int kept, int drawn, int total, boolean terrain, String culling) {
	}

	/** The last walk, for the one line that shows it. */
	public static @Nullable Walk lastWalk() {
		return walkCulling == null ? null
				: new Walk(walkKept, walkDrawn, walkTotal, walkTerrain, walkCulling);
	}
}
