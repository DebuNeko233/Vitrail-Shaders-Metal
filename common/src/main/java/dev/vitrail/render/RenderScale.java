package dev.vitrail.render;

import dev.vitrail.mixin.access.LevelRendererOutlineAccessor;
import dev.vitrail.mixin.access.RenderTargetAccessor;
import dev.vitrail.settings.PackFile;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * Renders the world at a fraction of the window and upscales the finished image before the
 * interface: the render scale of the video settings page, stored in {@code vitrail/pack.txt}
 * beside the shadow distance and applied while a pack draws.
 * <p>
 * <strong>The whole feature is the size of the game's main render target.</strong> Everything that
 * has a screen-sized life keys itself off that one number each frame rather than off the window:
 * the frame graph describes its translucency targets from it, this engine allocates the pack's
 * colour targets and its depth images from it, and {@code viewWidth} is read from it, so handing
 * the world phase a smaller main target scales the whole frame in one move and no consumer needs
 * to be told. What is swapped is the four texture fields <em>inside</em> the one target object,
 * never the object itself: the sky renderer holds the object by reference across frames, and a
 * second object would leave it drawing into the window-sized image while everything else had
 * moved.
 * <p>
 * The swap is bracketed inside one frame by the two clears of {@code GameRenderer.render}. It goes
 * in where the game clears the main target for the world, so the world phase runs scaled from its
 * first write; it comes out where the game clears the depth for the interface, once the level and
 * everything after it (the entity outline blit, the spectator post effect) are done. Between the
 * restore and that second clear this class draws the scaled image up onto the window-sized colour
 * texture, so the interface lands on a full-resolution picture and the present blit, which copies
 * texel for texel and stretches nothing, is handed the size it expects.
 * <p>
 * The upscale is MetalFX's spatial scaler, which the backend reaches through {@link ScaleCommands}:
 * one encode from the picture drawn small straight into the game's own colour texture, where this
 * engine used to run FSR 1.0's EASU upsample and RCAS sharpen as two passes of its own. A device that
 * cannot run the scaler - an older system, a GPU that refuses it, a backend that is not the one this
 * engine is built for - falls back to a plain bilinear pass, and a driver that refuses even that
 * disengages the scale for the session rather than leave the interface over a stale image.
 * <p>
 * Two neighbours are handled by hand because they do not follow the main target within a frame.
 * The entity outline target is the one screen-sized target allocated outside the frame's resource
 * pool, so it is resized alongside and handed back to the window when the scale stands down. And
 * the panorama capture, which renders the world at 4096 square, never passes through
 * {@code render} at all, so the swap never sees it and its frames stay full size.
 * <p>
 * What deliberately keeps the window's numbers is the game's global settings block, written once
 * per frame with the window's size: its known reader, the interface blur, runs after the restore
 * and reads the size it should, and rewriting the block around the world phase would spend a
 * buffer write per frame to correct readers this engine has not seen. Named here so that a
 * consumer found later starts from the fact rather than from the picture.
 */
public final class RenderScale {

	/** The range is the file's, so that the slider, the file and this class hold one number. */
	private static final int SMALLEST = PackFile.MIN_RENDER_SCALE;
	private static final int WHOLE = PackFile.MAX_RENDER_SCALE;

	private static final String UPSCALE_LABEL = "Vitrail upscale";

	private static final String SAMPLER = "InSampler";

	/** Two triangles, the quad every full screen pass of this engine draws. */
	private static final int VERTICES = 6;
	private static final float[] QUAD = {
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 1.0F, 0.0F, 0.0F, 1.0F };

	/** The main target's own format, which is what both output passes attach. */
	private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(Vitrail.MOD_ID, "scale/upscale_vertex");
	private static final Identifier BLIT_ID =
			Identifier.fromNamespaceAndPath(Vitrail.MOD_ID, "scale/blit_fragment");

	private static final String VERTEX = """
			#version 460 core

			in vec3 Position;
			in vec2 UV0;

			out vec2 ofTexCoord;

			void main() {
				ofTexCoord = UV0;
				gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

	/**
	 * The fallback when MetalFX cannot run: one bilinear read, which is what the image
	 * would have been under a plain stretch. Chosen over disengaging because the frame that learns
	 * of the refusal has already rendered the world small, and something has to put a picture
	 * under the interface.
	 */
	private static final String BLIT = """
			#version 460 core

