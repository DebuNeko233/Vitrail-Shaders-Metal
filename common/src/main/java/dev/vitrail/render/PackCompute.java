package dev.vitrail.render;

import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.pack.model.ProgramNames;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.pack.model.RenderStage;
import dev.vitrail.pack.model.TargetName;
import dev.vitrail.pack.model.TextureStage;
import dev.vitrail.pack.source.OpenedPack;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.pack.target.TargetSchedule;
import dev.vitrail.render.compute.ComputeCommands;
import dev.vitrail.render.compute.ComputeDeviceBackend;
import dev.vitrail.render.storage.GpuRecording;
import dev.vitrail.uniform.ClipSpace;
import dev.vitrail.uniform.UniformCatalog;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pack's compute passes: the shadow computes, dispatched at the head of the frame, and the
 * computes hanging off a full screen pass, dispatched right before that pass.
 * <p>
 * The shadow computes run in the parity of the gbuffers that read what they propagate; the
 * volumes they read are the previous frame's shadow-geometry writes, one frame late like the
 * shadow map itself. Complementary's floodfill lives in {@code shadowcomp.csh}. Iris runs it
 * inside its shadow render ({@code ShadowRenderer.java:632-633}, the debug group and the
 * {@code compositeRenderer.renderAll()} under it), before its gbuffers in the SAME frame. Under
 * this engine's deferred shadow stage, the head of the frame is that moment's translation.
 * <p>
 * The chained computes, {@code deferred4_a.csh} for {@code deferred4}, run where Iris runs them:
 * in a loop right before their pass, with a memory barrier after ({@code CompositeRenderer.java:289-300}),
 * reading and storing the colour targets on the halves that pass reads. Photon builds its sky
 * lighting in one, and without it everything in shadow was black.
 * <p>
 * The Java facade has no compute, so the road goes through the seam and there is only one of it:
 * shaderc kind 2 turns the translated stage into SPIR-V, {@link BackendComputePass} hands those
 * bytes to the backend that compiles them, and the backend's own reflection says which names its
 * resources carry, so a pack's names are resolved to Minecraft facade buffers, views and samplers
 * rather than to native handles. The scheduling, the resource policy and the dispatch moments are
 * this class's; the pipeline, the binding and the fences are the backend's. A device that does not
 * serve that seam is refused by name at the dispatch rather than passed over in silence, because
 * there is no second road to run these computes on.
 * <p>
 * One name a compute reads is resolved without the compute ever being asked: which
 * {@code shadowcolor} buffers get allocated is read off the FRAGMENT stages of a place alone
 * ({@code TargetPlan.read} fills its shadow names there and nowhere else), so a shadow colour that
 * only a COMPUTE of the place names is never opened and the compute is handed the white stand-in
 * for it. Iris opens it from the compute itself, {@code addShadowSamplers} calling
 * {@code createIfEmpty} for each shadowcolor the program declares, on the compute path as on the
 * composite one ({@code CompositeRenderer.java:465}, {@code samplers/IrisSamplers.java:156-164}).
 * Nothing of the corpus asks for it: every shadowcolor a compute of it names is named by a
 * fragment stage of the same place too.
 *
 * @see <a href="https://github.com/IrisShaders/Iris">Iris ComputeProgram, LGPL-3.0</a>
 */
final class PackCompute implements AutoCloseable {

	/** The pattern of a colour target written as an image, {@code colorimg4} for {@code colortex4}. */
	private static final Pattern COLOUR_IMAGE = Pattern.compile("\\bcolorimg(\\d+)\\b");

	/**
	 * Setup sees the initial, unflipped side of every colour target. Iris builds setup with an
	 * empty flipped set and runs it after the full clear, before any begin or geometry program.
	 */
	private static final TargetSchedule.Bound SETUP_STEP =
			new TargetSchedule.Bound("setup", List.of(), true, Set.of(), Set.of());

	/** The setup computes, dispatched once after target allocation and again after a resize. */
	private final List<Pass> setup;

