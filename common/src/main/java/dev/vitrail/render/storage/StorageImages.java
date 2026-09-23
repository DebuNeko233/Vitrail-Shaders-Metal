package dev.vitrail.render.storage;

import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.pack.model.ImageInformation;
import dev.vitrail.pack.model.PackTexture;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.vitrail.render.Backends;

/**
 * The storage images a pack declared with {@code image.NAME}.
 * <p>
 * Minecraft 26.2's public texture creation path has no storage bit and cannot distinguish a true
 * three-dimensional texture from array layers. There is exactly one road, and it goes through the
 * backend: a backend implementing {@link StorageImageBackend} allocates the shader-writable texture
 * and keeps it inside Minecraft's {@link GpuTexture}/{@link GpuTextureView} facade, and the same
 * backend's encoder records the clears and the exact region copies through
 * {@link StorageImageCommands}. Nothing native crosses the seam and nothing here names a handle.
 * The pack-facing policy is shared: clear timing, persistence, relative sizing, camera following
 * and scratch-copy decisions are made here and not in a backend.
 * <p>
 * A backend that cannot serve one of those operations refuses explicitly, and the refusal reaches
 * the colour targets as the frame's own failure. A declared name quietly left unserved would be a
 * shader reading whatever the allocator handed back, which is a wrong picture rather than a
 * missing optimisation.
 *
 * @see <a href="https://github.com/IrisShaders/Iris">Iris GlImage, LGPL-3.0</a>
 */
public final class StorageImages implements AutoCloseable {

	private static volatile StorageImages current = none();

	/**
	 * Whether a volume the pack does NOT ask to clear is emptied once, when it is created.
	 * <p>
	 * <strong>What it is for.</strong> A volume is created with undefined contents, and the ones
	 * the pack marks {@code clear} are emptied by {@link #clearMarked} at the head of every shadow
	 * stage. The others are the ones a pack carries FORWARD, and the assumption written here was
	 * that the pack's own compute writes them whole on its first dispatch. That holds for a
	 * floodfill written from nothing. It does not hold for one that PROPAGATES: Photon's
	 * {@code light_img_a} and {@code light_img_b} are read and written in turn, so its first
	 * dispatch reads whatever the allocator handed back, and that is what the light carries until
	 * enough frames have overwritten it. Measured 7 September 2026: after a pack reload the world
	 * comes back with red and blue at nought for about thirty seconds, on eight reloads out of ten,
	 * and not once with the pack's coloured lighting turned off.
	 *
	 * @see #CLEAR_AT_BIRTH_BUDGET for why this is not simply done for every volume of every pass
	 */
	private static final boolean CLEAR_AT_BIRTH = Boolean.parseBoolean(
			System.getProperty("vitrail.clearStorageAtBirth", "true"));

	/**
	 * How many bytes of emptying one allocation pass may record, and the reason the emptying is
	 * bounded rather than universal.
	 * <p>
	 * <strong>It is a budget for the PASS and not a size for one volume, because that is the shape
	 * of what it guards.</strong> What went wrong the day the device was lost went wrong on a
	 * command buffer, not inside an image: what a buffer carries is the sum of what was recorded
	 * into it. A per volume bound cannot say that, and it let a pack declaring several volumes each
	 * just under it record their total with nothing to stop it. Reverie declares four such volumes
	 * and BSL two.
	 * <p>
	 * <strong>Two comments of this engine disagree on why that device was lost, and this budget
	 * takes the cautious side of both rather than choosing.</strong> One blamed the SIZE, a five
	 * hundred mebibyte clear beside a 773 MiB storage-buffer fill on one command buffer; the other
	 * blamed a fence that a transfer never recorded behind it. {@link GpuRecording} is where the
	 * division is written down now - ordering is stated as intent and the backend's own fence chain
	 * carries it, so a backend-native clear has its own ordering - but the same allocation-pass
	 * budget remains the conservative policy until the large-volume case has been measured there
	 * too.
	 * <p>
	 * <strong>What it still cuts, and it stays a debt.</strong> Photon's volumes at its own default
	 * and below fit inside it twice over; at its two largest settings one volume alone is over it,
	 * and there the defect this exists for comes back untouched.
	 */
	private static final long CLEAR_AT_BIRTH_BUDGET = 64L * 1024L * 1024L;