			uniform sampler2D InSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				ofFragData0 = vec4(texture(InSampler, ofTexCoord).rgb, 1.0);
			}
			""";

	private static final ShaderSource SOURCE = (id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			return BLIT_ID.equals(id) ? BLIT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	};

	/**
	 * One of the three pipelines, with the latch a refusal raises. The compiled form lives in the
	 * device cache, which the game empties at every resource reload, so {@link #get} offers the
	 * pipeline to the device on every use rather than trusting a flag of its own: that call is a
	 * {@code computeIfAbsent} on the device side and costs nothing once it has been made.
	 */
	private static final class Pass {

		private final Identifier fragment;
		private RenderPipeline pipeline;
		private boolean refused;

		private Pass(Identifier fragment) {
			this.fragment = fragment;
		}

		private RenderPipeline get(GpuDevice device) {
			if (this.refused) {
				return null;
			}

			if (this.pipeline == null) {
				this.pipeline = build(this.fragment);
			}

			try {
				if (device.precompilePipeline(this.pipeline, SOURCE).isValid()) {
					return this.pipeline;
				}

				Vitrail.logger().error("The {} pass of the render scale did not compile",
						this.fragment.getPath());
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				// A stage the driver refuses throws out of precompilePipeline rather than coming back
				// invalid, which is what MoltenVK does with one Metal will not build, and endWorld
				// runs outside every catch of the frame. The same latch as an invalid pipeline, so the
				// fallback and the abandonment below it take over from here.
				Vitrail.logger().error("The {} pass of the render scale did not compile",
						this.fragment.getPath(), e);
			}

			this.refused = true;
			this.pipeline = null;

			return null;
		}
	}

	private static final Pass BILINEAR = new Pass(BLIT_ID);

	/**
	 * What the player asked for, as a percentage of the window; {@link #WHOLE} disengages.
	 * Volatile because {@link #wanted} is reached from {@code PackChoice.load}, which the loaders
	 * may run on a loading worker, while every reader is the render thread: the cross-thread
	 * statics of that class carry the same word for the same reason.
	 */
	private static volatile int percent = WHOLE;

	/** Whether this frame will render a world at all, said at the head of the frame. */
	private static boolean worldComing;

	/** Whether the main target currently holds the scaled set, which never survives a frame. */
	private static boolean swapped;

	/**
	 * Whether the world being drawn right now is going to be upscaled.
	 * <p>
	 * The one question worth asking for a pass that only earns its cost when something is going to
	 * rebuild the image afterwards. It is the frame's answer and not the player's setting: a
	 * hundred percent, a frame with no world, and an allocation this class refused all read false
	 * here, which is what {@link #swapped} already means.
	 */
	static boolean upscaling() {
		return swapped;
	}

	/** The window-sized set the game allocated, held only while the scaled set stands in for it. */
	private static GpuTexture fullColor;
	private static GpuTextureView fullColorView;
	private static GpuTexture fullDepth;
	private static GpuTextureView fullDepthView;
	private static int fullWidth;
	private static int fullHeight;

	/** The scaled colour and depth the world renders into. Allocated while a scale is active. */
	private static TextureTarget scaled;

	private static GpuBuffer quad;

	/** Whether the entity outline target is at the scaled size and owes the window its own back. */
	private static boolean outlineScaled;

	/** Said once per session: which of the two roads the picture is brought back on. */
	private static boolean saidRoad;

	/**
	 * Latched on an allocation failure at one size, and lifted when the size moves. Volatile for
	 * the reason {@link #percent} is: {@link #wanted} writes it too, from the same callers.
	 */
	private static volatile boolean refusedAtSize;
	private static int refusedWidth;
	private static int refusedHeight;

	private RenderScale() {
	}

	/**
	 * What the player asked for, applied from the next frame: the slider hands it over as it is
	 * moved and every pack load hands it over again from {@code pack.txt}. The number is the
	 * player's whatever is loaded; whether it ENGAGES is {@link #beginWorld}'s question, asked per
	 * frame, so no caller has to remember to hand a hundred over when the packs are put away.
	 */
	public static void wanted(int asked) {
		int clamped = Math.clamp(asked, SMALLEST, WHOLE);
		// A new number is a new question, so an allocation refused at the old size does not hold
		// the answer to this one. Lifted here and not in standDown, whose caller is sometimes the
		// very failure that latched it: lifted there, a refused size would be retried every frame.
		if (clamped != percent) {
			refusedAtSize = false;
		}

		percent = clamped;
	}

	/**
	 * Puts the window-sized set back if a frame died between the two clears, and does nothing on
	 * every ordinary frame. Runs at the HEAD of the next frame, and the placement is the whole of
	 * it: the game's own resize check compares the window against the target's fields before the
	 * first clear, so scaled numbers left in them would send that resize through
	 * {@code destroyBuffers} over this class's own textures, and the later restore would then
	 * reinstall the game's handles over a freshly created set nothing ever closes.
	 */
	public static void recover(RenderTarget main) {
		if (swapped && main != null) {
			restore(main);
		}
	}

	/** Whether this frame will render a world, told by the head of {@code render}. */
	public static void frameIntent(boolean rendersWorld) {
		worldComing = rendersWorld;
	}

	/**
	 * Puts the scaled set into the main target for the world phase, and answers whether it did.
	 * Runs where the game clears the main target for the world, so a yes means the clear that
	 * follows lands on the scaled textures and the world never touches the window-sized set.
	 * <p>
	 * Every no leaves the target exactly as the game built it. One case is not guarded because
	 * common code cannot see it: a main target carrying NeoForge's optional stencil aspect gets a
	 * stand-in without one, the stencil field being a loader patch this module does not compile
	 * against, so a mod that renders stencilled world passes has them fail loudly while a scale is
	 * active. No such mod is in any bench; lifting that means asking the loader for the stencil
	 * choice from the NeoForge module alone.
	 */
	public static boolean beginWorld(RenderTarget main) {
		if (main == null) {
			return false;
		}

		// One read of the volatile for the whole decision: the gate and the two sizes have to
		// answer the same number, and three reads of a field another thread writes may not.
		int asked = percent;

		// Every road that answers no from here on stands down, and the reason is the same on each:
		// the outline target follows the scale by hand, so a road that forgets it leaves the
		// outline at a size nothing else will correct. The broken road is here for that reason,
		// or the very failure that stopped scaling would freeze the outline scaled for good; and a
		// world no pack draws stands down with them, the promise of the None road being that the
		// game is left untouched.
		if (asked >= WHOLE || !worldComing || !PackChain.drawingPack() || BILINEAR.refused) {
			standDown(main);

			return false;
		}

		int width = Math.max(1, main.width * asked / WHOLE);
		int height = Math.max(1, main.height * asked / WHOLE);
		if (width >= main.width || height >= main.height) {
			standDown(main);

			return false;
		}

		if (!ensure(width, height, main.width, main.height)) {
			standDown(main);

			return false;
		}

		resizeOutline(width, height, true);

		RenderTargetAccessor accessor = (RenderTargetAccessor) main;
		fullColor = accessor.vitrail$colorTexture();
		fullColorView = accessor.vitrail$colorTextureView();
		fullDepth = accessor.vitrail$depthTexture();
		fullDepthView = accessor.vitrail$depthTextureView();
		fullWidth = main.width;
		fullHeight = main.height;

		RenderTargetAccessor ours = (RenderTargetAccessor) scaled;
		accessor.vitrail$colorTexture(ours.vitrail$colorTexture());
		accessor.vitrail$colorTextureView(ours.vitrail$colorTextureView());
		accessor.vitrail$depthTexture(ours.vitrail$depthTexture());
		accessor.vitrail$depthTextureView(ours.vitrail$depthTextureView());
		main.width = width;
		main.height = height;
		swapped = true;

		return true;
	}

	/**
	 * Puts the window-sized set back and draws the scaled image up onto it, where the game is
	 * about to clear the depth for the interface. Does nothing on the frames {@link #beginWorld}
	 * declined, so the two are safe to call unconditionally from the same frame.
	 * <p>
	 * Must run outside any render pass, which the injection point guarantees: the last pass of
	 * the world (the outline blit or the spectator post effect) has closed by the time the game
	 * reaches that clear.
	 */
	public static void endWorld(RenderTarget main, CommandEncoder encoder) {
		if (!swapped || main == null) {
			return;
		}

		restore(main);

		GpuDevice device = RenderSystem.getDevice();
		GpuTextureView world = ((RenderTargetAccessor) scaled).vitrail$colorTextureView();
		GpuTextureView into = fullColorView(main);

		// MetalFX where this device has a scaler for the pair: one encode from the picture drawn small
		// straight into the game's own colour texture, which is the whole of bringing it back. The
		// capability answers false wherever it cannot be done - no framework, no scaler for these sizes
		// and formats, a backend that is not this one - and the blit below is what the frame gets then.
		boolean scaledWithMetalFx = false;
		Object backend = Backends.encoder(encoder);
		if (Backends.capabilities(encoder) instanceof ScaleCommands commands) {
			boolean available = commands.vitrail$metalFxAvailable();
			scaledWithMetalFx = available
					&& commands.vitrail$metalFxScale(world, into, scaled.width, scaled.height);
			if (!saidRoad) {
				saidRoad = true;
				Vitrail.logger().info("The {}% render scale brings the picture back with {}",
						percent, scaledWithMetalFx ? "MetalFX"
								: available ? "a blit: MetalFX refused this frame's pair"
										: "a blit: MetalFX is not available here");
			}
		} else if (!saidRoad) {
			saidRoad = true;
			Vitrail.logger().warn("The {}% render scale brings the picture back with a blit: the "
					+ "backend behind {} carries no scale capability (mipmaps {}), so nothing MetalFX "
					+ "offers can be reached", percent, encoder.getClass().getName(),
					Backends.capabilities(encoder) instanceof MipmapCommands);
		}

		if (scaledWithMetalFx) {
			return;
		}

		RenderPipeline fallback = BILINEAR.get(device);
		if (fallback != null) {
			draw(encoder, device, fallback, world, into, FilterMode.LINEAR, UPSCALE_LABEL);

			return;
		}

		// Nothing compiled at all, so nothing can put the world under the interface: the scale
		// is off for the session, BILINEAR's own latch being what beginWorld refuses on, and this
		// frame's interface stands on whatever the window-sized colour texture held.
		Vitrail.logger().error("The {}% render scale is abandoned for this session: no upscale "
				+ "pipeline compiles, so a world rendered small could never reach the window again",
				percent);
		release();
	}

	/**
	 * Frees everything the scale holds, at the end of the session and while the device is still
	 * alive, which is {@code EngineStages.closeClient}'s moment.
	 * <p>
	 * The swap is put back first, because a session can end MID-SWAP: a throw inside the world
	 * phase unwinds into the client's close with the scaled set still installed, and freeing it
	 * then would have the renderer's own shutdown destroy the same textures a second time, with
	 * the window-sized set leaking unclosed behind them. Where no target can be reached to
	 * restore into, nothing is freed at all: the process is dying, and a leak has no cost a
	 * double close does not double.
	 */
	public static void close() {
		if (swapped) {
			Minecraft minecraft = Minecraft.getInstance();
			RenderTarget main = minecraft == null || minecraft.gameRenderer == null
					? null
					: minecraft.gameRenderer.mainRenderTarget();
			if (main == null) {
				return;
			}

			restore(main);
		}

		release();
		if (quad != null) {
			quad.close();
			quad = null;
		}
	}

	/**
	 * The inactive frame's bookkeeping: the outline target handed back to the window, and the
	 * scaled memory freed the frame the feature stops being asked for rather than kept against a
	 * return that may never come. The pipelines and their latches stay, being a handful of
	 * objects with no image behind them.
	 */
	private static void standDown(RenderTarget main) {
		if (outlineScaled) {
			resizeOutline(main.width, main.height, false);
		}

		release();
	}

	private static void release() {
		if (scaled != null) {
			scaled.destroyBuffers();
			scaled = null;
		}

	}

	/** Puts the game's own textures back into the target's fields, closing nothing. */
	private static void restore(RenderTarget main) {
		RenderTargetAccessor accessor = (RenderTargetAccessor) main;
		accessor.vitrail$colorTexture(fullColor);
		accessor.vitrail$colorTextureView(fullColorView);
		accessor.vitrail$depthTexture(fullDepth);
		accessor.vitrail$depthTextureView(fullDepthView);
		main.width = fullWidth;
		main.height = fullHeight;
		fullColor = null;
		fullColorView = null;
		fullDepth = null;
		fullDepthView = null;
		swapped = false;
	}

	/** The view the upscale writes, read off the target after the restore. */
	private static GpuTextureView fullColorView(RenderTarget main) {
		return ((RenderTargetAccessor) main).vitrail$colorTextureView();
	}

	/**
	 * Makes the scaled target and the intermediate image exist at their sizes, reallocating when
	 * either moved. Runs while the main target holds the game's own set, so the resize below
	 * destroys only textures this class allocated.
	 */
	private static boolean ensure(int width, int height, int outWidth, int outHeight) {
		if (refusedAtSize && (width != refusedWidth || height != refusedHeight)) {
			refusedAtSize = false;
		}

		if (refusedAtSize) {
			return false;
		}

		try {
			if (scaled == null) {
				scaled = new TextureTarget("Vitrail scaled world", width, height, true, FORMAT);
				announce(width, height, outWidth, outHeight);
			} else if (scaled.width != width || scaled.height != height) {
				scaled.resize(width, height);
				announce(width, height, outWidth, outHeight);
			}

		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			refusedAtSize = true;
			refusedWidth = width;
			refusedHeight = height;
			release();
			Vitrail.logger().error("Vitrail could not allocate the scaled world at {}x{}, so the "
					+ "{}% render scale waits for the window to be another size", width, height,
					percent, e);

			return false;
		}

		return true;
	}

	private static void announce(int width, int height, int outWidth, int outHeight) {
		Vitrail.logger().info("The world renders at {}x{} for a {}x{} window, render scale {}%",
				width, height, outWidth, outHeight, percent);
	}

	/**
	 * Brings the entity outline target to the given size. It is the one screen-sized target of
	 * the world allocated once rather than described from the main target each frame, so it does
	 * not follow the swap on its own; left at the window's size, the outline would be drawn into
	 * a corner of it and blitted back over the whole world. The game sets it back to the window
	 * whenever the window moves, which is why this is asked every scaled frame rather than once.
	 */
	private static void resizeOutline(int width, int height, boolean scaledNow) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.levelRenderer == null) {
			return;
		}

		RenderTarget outline = ((LevelRendererOutlineAccessor) minecraft.levelRenderer)
				.vitrail$entityOutlineTarget();
		if (outline != null && (outline.width != width || outline.height != height)) {
			outline.resize(width, height);
		}

		outlineScaled = scaledNow;
	}

	/** One full screen pass: sample one image whole, write another whole. */
	private static void draw(CommandEncoder encoder, GpuDevice device, RenderPipeline pipeline,
			GpuTextureView from, GpuTextureView into, FilterMode filter, String label) {
		try (RenderPass pass = encoder.createRenderPass(() -> label, into, Optional.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad(device).slice());
			pass.bindTexture(SAMPLER, from,
					RenderSystem.getSamplerCache().getClampToEdge(filter));
			pass.draw(VERTICES, 1, 0, 0);
		}
	}

	private static RenderPipeline build(Identifier fragment) {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(Vitrail.MOD_ID,
						"pipeline/" + fragment.getPath().replace('/', '_')))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(fragment)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(BindGroupLayout.builder().withSampler(SAMPLER).build())
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	private static GpuBuffer quad(GpuDevice device) {
		if (quad == null) {
			ByteBuffer vertices = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
					.order(ByteOrder.nativeOrder());
			vertices.asFloatBuffer().put(QUAD);
			quad = device.createBuffer(() -> "Vitrail upscale quad",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
		}

		return quad;
	}
}