	/** The shadow computes, dispatched at the head of the frame. */
	private final List<Pass> passes;

	/**
	 * The computes hanging off a full screen pass, by that pass, each list in letter order. Iris
	 * dispatches them right before the pass ({@code CompositeRenderer.java:289-300}, the loop over
	 * {@code compositePass.computes} with a memory barrier after), and so does the chain here.
	 */
	private final Map<String, List<Pass>> chained;

	/**
	 * The same, by the program they hang off, for the programs this place draws no pass for: the
	 * pack ships less than both halves of the program, or it switched the program off itself.
	 * Nothing draws them, so there is no pass to run these before and they are dispatched at that
	 * program's own moment in the frame, which is where Iris runs them: a stage entry with computes
	 * and no valid source becomes a pass of computes alone, at its own index of the stage
	 * ({@code CompositeRenderer.java:137-145}), dispatched and barriered like any other and then
	 * skipped before the draw ({@code :304-307}).
	 */
	private final Map<String, List<Pass>> alone;

	/** The targets some compute writes as {@code colorimgN}, which are created writable for it. */
	private final Set<Integer> storageTargets;

	private boolean announced;

	/** The passes whose computes have been announced once, which is once per pass and not per frame. */
	private final Set<String> announcedChains = new LinkedHashSet<>();

	private PackCompute(List<Pass> setup, List<Pass> passes, Map<String, List<Pass>> chained,
			Map<String, List<Pass>> alone, Set<Integer> storageTargets) {
		this.setup = List.copyOf(setup);
		this.passes = List.copyOf(passes);
		this.chained = Map.copyOf(chained);
		this.alone = Map.copyOf(alone);
		this.storageTargets = Set.copyOf(storageTargets);
	}

	static PackCompute none() {
		return new PackCompute(List.of(), List.of(), Map.of(), Map.of(), Set.of());
	}

	/** The targets to create writable from a compute, read before the first allocation. */
	Set<Integer> storageTargets() {
		return this.storageTargets;
	}

	/** Whether this pack has setup work that must run after a full target allocation/clear. */
	boolean hasSetup() {
		return !this.setup.isEmpty();
	}

	/** Whether any compute hangs off that full screen pass. */
	boolean hangsOff(String program) {
		return this.chained.containsKey(program);
	}

	/**
	 * Whether a compute hanging off a full screen pass samples {@code centerDepthSmooth}, keyed
	 * like the passes on the declaration surviving the translation. Such a compute is handed the
	 * texel the same way its pass is, so a chain whose only reader is one has to fold it the same
	 * way: Iris arms the fold from its compute builder as it does from a composite
	 * ({@code pipeline/CompositeRenderer.java:470}).
	 * <p>
	 * The shadow computes do not count. They run at the head of the frame, before the opaque
	 * world whose depth the fold takes exists, and Iris hands them no such sampler at all: its
	 * shadow builder ({@code pipeline/IrisRenderingPipeline.java:544},
	 * {@code createShadowComputes}) gives them the targets, the custom textures and images, the
	 * level samplers, the noise and the shadow set, and the centre depth is bound nowhere but in
	 * the composite and final builders. A shadow compute declaring the name reads here what the
	 * fold last left, or white where nothing arms it, which is nearer what it reads under Iris
	 * than a fold of its own would be.
	 */
	boolean readsCenterDepth() {
		return readsCenterDepth(this.chained) || readsCenterDepth(this.alone);
	}