	private final ImageInformation.Reading declared;
	private final List<Allocated> allocated = new ArrayList<>();

	/**
	 * The backend-owned texture view every name a pack may bind resolves to, under both the image
	 * uniform and its optional sampler alias, rebuilt whenever {@link #allocated} changes.
	 */
	private Map<String, GpuTextureView> facadeBindings = Map.of();

	/**
	 * Which of those names is the image uniform rather than a sampler written beside it on the same
	 * {@code image.} line. Both resolve to the very same view, so this is the one thing a binding
	 * layout cannot read off the map: see {@link #storageBinding}.
	 */
	private Set<String> storageNames = Set.of();
	private int lastWidth;
	private int lastHeight;
	private boolean laidOut;

	/**
	 * Whether an image whose size does not follow the screen was refused. Another screen size is
	 * another question for the images that follow it and for the colour targets, and not for
	 * this one: asked again at every size the window passes through, it would fail at each with a
	 * full allocation and a full stack trace behind it.
	 */
	private boolean refusedForGood;

	public StorageImages(ImageInformation.Reading declared) {
		this.declared = declared;
	}

	static StorageImages none() {
		return new StorageImages(ImageInformation.Reading.empty());
	}

	/**
	 * The backend-owned Minecraft view for this name, or null where nothing is allocated under it.
	 * The public {@link com.mojang.blaze3d.systems.RenderPass} seam substitutes this view before the
	 * backend sees the bind, so the pack's own name is the only thing that has to travel.
	 */
	public static @Nullable GpuTextureView facadeView(String name) {
		return current.facadeBindings.get(name);
	}

	/**
	 * Whether this name is the storage image itself rather than a sampler hanging off its line.
	 * <p>
	 * A layout built from the pack's declaration has to tell the two apart: both names answer with
	 * the same view, and only the image uniform is one a shader stores into. The first image that
	 * claims a name answers for it, the image uniform before the sampler on the same line.
	 */
	public static boolean storageBinding(String name) {
		return current.storageNames.contains(name);
	}

	void install() {
		current = this;
	}

	/**
	 * The first image declaring a name answers for it, the image uniform before the sampler on
	 * the same line, which is the order the walk this replaces read them in.
	 */
	private void rebind() {
		Map<String, GpuTextureView> facade = new HashMap<>();
		Set<String> storage = new HashSet<>();
		for (Allocated image : this.allocated) {
			GpuTextureView view = image.facadeView;
			if (view == null) {
				continue;
			}

			// A null answer means the name was free and this image's uniform is what answers for
			// it. A name already taken keeps the image that took it, sampler aliases included.
			if (facade.putIfAbsent(image.declared.name(), view) == null) {
				storage.add(image.declared.name());
			}

			image.declared.sampler().ifPresent(sampler -> facade.putIfAbsent(sampler, view));
		}

		this.facadeBindings = Map.copyOf(facade);
		this.storageNames = Set.copyOf(storage);
	}

	/** Whether the last refusal was of an image no screen size can change, see {@link #ensure}. */
	public boolean refusedForGood() {
		return this.refusedForGood;
	}

	/**
	 * Allocates every absolute image once, and rebuilds the relative ones when the screen moves.
	 * A failure is the whole set given back and the frame refused, see {@link #refused}.
	 * <p>
	 * A device whose backend does not serve {@link StorageImageBackend} is one this engine cannot
	 * draw a pack with at all, and it is told so plainly rather than having the images skipped: the
	 * pack's shaders were compiled against these names, so an engine that cannot allocate them has
	 * no frame to draw.
	 */
	public void ensure(int screenWidth, int screenHeight) {
		install();
		if (this.declared.images().isEmpty()) {
			return;
		}

		boolean first = this.allocated.isEmpty();
		boolean resized = screenWidth != this.lastWidth || screenHeight != this.lastHeight;
		if (!first && !resized) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		GpuDeviceBackend deviceBackend = ((GpuDeviceAccessor) device).vitrail$backend();
		if (!(deviceBackend instanceof StorageImageBackend storageBackend)) {
			throw new IllegalStateException("The " + device.getDeviceInfo().backendName()
					+ " backend does not serve shader storage images, which "
					+ this.declared.images().size() + " of this pack declares");
		}

		// Everything or nothing: a refusal halfway through gives back what was allocated, so the
		// next screen size the colour targets try again at starts from the first image rather
		// than skipping the one that failed as already dealt with.
		try {
			allocate(device, storageBackend, first, resized, screenWidth, screenHeight);
		} catch (RuntimeException e) {
			this.allocated.forEach(Allocated::close);
			this.allocated.clear();
			this.laidOut = false;
			rebind();
			throw e;
		}

		this.lastWidth = screenWidth;
		this.lastHeight = screenHeight;
		rebind();
		try {
			layoutIfNeeded(device);
		} catch (RuntimeException e) {
			this.allocated.forEach(Allocated::close);
			this.allocated.clear();
			this.laidOut = false;
			rebind();
			throw e;
		}
	}

	private void allocate(GpuDevice device, StorageImageBackend storageBackend, boolean first,
			boolean resized, int screenWidth, int screenHeight) {
		if (first) {
			for (ImageInformation image : this.declared.images()) {
				if (image.relative()) {
					continue;
				}

				boolean movable = movable(image);
				try {
					this.allocated.add(Allocated.create(device, storageBackend, image,
							image.width(), image.height(), Math.max(image.depth(), 1), movable));
					// Which volumes follow the camera and which do not, said once per pack rather
					// than left to be guessed from the picture: a volume left behind keeps a frame
					// of lag at every block crossed, and a volume that follows costs a second
					// image of its own size. Neither shows as itself on screen.
					Vitrail.logger().info("storage image {}{}", image.describe(), movable
							? ", moved onto each frame's camera block for " + scratchCost(image)
									+ " more"
							: image.clear() && image.depth() > 1
									? ", left on the frame that filled it"
									: "");
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {
					this.refusedForGood = true;
					throw refused(image, e);
				}
			}
		}

		if (first || resized) {
			List<Allocated> kept = new ArrayList<>();
			for (Allocated image : this.allocated) {
				if (image.relative) {
					image.close();
					this.laidOut = false;
				} else {
					kept.add(image);
				}
			}

			this.allocated.clear();
			this.allocated.addAll(kept);
			for (ImageInformation image : this.declared.images()) {
				if (!image.relative()) {
					continue;
				}

				int width = Math.max(1, (int) (screenWidth * image.relativeWidth()));
				int height = Math.max(1, (int) (screenHeight * image.relativeHeight()));
				try {
					// Never movable: a relative image is a screen and not a volume, and it goes
					// back and is built again on every resize.
					this.allocated.add(Allocated.create(device, storageBackend, image,
							width, height, 1, false));
					Vitrail.logger().info("storage image {} at {}x{}", image.describe(), width,
							height);
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {
					throw refused(image, e);
				}
			}
		}
	}

	/**
	 * An image the device would not give is the whole frame refused, and not a name skipped.
	 * <p>
	 * The bind group layout of every program naming the image was built off the pack's
	 * declaration, as a storage image, before anything was allocated; the descriptor write
	 * answers off the allocation. A name declared and not allocated would therefore be written
	 * as a sampled image under a layout that says storage, two types under one binding, which
	 * no driver has to survive and none reports. Thrown to the colour targets, which refuse the
	 * frame at this size the way they do for a colour target that could not be allocated.
	 */
	private static IllegalStateException refused(ImageInformation image, RuntimeException cause) {
		return new IllegalStateException("storage image " + image.describe()
				+ " could not be allocated, and a program declaring it cannot be drawn without it",
				cause);
	}

	/**
	 * Empties every image the pack asked to clear, which Complementary's voxel volume is. Called
	 * at the top of the shadow stage, before geometry writes, matching Iris clearing custom images
	 * before the shadow map is drawn.
	 * <p>
	 * Nothing is recorded when no image asks for it, which is most packs: the capability is
	 * demanded only where there is work for it.
	 */
	public void clearMarked(CommandEncoder encoder) {
		GpuRecording.endPass(encoder);
		List<Allocated> marked = new ArrayList<>();
		for (Allocated image : this.allocated) {
			if (image.declared.clear() && image.texture != null) {
				marked.add(image);
			}
		}
		if (marked.isEmpty()) {
			return;
		}

		StorageImageCommands commands = storageCommands(encoder);
		for (Allocated image : marked) {
			clearImage(commands, image);
		}
	}

	/**
	 * What a second image of this one's shape costs, for the line that says a volume follows. In
	 * mebibytes where there is a whole one and in kibibytes below that: the smallest volume the
	 * corpus declares is half a mebibyte, and a line reading "0 MiB more" says the move is free.
	 */
	private static String scratchCost(ImageInformation image) {
		long texels = (long) Math.max(image.width(), 1) * Math.max(image.height(), 1)
				* Math.max(image.depth(), 1);
		long bytes = texels * image.internalFormat().used().bytesPerPixel();

		return bytes >= 1024L * 1024L
				? bytes / (1024L * 1024L) + " MiB"
				: bytes / 1024L + " KiB";
	}

	/**
	 * Whether a volume may be moved by a count of BLOCKS, which is the one thing this class has to
	 * settle before {@link #reanchor} may touch anything: a custom image is a grid whose scale the
	 * pack keeps to itself, and the engine sees only three extents.
	 * <p>
	 * The rule is a volume the pack CLEARS whose three extents match a volume it does NOT clear.
	 * What that buys: an uncleared volume survives frames, so it is the pack that carries it
	 * forward, and it does so by the blocks the camera crossed and nothing else,
	 * {@code pos - (floor(previousCameraPosition) - floor(cameraPosition))} in Complementary's
	 * {@code program/shadowcomp.glsl}. One texel a block, and a cleared volume declared at that same
	 * extent is the identity half of the same grid. Three packs of the corpus are built that way,
	 * each with its identity volume beside the light volumes it feeds: Complementary, BSL and Bliss,
	 * and all three anchor on the fractional part of the camera position plus half the volume, which
	 * only the first of them writes under the {@code cameraPositionBestFract} name.
	 * <p>
	 * <strong>Anything else is left where it is, and the counter-example is in the same pack.</strong>
	 * Complementary's coarse reflection volume is stored at a QUARTER of the voxel position
	 * ({@code lib/voxelization/reflectionVoxelization.glsl}), one cell per four blocks, and it is
	 * cleared each stage exactly like the identity volume. Moved by a block count it would land four
	 * cells out in the direction of travel, which is worse than leaving it untouched, and nothing
	 * in an {@code image.} directive tells the two apart. It has no uncleared twin, so
	 * the rule excludes it.
	 * <p>
	 * <strong>The rule is sufficient and not necessary, and the log says so.</strong> The same
	 * pack's full-scale reflection volume IS one texel a block, and it is left behind at every
	 * setting but the smallest, being the only one where its height happens to equal the floodfill's.
	 * Widening the rule to catch it means guessing from two extents out of three, which is the guess
	 * that would have caught the quarter-scale volume as well.
	 */
	private boolean movable(ImageInformation image) {
		if (!image.clear() || image.relative() || image.depth() <= 1) {
			return false;
		}

		for (ImageInformation other : this.declared.images()) {
			if (!other.clear() && !other.relative() && other.width() == image.width()
					&& other.height() == image.height() && other.depth() == image.depth()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Moves every volume {@link #movable} accepts by whole camera blocks when a writer and reader
	 * nevertheless arrive under different anchors.
	 * <p>
	 * The ordinary shadow path no longer needs that repair: it clears custom images, draws the
	 * voxelising shadow geometry and dispatches {@code shadowcomp} in one frame, matching Iris, so
	 * both sides share the same camera position and any pack-defined view-centred origin. This
	 * operation remains as a backend-neutral safety path for an actual anchor delta; the scale
	 * guard above is still required because not every cleared 3D image is one texel per block.
	 * <p>
	 * The {@code |d|} leading planes keep what they held rather than being emptied. They sit at the
	 * far edge of the volume and are the conservative choice: a stale identity there blocks light
	 * where an emptied one would leak it.
	 * <p>
	 * A volume with no scratch beside it is left where it is and keeps the frame of lag its failed
	 * allocation already logged; a volume whose move the backend refuses ends in
	 * {@link #commandRefused} rather than half moved.
	 */
	public void reanchor(CommandEncoder encoder, int dx, int dy, int dz) {
		if (dx == 0 && dy == 0 && dz == 0) {
			return;
		}

		List<Allocated> movable = new ArrayList<>();
		for (Allocated image : this.allocated) {
			if (image.scratchTexture != null) {
				movable.add(image);
			}
		}
		if (movable.isEmpty()) {
			return;
		}

		GpuRecording.endPass(encoder);
		StorageImageCommands commands = storageCommands(encoder);
		for (Allocated image : movable) {
			// Past the extent nothing of the volume survives the move, which is a teleport
			// rather than a step. Emptied and not moved: the pack's own floodfill reprojection
			// is just as lost there, and identities from the world the player has left would
			// block light all over the one they arrived in.
			if (Math.abs(dx) >= image.width || Math.abs(dy) >= image.height
					|| Math.abs(dz) >= image.depth) {
				clearImage(commands, image);
				continue;
			}

			move(commands, image, dx, dy, dz);
		}
	}

	/**
	 * The move itself, in two copies through the image's own scratch because an in-place overlapping
	 * copy has no portable meaning. The backend carries the same region through a second image;
	 * Vitrail owns the arithmetic and the backend owns how each exact copy is encoded.
	 * <p>
	 * Both copies carry the SAME region, the one the second reads, and the scratch keeps whatever an
	 * earlier move left outside it: no reader of any kind ever names the scratch, so a texel there
	 * that the second copy will not read is a texel the first one has no reason to write. That is
	 * {@code |d|} planes an axis, a plane at a walking pace and a large part of the volume at speed.
	 * <p>
	 * <strong>The dependency between the two copies is not this class's to record, and the road this
	 * replaces recorded one.</strong> The second copy reads what the first has just written, and the
	 * machinery that used to say so - a barrier between two transfers - is gone with the backend it
	 * was written for. {@link StorageImageCommands} offers one verb for an exact region copy and none
	 * for an ordering between two of them, so this method does not order them and cannot claim to.
	 * What makes the pair correct is the capability's own contract, which is written for exactly this
	 * caller: a shift in place is a scratch and two non-overlapping copies, the backend that records
	 * both is the one whose encoder orders the commands inside it, and where that backend cannot
	 * record the copy at all it answers false - which this method turns into a refusal rather than a
	 * volume left half moved.
	 */
	private static void move(StorageImageCommands commands, Allocated image, int dx, int dy, int dz) {
		if (image.texture == null || image.scratchTexture == null) {
			throw commandRefused(image, "copy it through its scratch image");
		}

		int fromX = Math.max(dx, 0);
		int fromY = Math.max(dy, 0);
		int fromZ = Math.max(dz, 0);
		int spanX = image.width - Math.abs(dx);
		int spanY = image.height - Math.abs(dy);
		int spanZ = image.depth - Math.abs(dz);
		boolean carried = commands.vitrail$copyStorageImageRegion(
				image.texture, image.scratchTexture,
				fromX, fromY, fromZ,
				fromX, fromY, fromZ,
				spanX, spanY, spanZ);
		if (!carried) {
			throw commandRefused(image, "copy into its scratch image");
		}

		boolean shifted = commands.vitrail$copyStorageImageRegion(
				image.scratchTexture, image.texture,
				fromX, fromY, fromZ,
				Math.max(-dx, 0), Math.max(-dy, 0), Math.max(-dz, 0),
				spanX, spanY, spanZ);
		if (!shifted) {
			throw commandRefused(image, "copy back from its scratch image");
		}
	}

	private void layoutIfNeeded(GpuDevice device) {
		if (this.laidOut || this.allocated.isEmpty()) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		GpuRecording.endPass(encoder);
		List<Allocated> born = newlyBorn();
		clearBornImages(encoder, born);
		markBornPrepared(born);
		this.laidOut = true;
	}

	/** Newly created allocations only; surviving absolute volumes retain their contents on resize. */
	private List<Allocated> newlyBorn() {
		List<Allocated> born = new ArrayList<>();
		for (Allocated image : this.allocated) {
			if (!image.laidOut) {
				born.add(image);
			}
		}
		return born;
	}

	/** Commits birth preparation only after the backend has recorded it successfully. */
	private static void markBornPrepared(List<Allocated> born) {
		for (Allocated image : born) {
			image.laidOut = true;
		}
	}

	/**
	 * Empties the volumes that have just been created and that nothing else will ever empty, which
	 * is every one the pack did not mark {@code clear}.
	 * <p>
	 * <strong>The zeros are visible to the pack's first dispatch because the backend says so, and
	 * not because a fence is recorded beside them.</strong> There is no image layout to be brought
	 * out of undefined here, the clear goes through {@code vitrail$clearStorageImage} into the same
	 * command stream as the dispatch that reads it, and what a later reader waits on is the
	 * backend's own fence chain - {@link GpuRecording} is where that division is written down. The
	 * decision about WHICH born images receive zeros remains here, in {@link #birthClears}.
	 */
	private void clearBornImages(CommandEncoder encoder, List<Allocated> born) {
		List<Allocated> emptying = birthClears(born);
		if (emptying.isEmpty()) {
			return;
		}

		StorageImageCommands commands = storageCommands(encoder);
		for (Allocated image : emptying) {
			clearImage(commands, image);
		}

		logBornCleared(emptying.size());
	}

	/** Selects birth clears once, independent of how a backend records the resulting zero command. */
	private static List<Allocated> birthClears(List<Allocated> born) {
		List<Allocated> emptying = new ArrayList<>();
		long spent = 0L;
		for (Allocated image : born) {
			// The marked ones are emptied at the head of every shadow stage anyway, so a clear here
			// would be the same write twice in one frame.
			if (image.declared.clear()) {
				continue;
			}

			if (!CLEAR_AT_BIRTH) {
				Vitrail.logger().info("Storage volume {} is left as the allocator handed it back, "
						+ "property=vitrail.clearStorageAtBirth", image.declared.name());
				continue;
			}

			long bytes = bytes(image);
			// Against what this pass has already spent, so the answer is about the command buffer
			// and not about the image. A volume refused here does not stop a smaller one after it:
			// the budget is what the buffer may carry, and a volume that fits in what is left fits.
			if (spent + bytes > CLEAR_AT_BIRTH_BUDGET) {
				Vitrail.logger().info("Storage volume {} is {} MiB and this pass has {} MiB of its "
						+ "{} MiB left, so it is left as the allocator handed it back and a pack "
						+ "reading it before writing it whole reads that",
						image.declared.name(), bytes / (1024L * 1024L),
						(CLEAR_AT_BIRTH_BUDGET - spent) / (1024L * 1024L),
						CLEAR_AT_BIRTH_BUDGET / (1024L * 1024L));
				continue;
			}

			spent += bytes;
			emptying.add(image);
		}
		return emptying;
	}

	private static void logBornCleared(int count) {
		// Said per allocation pass rather than per pack load, which is the same thing for a volume
		// of the pack's own size and is NOT for one sized on the screen: those are destroyed and
		// built again at every resize, so this line follows a window being dragged. The words say
		// "created" and not "carried forward" for that reason.
		Vitrail.logger().info("{} storage volume(s) emptied as they were created, "
				+ "property=vitrail.clearStorageAtBirth", count);
	}

	private static long bytes(Allocated image) {
		return (long) image.width * image.height * image.depth
				* image.declared.internalFormat().used().bytesPerPixel();
	}

	private static void clearImage(StorageImageCommands commands, Allocated image) {
		if (image.texture == null
				|| !commands.vitrail$clearStorageImage(image.texture, dimensions(image.declared.shape()))) {
			throw commandRefused(image, "clear it to zero");
		}
	}

	private static IllegalStateException commandRefused(Allocated image, String operation) {
		return new IllegalStateException("storage image " + image.declared.name()
				+ " was allocated by the active backend but that backend could not " + operation);
	}

	/**
	 * The clear and copy commands of the active encoder, or a refusal that names what was asked of
	 * which backend.
	 * <p>
	 * The capability is asked of the backend behind the encoder wrapper, for the reason
	 * {@link Backends} records: the wrapper is a new object per call and answers no capability ever.
	 * A backend that hands out a storage image and cannot then clear or copy one is not a backend
	 * this engine can keep a pack's volumes on, and saying so is the point: a clear that was quietly
	 * skipped is a pack reading the previous frame, and a skipped copy is a volume standing at the
	 * wrong anchor.
	 */
	private static StorageImageCommands storageCommands(CommandEncoder encoder) {
		Object capabilities = Backends.capabilities(encoder);
		if (capabilities instanceof StorageImageCommands commands) {
			return commands;
		}

		CommandEncoderBackend backend = Backends.encoder(encoder);
		throw new IllegalStateException("the storage images this pack declares need clears and "
				+ "region copies from the active command encoder, and "
				+ (backend == null
						? "this encoder has no backend this engine can ask for them"
						: "the " + backend.getClass().getName() + " backend does not serve them"));
	}

	int count() {
		return this.allocated.size();
	}

	@Override
	public void close() {
		if (current == this) {
			current = none();
		}

		this.allocated.forEach(Allocated::close);
		this.allocated.clear();
		this.facadeBindings = Map.of();
		this.storageNames = Set.of();
		this.lastWidth = 0;
		this.lastHeight = 0;
		this.laidOut = false;
	}

	/**
	 * One allocated image: a backend-owned Minecraft texture behind the game's own facade, with the
	 * scratch beside it for the volumes {@link #reanchor} moves.
	 */
	private static final class Allocated {

		private final ImageInformation declared;
		private final boolean relative;
		private final int width;
		private final int height;
		private final int depth;
		private @Nullable GpuTexture texture;
		private @Nullable GpuTextureView facadeView;

		/**
		 * A second image of the same shape, for the volumes {@link #reanchor} moves, and null for
		 * every other. It carries no view: nothing samples it and nothing stores into it, it is
		 * one end of a copy and no more.
		 */
		private @Nullable GpuTexture scratchTexture;

		/** Whether backend birth preparation for this allocation has already been recorded. */
		private boolean laidOut;

		private Allocated(ImageInformation declared, int width, int height, int depth) {
			this.declared = declared;
			this.relative = declared.relative();
			this.width = width;
			this.height = height;
			this.depth = depth;
		}

		/**
		 * Asks the active backend for the texture, and for the scratch beside it where the volume
		 * may be moved. A failure of the texture is the caller's to turn into a refused frame; a
		 * failure of the scratch is not, because a volume without one keeps the frame of lag it
		 * carried before rather than dropping the pack's lighting.
		 */
		private static Allocated create(GpuDevice device, StorageImageBackend backend,
				ImageInformation declared, int width, int height, int depth, boolean movable) {
			int extentWidth = Math.max(width, 1);
			int extentHeight = Math.max(height, 1);
			int extentDepth = Math.max(depth, 1);
			GpuFormat format = GpuFormat.valueOf(declared.internalFormat().used().name());
			int dimensions = dimensions(declared.shape());
			GpuTexture texture = backend.vitrail$createStorageImage(
					"Vitrail storage image " + declared.name(), format,
					extentWidth, extentHeight, extentDepth, dimensions);
			if (texture == null) {
				throw new IllegalStateException("Backend returned null for storage image "
						+ declared.name());
			}

			GpuTextureView view;
			try {
				view = device.createTextureView(texture);
			} catch (RuntimeException e) {
				texture.close();
				throw e;
			}

			Allocated allocated = new Allocated(declared, extentWidth, extentHeight, extentDepth);
			allocated.texture = texture;
			allocated.facadeView = view;
			if (movable) {
				try {
					allocated.scratchTexture = backend.vitrail$createStorageImage(
							"Vitrail storage image scratch " + declared.name(), format,
							extentWidth, extentHeight, extentDepth, dimensions);
					if (allocated.scratchTexture == null) {
						throw new IllegalStateException("Backend returned null scratch texture");
					}
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {
					Vitrail.logger().warn("storage image {} keeps a frame of lag, its scratch "
							+ "could not be allocated: {}", declared.name(), e.toString());
				}
			}
			return allocated;
		}

		/**
		 * Gives the facades back through their own close path. Nothing native is held here, so there
		 * is no handle of this engine's to defer: the game's own texture release is where an
		 * allocation still named by in-flight work already waits.
		 */
		private void close() {
			GpuTextureView facade = this.facadeView;
			GpuTexture texture = this.texture;
			GpuTexture scratchTexture = this.scratchTexture;
			this.facadeView = null;
			this.texture = null;
			this.scratchTexture = null;
			if (facade != null) {
				facade.close();
			}
			if (texture != null) {
				texture.close();
			}
			if (scratchTexture != null) {
				scratchTexture.close();
			}
		}
	}

	private static int dimensions(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> 1;
			case TEXTURE_3D -> 3;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> 2;
		};
	}
}