	private static boolean readsCenterDepth(Map<String, List<Pass>> computes) {
		for (List<Pass> passes : computes.values()) {
			for (Pass pass : passes) {
				for (TranslatedUnit.Uniform sampler : pass.compute.loaded().program().samplers()) {
					if (sampler.name().equals(SamplerPlan.centerDepth())) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Reads every compute the pack ships and the plan kept, out of the opening the load already
	 * holds: the shadow computes for the head of the frame, and the computes hanging off a pass of
	 * the chain for the moment before that pass.
	 * <p>
	 * The opening is handed in and not taken here, and this loop is why it matters: read a program
	 * at a time from a pack path, each turn mounted the archive again and walked every source file
	 * of it to rebuild the same index of the same settings, so a pack with four computes paid for
	 * four whole readings of itself to translate four files.
	 *
	 * @param running the programs the chain draws, which is where a chained compute can hang
	 * @param passing the programs this place draws no pass for, the pack having shipped less than
	 *                both halves of them or switched them off itself. They draw nothing and so have
	 *                no pass to hang a compute off; their computes are kept all the same and
	 *                dispatched at that program's own moment in the frame, as Iris runs them
	 *                ({@code CompositeRenderer.java:137-145}). Asked before {@code running}, since a
	 *                program shipped with one half only is in both. A program in neither set has its
	 *                computes left where they were, and the plan's notes carry the reason
	 */
	static PackCompute load(OpenedPack pack, String place, List<String> computes, int load,
			UniformCatalog shadowCatalog, UniformCatalog chainCatalog, Set<String> running,
			Set<String> passing) {
		List<Pass> setup = new ArrayList<>();
		List<Pass> passes = new ArrayList<>();
		Map<String, List<Pass>> chained = new LinkedHashMap<>();
		Map<String, List<Pass>> alone = new LinkedHashMap<>();
		Set<Integer> storageTargets = new LinkedHashSet<>();
		for (String name : computes) {
			String family = ProgramNames.familyOf(name);
			boolean setupCompute = family.equals("setup");
			boolean shadow = ProgramNames.shadowComposite(family);
			Optional<String> base = ProgramNames.computeBase(name);
			if (!shadow && !setupCompute && base.isEmpty()) {
				continue;
			}

			// A program the place merely draws no pass for was never asked about: it is the
			// reference's own invalid source, and its computes run there, so they run here. What is
			// left over is a program taken out of the frame on purpose, by the pass filter at the
			// user's ask or by this engine refusing a sampler it cannot bind, and a final nothing
			// draws, whose computes the reference builds with the final and never without it. The
			// plan tells the three apart in its notes; here they take the same road.
			boolean standalone = !shadow && !setupCompute && passing.contains(base.get());
			if (!shadow && !setupCompute && !standalone && !running.contains(base.get())) {
				Vitrail.logger().warn("compute {} is not dispatched: nothing of this chain runs {}, "
						+ "and the plan's notes say what took it out", name, base.get());
				continue;
			}

			String path = place.isEmpty() ? name : place + "/" + name;
			try {
				Optional<PackProgram.Compute> compute = PackProgram.loadCompute(pack, path);
				if (compute.isEmpty()) {
					continue;
				}

				// Ahead of the roads parting and once for the program: any of the three can be
				// missing a word, and a word means the same on all of them, that the directive it
				// belongs to is read as absent. What that absence costs is the size said below.
				// Said here rather than beside a size, where a word off a road the dispatch never
				// took would read as the reason for a size that was read correctly.
				List<String> unread = compute.get().unresolved();
				if (!unread.isEmpty()) {
					Vitrail.logger().info("compute {} reads no number for {}, so that directive "
							+ "counts as absent", path, String.join(", ", unread));
				}

				// Left undispatched rather than dispatched at a guessed size. A program on one of
				// the screen roads is sized by dividing the screen by its own local size, and a
				// local size this engine cannot read as a number would have to be invented: read
				// as one where the shader means sixteen, the guess asks for a group per pixel and
				// stalls the frame instead of drawing it wrong.
				if (!compute.get().sized()) {
					Vitrail.logger().warn("compute {} is not dispatched: it leaves its work "
							+ "group count to the engine and writes its local size as something "
							+ "other than a number, so how much work it asks for cannot be read",
							path);
					continue;
				}

				String program = setupCompute
						? ProgramNames.parse(name).map(ProgramNames.ProgramName::baseName).orElse(name)
						: shadow ? null : base.get();
				Pass pass = new Pass(compute.get(), shadow ? shadowCatalog : chainCatalog, load,
						path, program);
				if (setupCompute) {
					setup.add(pass);
					storageTargets.addAll(colourImagesOf(compute.get()));
					Vitrail.logger().info("Loaded setup compute {} ({})", path, sizing(compute.get()));
				} else if (shadow) {
					passes.add(pass);
					Vitrail.logger().info("Loaded shadow compute {} ({})", path, sizing(compute.get()));
				} else if (standalone) {
					alone.computeIfAbsent(base.get(), _ -> new ArrayList<>()).add(pass);
					storageTargets.addAll(colourImagesOf(compute.get()));
					Vitrail.logger().info("Loaded compute {} at the moment of {}, which draws "
							+ "nothing here ({})", path, base.get(), sizing(compute.get()));
				} else {
					chained.computeIfAbsent(base.get(), _ -> new ArrayList<>()).add(pass);
					storageTargets.addAll(colourImagesOf(compute.get()));
					Vitrail.logger().info("Loaded compute {} before {} ({})", path, base.get(),
							sizing(compute.get()));
				}

				// The one binding the pack's own text cannot be read for, said here for a compute
				// as PackPass.describe says it for a pass. Past all three roads rather than on one
				// of them, and once per program: a compute of a full screen family takes the
				// default whether or not the program it hangs off draws anything, and Pegasus,
				// whose prepare stage is four computes and no pass, would never have said so.
				List<String> defaulted = compute.get().loaded().samplers().defaulted();
				if (!defaulted.isEmpty()) {
					Vitrail.logger().info("{} reads {} by default under {}", path,
							TargetName.canonical(SamplerPlan.DEFAULT_TARGET),
							String.join(", ", defaulted));
				}
			} catch (IOException | RuntimeException e) {
				Vitrail.logger().warn("compute {} could not be translated: {}", path, e.toString());
			}
		}

		setup.sort(Comparator
				.comparing((Pass pass) -> pass.program, ProgramNames.frameOrder())
				.thenComparing(pass -> ProgramNames.computeLetter(pass.name)));
		chained.values().forEach(list -> list.sort(
				Comparator.comparing(pass -> ProgramNames.computeLetter(pass.name))));
		alone.values().forEach(list -> list.sort(
				Comparator.comparing(pass -> ProgramNames.computeLetter(pass.name))));

		return new PackCompute(setup, passes, chained, alone, storageTargets);
	}

	/** The colour targets a compute names as an image, read off its translated text. */
	private static Set<Integer> colourImagesOf(PackProgram.Compute compute) {
		Set<Integer> indices = new LinkedHashSet<>();
		TranslatedUnit unit = compute.loaded().program().stages().get(ProgramStage.COMPUTE);
		if (unit == null) {
			return indices;
		}

		Matcher matcher = COLOUR_IMAGE.matcher(unit.text());
		while (matcher.find()) {
			indices.add(Integer.parseInt(matcher.group(1)));
		}

		return indices;
	}

	/**
	 * Runs the setup computes after the targets have been fully allocated and cleared. Iris runs
	 * setup at exactly that resource-lifecycle boundary and repeats it only after a target resize.
	 * The caller has already flushed deferred clears, so a later render-pass load operation cannot
	 * erase an imageStore performed here.
	 * <p>
	 * Setup uses the initial MAIN side of every colour target and a logical dispatch size of 1x1,
	 * matching Iris's {@code program.dispatch(1, 1)}. This remains one ordered command stream: any
	 * render/blit encoder used for the clear is ended before the backend's compute encoder starts,
	 * so the ordering a barrier used to spell out is the encoder boundary the seam already owns.
	 */
	void dispatchSetup(CommandEncoder encoder, GpuDevice device, PackValues values,
			ColorTargets targets) {
		if (this.setup.isEmpty() || !targets.usable()) {
			return;
		}

		values.modelView(null, null);
		values.projection(null);
		values.passColour(null);
		values.renderStage(RenderStage.NONE);
		dispatchChain(this.setup, "setup", encoder, device, values, targets, SETUP_STEP,
				null, null, 1, 1);
	}

	/**
	 * Dispatches the computes hanging off that full screen pass, right before it, as Iris does.
	 * Nothing happens for a pass with none, which is every pass of most packs.
	 *
	 * @param step    the halves that pass reads and writes, which is where its computes read a
	 *                colour target from and write one to: Iris binds both the sampler and the
	 *                image on the flipped state of that moment
	 *                ({@code IrisImages.addRenderTargetImages})
	 * @param depth   what that pass reads as {@code depthtex0}, already in the pack's own window,
	 *                and so what its computes read under the same name: Iris hands a compute the
	 *                three depth names of the pass it hangs off through the same
	 *                {@code IrisSamplers.addCompositeSamplers} call
	 *                ({@code pipeline/CompositeRenderer.java:462} against {@code :406}), and the
	 *                far terrain's through the same {@code addRenderTargetSamplers}
	 *                ({@code :454} against {@code :398})
	 * @param distant what that pass reads as {@code dhDepthTex0}, on the same split, or null for
	 *                the far plane on the frames the pack drew no far terrain
	 */
	void dispatchBefore(String program, CommandEncoder encoder, GpuDevice device, PackValues values,
			ColorTargets targets, TargetSchedule.Bound step, GpuTextureView depth,
			GpuTextureView distant, int width, int height) {
		dispatchChain(this.chained.get(program), program, encoder, device, values, targets, step,
				depth, distant, width, height);
	}

	/**
	 * Dispatches the computes hanging off a program this place draws no pass for, at the moment
	 * that program would have run, which is what Iris does with them. Nothing happens for a name
	 * with none, which is every name of nearly every pack.
	 * <p>
	 * The same road as a chained dispatch and deliberately so: Iris builds one kind of compute for
	 * both and runs them in the same loop, the only difference being that the pass around them
	 * draws nothing and is left where the others set their state up
	 * ({@code CompositeRenderer.java:304-307}). What is not the same is where the halves come from,
	 * and the caller answers that: {@link TargetSchedule#passing} rather than the step of a pass,
	 * since there is no pass.
	 */
	void dispatchAlone(String program, CommandEncoder encoder, GpuDevice device, PackValues values,
			ColorTargets targets, TargetSchedule.Bound step, GpuTextureView depth,
			GpuTextureView distant, int width, int height) {
		dispatchChain(this.alone.get(program), program, encoder, device, values, targets, step,
				depth, distant, width, height);
	}

	/** The programs {@link #dispatchAlone} answers for, which the chain has to find a moment for. */
	Set<String> standingAlone() {
		return this.alone.keySet();
	}

	private void dispatchChain(List<Pass> attached, String program, CommandEncoder encoder,
			GpuDevice device, PackValues values, ColorTargets targets, TargetSchedule.Bound step,
			GpuTextureView depth, GpuTextureView distant, int width, int height) {
		if (attached == null || attached.isEmpty()) {
			return;
		}

		// The hold first, as the head-of-frame dispatch does: the first pass of a chain half can
		// still have the geometry's render pass object open over it, and ending the pass
		// underneath would leave that object to close a pass the encoder no longer has.
		GeometryHold.flush(() -> "the compute dispatch at " + program);
		GpuRecording.endPass(encoder);
		BackendRoute backend = backendRoute(encoder, device,
				"the " + attached.size() + " compute pass(es) at " + program);
		for (Pass pass : attached) {
			try {
				pass.dispatch(backend.device(), backend.commands(), values, targets, width, height,
						step, depth, distant);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				if (pass.failed.add(e.toString())) {
					Vitrail.logger().warn("compute {} failed: {}", pass.path, e.toString());
				}
			}
		}

		if (this.announcedChains.add(program)) {
			Vitrail.logger().info("Dispatched {} compute pass(es) at {}", attached.size(), program);
		}
	}

	void dispatch(PackValues values, ColorTargets targets) {
		// A frame the targets refused is one no pass of the chain draws, and this dispatch runs
		// ahead of the call that would refuse it again, and of the first call of all after a
		// load: a compute pushed here would be handed names nothing stands behind, each thrown
		// and said once, over a frame nothing reads.
		if (this.passes.isEmpty() || !targets.usable()) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		// The hold first, and through its own door: it keeps a RenderPass OBJECT open across
		// draws, so ending the pass underneath it would leave that object to close a pass the
		// encoder no longer has, which is a crash at the next flush and not here.
		GeometryHold.flush(() -> "the shadow compute dispatch");
		GpuRecording.endPass(encoder);
		BackendRoute backend = backendRoute(encoder, device,
				"the " + this.passes.size() + " shadow compute pass(es)");
		values.convention(ClipSpace.FORWARD);
		values.modelView(null, null);
		values.projection(null);
		values.passColour(null);
		values.renderStage(RenderStage.NONE);

		// Asked of the game and not of ColorTargets, which is the same number one frame late or
		// nought on the first. ColorTargets.screenWidth is only written by its ensure(), which
		// runs inside the world render, while this dispatch is placed at the head of the frame
		// before any of it: on the first frame after a pack loads the fields are still nought,
		// which is a dispatch of no groups at all, and every resize afterwards would size a frame
		// by the window it had before. Iris asks the same object at the same moment,
		// ShadowCompositeRenderer.java:213-214.
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget();
		int width = main == null ? 0 : main.width;
		int height = main == null ? 0 : main.height;
		for (Pass pass : this.passes) {
			try {
				pass.dispatch(backend.device(), backend.commands(), values, targets, width, height,
						null, null, null);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				if (pass.failed.add(e.toString())) {
					Vitrail.logger().warn("shadow compute {} failed: {}", pass.path, e.toString());
				}
			}
		}

		if (!this.announced) {
			this.announced = true;
			Vitrail.logger().info("Dispatched {} shadow compute pass(es) at the head of the frame",
					this.passes.size());
		}
	}

	/**
	 * Frees every pass this load built: the shadow computes, the ones hanging off a full screen
	 * pass, and the ones dispatched at the moment of a program this place draws no pass for.
	 * <p>
	 * A pass holds a ring of uniform buffers and the backend's opaque pipeline token, and on a
	 * backend that needed the shared-memory fallback the transient buffer its shared variables were
	 * moved into. Every one of those is closed where the pass stands rather than queued for a later
	 * frame: none of them is a native object this engine made, so the backend that owns each one is
	 * the only thing that can free it, and it does so on the call.
	 * The ring is the one that could have been deferred, and it is not: this is not a quiet moment
	 * - every error path of a frame calls {@link PackChain#release} in the middle of one, after the
	 * chain has dispatched these computes, so a pass is closed while frames that still name its
	 * objects are in flight - and the backend's own teardown is what makes that safe.
	 * <p>
	 * Every container of passes declared above has to be named here, and one added later has to be
	 * added here with it: {@link Pass} is private to this class, so a pass this method does not
	 * reach is a pass nothing can reach, and it lives until the process ends.
	 */
	@Override
	public void close() {
		this.setup.forEach(Pass::close);
		this.passes.forEach(Pass::close);
		this.chained.values().forEach(list -> list.forEach(Pass::close));
		this.alone.values().forEach(list -> list.forEach(Pass::close));
	}

	/**
	 * The backend seam this pack's computes run on, or a refusal that names the backend which does
	 * not serve it.
	 * <p>
	 * The two halves are asked separately because they are answered by two objects and one can be
	 * there without the other: a backend may compile a compute pipeline and have no encoder that
	 * accepts a dispatch. Both refusals name the backend and say what could not be dispatched.
	 * That is what keeps this from being the silent no-op a second road used to make of a device it
	 * could not use: a pack whose computes never ran draws with every volume they fill left empty
	 * and would otherwise have nothing in the log saying why.
	 *
	 * @param where what could not be dispatched, named so the line says how much was lost
	 */
	private static BackendRoute backendRoute(CommandEncoder encoder, GpuDevice device, String where) {
		GpuDeviceBackend deviceBackend = ((GpuDeviceAccessor) device).vitrail$backend();
		if (!(deviceBackend instanceof ComputeDeviceBackend computeDevice)) {
			throw new IllegalStateException("The " + device.getDeviceInfo().backendName()
					+ " backend does not serve shader-pack compute, so " + where
					+ " cannot be dispatched, and this engine has no second road to run them on");
		}

		if (!(Backends.capabilities(encoder) instanceof ComputeCommands computeCommands)) {
			throw new IllegalStateException("The " + device.getDeviceInfo().backendName()
					+ " backend compiles a compute pipeline but its command encoder accepts no "
					+ "dispatch, so " + where + " cannot be dispatched");
		}

		return new BackendRoute(computeDevice, computeCommands);
	}

	private record BackendRoute(ComputeDeviceBackend device, ComputeCommands commands) {
	}

	/**
	 * What sizes this program's dispatch, in the pack's own terms. Only the count a pack writes
	 * out for itself is a number before the frame runs: the two roads that go by the screen are
	 * settled at the size the frame is drawn at, so what is named here is the road and the tile of
	 * pixels one group of it covers.
	 */
	private static String sizing(PackProgram.Compute compute) {
		if (compute.fixed()) {
			return compute.groupsX() + "x" + compute.groupsY() + "x" + compute.groupsZ() + " groups";
		}

		String covered = compute.relative()
				? compute.renderX() + " by " + compute.renderY() + " of the screen"
				: "the whole screen";
		return covered + ", " + compute.localX() + "x" + compute.localY() + " pixels to a group";
	}

	/**
	 * One compute of this load: the program it came from, the moment it belongs to, and the
	 * backend-neutral pass that compiles and dispatches it.
	 * <p>
	 * Nothing native is held here. The uniform ring, the SPIR-V, the reflected resource names and
	 * the opaque pipeline token all live in {@link BackendComputePass}, which is the single place
	 * the compute road is implemented, so a second road cannot grow beside this one without
	 * someone deleting the first.
	 */
	private static final class Pass {

		private final PackProgram.Compute compute;
		private final String path;
		private final String name;

		/** The full screen pass this compute hangs off, or null for a shadow compute. */
		private final String program;

		private final BackendComputePass backendPass;
		private final Set<String> failed = new LinkedHashSet<>();

		private Pass(PackProgram.Compute compute, UniformCatalog catalog, int load, String path,
				String program) {
			this.compute = compute;
			this.path = path;
			this.name = path.substring(path.lastIndexOf('/') + 1);
			this.program = program;
			// Which stage a {@code texture.<stage>.<sampler>} line has to name for this compute to
			// read the file behind it. A shadow compute is the {@code shadowcomp} stage by
			// construction: it hangs off no pass, so there is nothing else it could be.
			TextureStage textureStage = program == null
					? TextureStage.SHADOWCOMP
					: TextureStage.of(program).orElse(null);
			String label = "pack/" + load + "/" + path + "/compute";
			this.backendPass = new BackendComputePass(compute,
					new PackUniforms(compute.loaded().program().uniforms(), catalog), path, label,
					program, textureStage);
		}

		private void dispatch(ComputeDeviceBackend deviceBackend, ComputeCommands commands,
				PackValues values, ColorTargets targets, int width, int height,
				TargetSchedule.Bound step, GpuTextureView depth, GpuTextureView distant) {
			this.backendPass.dispatch(deviceBackend, commands, values, targets, width, height,
					step, depth, distant);
		}

		private void close() {
			this.backendPass.close();
		}
	}
}
