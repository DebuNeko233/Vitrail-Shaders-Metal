package dev.vitrail.render.storage;

import dev.vitrail.mixin.access.CommandEncoderAccessor;
import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.mixin.access.VulkanCommandEncoderAccessor;
import dev.vitrail.pack.model.ImageInformation;
import dev.vitrail.pack.model.PackTexture;
import dev.vitrail.pack.model.TargetFormat;
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
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The storage images a pack declared with {@code image.NAME}.
 * <p>
 * Minecraft 26.2's public texture creation path has no storage bit and cannot distinguish a true
 * three-dimensional texture from array layers. Vulkan therefore keeps the direct VMA allocation it
 * has always used, while another backend may provide {@link StorageImageBackend} and keep the
 * resource inside Minecraft's {@link GpuTexture}/{@link GpuTextureView} facade. The pack-facing
 * policy is shared: clear timing, persistence, relative sizing, camera following and scratch-copy
 * decisions are made here and not in a backend.
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
	 * takes the cautious side of both rather than choosing.</strong> The Vulkan road used to blame
	 * the SIZE, a five hundred mebibyte clear beside a 773 MiB storage-buffer fill on one command
	 * buffer; {@link GpuRecording#afterTransfer} records the fence whose absence was the other
	 * explanation. A backend-native clear has its own ordering, but the same allocation-pass budget
	 * remains the conservative policy until the large-volume case has been measured there too.
	 * <p>
	 * <strong>What it still cuts, and it stays a debt.</strong> Photon's volumes at its own default
	 * and below fit inside it twice over; at its two largest settings one volume alone is over it,
	 * and there the defect this exists for comes back untouched.
	 */
	private static final long CLEAR_AT_BIRTH_BUDGET = 64L * 1024L * 1024L;

	private final ImageInformation.Reading declared;
	private final List<Allocated> allocated = new ArrayList<>();

	/**
	 * Every name a Vulkan descriptor push may ask for, resolved to the native view it gets, rebuilt
	 * whenever {@link #allocated} changes. Backends that expose a real Minecraft texture facade use
	 * {@link #facadeBindings} instead, leaving this ABI unchanged for the Vulkan mixins.
	 */
	private Map<String, Bound> bindings = Map.of();

	/** Backend-owned texture views, under both the image uniform and its optional sampler alias. */
	private Map<String, GpuTextureView> facadeBindings = Map.of();
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
	 * The Vulkan image currently allocated for this name, whether the pack wrote it as the image
	 * uniform or as the sampler on the same {@code image.} line. Vulkan mixins look it up while
	 * pushing descriptors.
	 */
	public static Bound bound(String name) {
		return current.lookup(name);
	}

	/**
	 * The backend-owned Minecraft view for this name, or null when the active resource is the direct
	 * Vulkan allocation. The public {@link com.mojang.blaze3d.systems.RenderPass} seam substitutes
	 * this view before the backend sees the bind.
	 */
	public static @Nullable GpuTextureView facadeView(String name) {
		return current.facadeBindings.get(name);
	}

	public static boolean storageBinding(String name) {
		Bound bound = bound(name);
		return bound != null && bound.storage();
	}

	void install() {
		current = this;
	}

	private Bound lookup(String name) {
		return this.bindings.get(name);
	}

	/**
	 * The first image declaring a name answers for it, the image uniform before the sampler on
	 * the same line, which is the order the walk this replaces read them in.
	 */
	private void rebind() {
		Map<String, Bound> bound = new HashMap<>();
		Map<String, GpuTextureView> facade = new HashMap<>();
		for (Allocated image : this.allocated) {
			boolean integer = image.declared.internalFormat().used().integer();
			if (image.view != 0L) {
				bound.putIfAbsent(image.declared.name(), new Bound(image.view, true, integer));
				image.declared.sampler().ifPresent(sampler ->
						bound.putIfAbsent(sampler, new Bound(image.view, false, integer)));
			}

			if (image.facadeView != null) {
				facade.putIfAbsent(image.declared.name(), image.facadeView);
				image.declared.sampler().ifPresent(sampler ->
						facade.putIfAbsent(sampler, image.facadeView));
			}
		}

		this.bindings = Map.copyOf(bound);
		this.facadeBindings = Map.copyOf(facade);
	}

	/**
	 * One Vulkan native view. {@code storage} is the image uniform ({@code voxel_img}); a sampler
	 * name hanging off the same directive is sampled, not stored.
	 */
	public record Bound(long view, boolean storage, boolean integer) {
	}

	/** Whether the last refusal was of an image no screen size can change, see {@link #ensure}. */
	public boolean refusedForGood() {
		return this.refusedForGood;
	}

	/**
	 * Allocates every absolute image once, and rebuilds the relative ones when the screen moves.
	 * A failure is the whole set given back and the frame refused, see {@link #refused}.
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
		VulkanDevice vulkan = deviceBackend instanceof VulkanDevice activeVulkan
				? activeVulkan : null;
		StorageImageBackend storageBackend = deviceBackend instanceof StorageImageBackend storage
				? storage : null;
		if (vulkan == null && storageBackend == null) {
			return;
		}

		// Everything or nothing: a refusal halfway through gives back what was allocated, so the
		// next screen size the colour targets try again at starts from the first image rather
		// than skipping the one that failed as already dealt with.
		try {
			allocate(device, vulkan, storageBackend, first, resized, screenWidth, screenHeight);
		} catch (RuntimeException e) {
			this.allocated.forEach(image -> image.destroy(vulkan));
			this.allocated.clear();
			this.laidOut = false;
			rebind();
			throw e;
		}

		this.lastWidth = screenWidth;
		this.lastHeight = screenHeight;
		rebind();
		try {
			layoutIfNeeded();
		} catch (RuntimeException e) {
			this.allocated.forEach(image -> image.destroy(vulkan));
			this.allocated.clear();
			this.laidOut = false;
			rebind();
			throw e;
		}
	}

	private void allocate(GpuDevice device, @Nullable VulkanDevice vulkan,
			@Nullable StorageImageBackend storageBackend, boolean first, boolean resized,
			int screenWidth, int screenHeight) {
		if (first) {
			for (ImageInformation image : this.declared.images()) {
				if (image.relative()) {
					continue;
				}

				boolean movable = movable(image);
				try {
					this.allocated.add(Allocated.create(device, vulkan, storageBackend, image,
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
					image.destroy(vulkan);
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
					this.allocated.add(Allocated.create(device, vulkan, storageBackend, image,
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
	 */
	public void clearMarked(CommandEncoder encoder) {
		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands != null) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				// The volume being emptied was sampled by the previous frame's gbuffers and stored by
				// the previous dispatch, and nothing else orders those against a transfer write.
				GpuRecording.beforeTransfer(commands, stack);
				for (Allocated image : this.allocated) {
					if (!image.declared.clear()) {
						continue;
					}

					clearImage(commands, stack, image);
				}

				GpuRecording.afterTransfer(commands, stack);
			}
			return;
		}

		StorageImageCommands storageCommands = storageCommands(encoder);
		if (storageCommands == null) {
			return;
		}

		for (Allocated image : this.allocated) {
			if (image.declared.clear() && image.texture != null) {
				clearImage(storageCommands, image);
			}
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
	 */
	public void reanchor(CommandEncoder encoder, int dx, int dy, int dz) {
		if (dx == 0 && dy == 0 && dz == 0) {
			return;
		}

		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands != null) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				GpuRecording.beforeTransfer(commands, stack);
				for (Allocated image : this.allocated) {
					if (image.scratch == 0L) {
						continue;
					}

					// Past the extent nothing of the volume survives the move, which is a teleport
					// rather than a step. Emptied and not moved: the pack's own floodfill reprojection
					// is just as lost there, and identities from the world the player has left would
					// block light all over the one they arrived in.
					if (Math.abs(dx) >= image.width || Math.abs(dy) >= image.height
							|| Math.abs(dz) >= image.depth) {
						clearImage(commands, stack, image);
						continue;
					}

					move(commands, stack, image, dx, dy, dz);
				}

				GpuRecording.afterTransfer(commands, stack);
			}
			return;
		}

		StorageImageCommands storageCommands = storageCommands(encoder);
		if (storageCommands == null) {
			return;
		}

		for (Allocated image : this.allocated) {
			if (image.scratchTexture == null) {
				continue;
			}

			if (Math.abs(dx) >= image.width || Math.abs(dy) >= image.height
					|| Math.abs(dz) >= image.depth) {
				clearImage(storageCommands, image);
				continue;
			}

			move(storageCommands, image, dx, dy, dz);
		}
	}

	/**
	 * The move itself, in two copies through the image's own scratch because an in-place overlapping
	 * copy has no portable meaning. Both backends therefore carry the same region through a second
	 * image; Vitrail owns the arithmetic and the backend owns how each exact copy is encoded.
	 * <p>
	 * Both copies carry the SAME region, the one the second reads, and the scratch keeps whatever an
	 * earlier move left outside it: no reader of any kind ever names the scratch, so a texel there
	 * that the second copy will not read is a texel the first one has no reason to write. That is
	 * {@code |d|} planes an axis, a plane at a walking pace and a large part of the volume at speed.
	 */
	private static void move(VkCommandBuffer commands, MemoryStack stack, Allocated image,
			int dx, int dy, int dz) {
		int fromX = Math.max(dx, 0);
		int fromY = Math.max(dy, 0);
		int fromZ = Math.max(dz, 0);
		int spanX = image.width - Math.abs(dx);
		int spanY = image.height - Math.abs(dy);
		int spanZ = image.depth - Math.abs(dz);

		VkImageCopy.Buffer kept = VkImageCopy.calloc(1, stack);
		VkImageCopy carried = kept.get(0);
		layers(carried);
		carried.srcOffset().set(fromX, fromY, fromZ);
		carried.dstOffset().set(fromX, fromY, fromZ);
		carried.extent().set(spanX, spanY, spanZ);
		VK12.vkCmdCopyImage(commands, image.image, VK12.VK_IMAGE_LAYOUT_GENERAL,
				image.scratch, VK12.VK_IMAGE_LAYOUT_GENERAL, kept);

		GpuRecording.betweenTransfers(commands, stack);

		VkImageCopy.Buffer shifted = VkImageCopy.calloc(1, stack);
		VkImageCopy region = shifted.get(0);
		layers(region);
		region.srcOffset().set(fromX, fromY, fromZ);
		region.dstOffset().set(Math.max(-dx, 0), Math.max(-dy, 0), Math.max(-dz, 0));
		region.extent().set(spanX, spanY, spanZ);
		VK12.vkCmdCopyImage(commands, image.scratch, VK12.VK_IMAGE_LAYOUT_GENERAL,
				image.image, VK12.VK_IMAGE_LAYOUT_GENERAL, shifted);
	}

	private static void move(StorageImageCommands commands, Allocated image,
			int dx, int dy, int dz) {
		if (image.texture == null || image.scratchTexture == null) {
			return;
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

	private static void layers(VkImageCopy region) {
		region.srcSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
		region.dstSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
	}

	private void layoutIfNeeded() {
		if (this.laidOut || this.allocated.isEmpty()) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands != null) {
			List<Allocated> born = newlyBorn();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				for (Allocated image : born) {
					// The scratch beside its image and in the same breath: it is a transfer end of the
					// same volume, so it has to leave UNDEFINED before the first copy names it, and a
					// copy is the only thing that ever will.
					int count = image.scratch == 0L ? 1 : 2;
					VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(count, stack)
							.sType$Default();
					for (int at = 0; at < count; at++) {
						VkImageMemoryBarrier barrier = barriers.get(at);
						barrier.sType$Default();
						barrier.oldLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
						barrier.newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
						barrier.srcAccessMask(0);
						barrier.dstAccessMask(VK12.VK_ACCESS_SHADER_READ_BIT
								| VK12.VK_ACCESS_SHADER_WRITE_BIT
								| VK12.VK_ACCESS_TRANSFER_WRITE_BIT
								| VK12.VK_ACCESS_TRANSFER_READ_BIT);
						barrier.srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
						barrier.dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
						barrier.image(at == 0 ? image.image : image.scratch);
						barrier.subresourceRange().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
					}

					VK12.vkCmdPipelineBarrier(commands, VK12.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
							VK12.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
				}

				clearBornImages(commands, stack, born);
			}

			markBornPrepared(born);
			this.laidOut = true;
			return;
		}

		StorageImageCommands storageCommands = storageCommands(encoder);
		if (storageCommands == null) {
			return;
		}

		List<Allocated> born = newlyBorn();
		clearBornImages(storageCommands, born);
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
	 * Vulkan orders the transfer clear through the barriers around this call. A backend-native road
	 * provides the same dependency through its own encoder ordering; the decision about WHICH born
	 * images receive zeros remains shared in {@link #birthClears}.
	 */
	private void clearBornImages(VkCommandBuffer commands, MemoryStack stack, List<Allocated> born) {
		List<Allocated> emptying = birthClears(born);
		if (emptying.isEmpty()) {
			return;
		}

		for (Allocated image : emptying) {
			clearImage(commands, stack, image);
		}

		// The one fence between these zeros and the pack's first dispatch. See the javadoc above.
		GpuRecording.afterTransfer(commands, stack);
		logBornCleared(emptying.size());
	}

	private void clearBornImages(StorageImageCommands commands, List<Allocated> born) {
		List<Allocated> emptying = birthClears(born);
		if (emptying.isEmpty()) {
			return;
		}

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

	private static void clearImage(VkCommandBuffer commands, MemoryStack stack, Allocated image) {
		VkClearColorValue colour = VkClearColorValue.calloc(stack);
		if (image.declared.internalFormat().used().integer()) {
			IntBuffer ints = colour.int32();
			ints.put(0, 0).put(1, 0).put(2, 0).put(3, 0);
		} else {
			colour.float32().put(0, 0.0F).put(1, 0.0F).put(2, 0.0F).put(3, 0.0F);
		}

		VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
		range.set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
		VK12.vkCmdClearColorImage(commands, image.image, VK12.VK_IMAGE_LAYOUT_GENERAL, colour, range);
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

	private static VkCommandBuffer commands(CommandEncoder encoder) {
		return ((CommandEncoderAccessor) encoder).vitrail$backend() instanceof VulkanCommandEncoder vulkan
				? ((VulkanCommandEncoderAccessor) vulkan).vitrail$commandBuffer()
				: null;
	}

	private static @Nullable StorageImageCommands storageCommands(CommandEncoder encoder) {
		CommandEncoderBackend backend = ((CommandEncoderAccessor) encoder).vitrail$backend();
		return backend instanceof StorageImageCommands commands ? commands : null;
	}

	int count() {
		return this.allocated.size();
	}

	@Override
	public void close() {
		if (current == this) {
			current = none();
		}

		VulkanDevice vulkan = vulkan();
		this.allocated.forEach(image -> image.destroy(vulkan));
		this.allocated.clear();
		this.bindings = Map.of();
		this.facadeBindings = Map.of();
		this.lastWidth = 0;
		this.lastHeight = 0;
		this.laidOut = false;
	}

	private static @Nullable VulkanDevice vulkan() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return null;
		}

		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).vitrail$backend();
		return backend instanceof VulkanDevice vulkan ? vulkan : null;
	}

	/** One allocated image, either direct Vulkan handles or a backend-owned Minecraft facade. */
	private static final class Allocated {

		private final ImageInformation declared;
		private final boolean relative;
		private final int width;
		private final int height;
		private final int depth;
		private long image;
		private long allocation;
		private long view;
		private @Nullable GpuTexture texture;
		private @Nullable GpuTextureView facadeView;

		/**
		 * A second image of the same shape, for the volumes {@link #reanchor} moves, and nought/null
		 * for every other. It carries no view: nothing samples it and nothing stores into it, it is
		 * one end of a copy and no more.
		 */
		private long scratch;
		private long scratchAllocation;
		private @Nullable GpuTexture scratchTexture;

		/** Whether backend birth preparation for this allocation has already been recorded. */
		private boolean laidOut;

		private Allocated(ImageInformation declared, boolean relative, int width, int height,
				int depth, long image, long allocation, long view) {
			this.declared = declared;
			this.relative = relative;
			this.width = width;
			this.height = height;
			this.depth = depth;
			this.image = image;
			this.allocation = allocation;
			this.view = view;
		}

		private static Allocated create(GpuDevice device, @Nullable VulkanDevice vulkan,
				@Nullable StorageImageBackend backend, ImageInformation declared, int width,
				int height, int depth, boolean movable) {
			if (vulkan != null) {
				return createVulkan(vulkan, declared, width, height, depth, movable);
			}
			if (backend != null) {
				return createFacade(device, backend, declared, width, height, depth, movable);
			}
			throw new IllegalStateException("Active backend exposes no storage-image allocation path");
		}

		private static Allocated createFacade(GpuDevice device, StorageImageBackend backend,
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
				throw new IllegalStateException("Backend returned null for storage image " + declared.name());
			}

			GpuTextureView view;
			try {
				view = device.createTextureView(texture);
			} catch (RuntimeException e) {
				texture.close();
				throw e;
			}

			Allocated allocated = new Allocated(declared, declared.relative(), extentWidth,
					extentHeight, extentDepth, 0L, 0L, 0L);
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

		private static Allocated createVulkan(VulkanDevice vulkan, ImageInformation declared,
				int width, int height, int depth, boolean movable) {
			int vkFormat = vkFormat(declared.internalFormat().used());
			int type = imageType(declared.shape());
			int viewType = viewType(declared.shape());
			int extentWidth = Math.max(width, 1);
			int extentHeight = Math.max(height, 1);
			int extentDepth = Math.max(depth, 1);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default();
				imageInfo.imageType(type);
				imageInfo.extent().set(extentWidth, extentHeight, extentDepth);
				imageInfo.mipLevels(1);
				imageInfo.arrayLayers(1);
				imageInfo.format(vkFormat);
				imageInfo.tiling(VK12.VK_IMAGE_TILING_OPTIMAL);
				imageInfo.initialLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
				imageInfo.usage(VK12.VK_IMAGE_USAGE_STORAGE_BIT
						| VK12.VK_IMAGE_USAGE_SAMPLED_BIT
						| VK12.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
						| VK12.VK_IMAGE_USAGE_TRANSFER_DST_BIT);
				imageInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
				imageInfo.samples(VK12.VK_SAMPLE_COUNT_1_BIT);
				VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
				allocationInfo.usage(8);
				LongBuffer imagePtr = stack.callocLong(1);
				PointerBuffer allocationPtr = stack.callocPointer(1);
				VulkanUtils.crashIfFailure(vulkan,
						Vma.vmaCreateImage(vulkan.vma(), imageInfo, allocationInfo, imagePtr,
								allocationPtr, null),
						"storage image " + declared.name());
				long image = imagePtr.get(0);
				long allocation = allocationPtr.get(0);

				VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default();
				viewInfo.image(image);
				viewInfo.viewType(viewType);
				viewInfo.format(vkFormat);
				viewInfo.subresourceRange().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
				LongBuffer viewPtr = stack.callocLong(1);
				try {
					VulkanUtils.crashIfFailure(vulkan,
							VK12.vkCreateImageView(vulkan.vkDevice(), viewInfo, null, viewPtr),
							"storage image view " + declared.name());
				} catch (RuntimeException e) {
					Vma.vmaDestroyImage(vulkan.vma(), image, allocation);
					throw e;
				}

				Allocated allocated = new Allocated(declared, declared.relative(), extentWidth,
						extentHeight, extentDepth, image, allocation, viewPtr.get(0));

				// A failure of scratch is not the image's failure. The volume stays bound and merely
				// keeps the frame of lag it carried before, rather than dropping the pack's lighting.
				if (movable) {
					try {
						VulkanUtils.crashIfFailure(vulkan,
								Vma.vmaCreateImage(vulkan.vma(), imageInfo, allocationInfo, imagePtr,
										allocationPtr, null),
								"storage image scratch " + declared.name());
						allocated.scratch = imagePtr.get(0);
						allocated.scratchAllocation = allocationPtr.get(0);
					} catch (GpuDeviceLossException e) {
						throw e;
					} catch (RuntimeException e) {
						Vitrail.logger().warn("storage image {} keeps a frame of lag, its scratch "
								+ "could not be allocated: {}", declared.name(), e.toString());
					}
				}

				return allocated;
			}
		}

		/**
		 * Frees backend-owned facades through their own close path and direct Vulkan handles through
		 * the game's deferred queue. The latter may still be named by in-flight descriptors, so they
		 * retain the existing delayed destruction rule.
		 */
		private void destroy(@Nullable VulkanDevice vulkan) {
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

			long view = this.view;
			long image = this.image;
			long allocation = this.allocation;
			long scratch = this.scratch;
			long scratchAllocation = this.scratchAllocation;
			this.view = 0L;
			this.image = 0L;
			this.allocation = 0L;
			this.scratch = 0L;
			this.scratchAllocation = 0L;
			if (vulkan == null) {
				return;
			}

			GpuRecording.destroyLater(() -> {
				if (view != 0L) {
					VK12.vkDestroyImageView(vulkan.vkDevice(), view, null);
				}

				if (image != 0L) {
					Vma.vmaDestroyImage(vulkan.vma(), image, allocation);
				}

				if (scratch != 0L) {
					Vma.vmaDestroyImage(vulkan.vma(), scratch, scratchAllocation);
				}
			});
		}
	}

	private static int dimensions(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> 1;
			case TEXTURE_3D -> 3;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> 2;
		};
	}

	private static int imageType(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> VK12.VK_IMAGE_TYPE_1D;
			case TEXTURE_3D -> VK12.VK_IMAGE_TYPE_3D;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> VK12.VK_IMAGE_TYPE_2D;
		};
	}

	private static int viewType(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> VK12.VK_IMAGE_VIEW_TYPE_1D;
			case TEXTURE_3D -> VK12.VK_IMAGE_VIEW_TYPE_3D;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> VK12.VK_IMAGE_VIEW_TYPE_2D;
		};
	}

	private static int vkFormat(TargetFormat format) {
		return switch (format) {
			case R8_UNORM -> VK12.VK_FORMAT_R8_UNORM;
			case R8_SNORM -> VK12.VK_FORMAT_R8_SNORM;
			case RG8_UNORM -> VK12.VK_FORMAT_R8G8_UNORM;
			case RG8_SNORM -> VK12.VK_FORMAT_R8G8_SNORM;
			case RGBA8_UNORM -> VK12.VK_FORMAT_R8G8B8A8_UNORM;
			case RGBA8_SNORM -> VK12.VK_FORMAT_R8G8B8A8_SNORM;
			case R16_UNORM -> VK12.VK_FORMAT_R16_UNORM;
			case R16_SNORM -> VK12.VK_FORMAT_R16_SNORM;
			case RG16_UNORM -> VK12.VK_FORMAT_R16G16_UNORM;
			case RG16_SNORM -> VK12.VK_FORMAT_R16G16_SNORM;
			case RGBA16_UNORM -> VK12.VK_FORMAT_R16G16B16A16_UNORM;
			case RGBA16_SNORM -> VK12.VK_FORMAT_R16G16B16A16_SNORM;
			case R8_UINT -> VK12.VK_FORMAT_R8_UINT;
			case R8_SINT -> VK12.VK_FORMAT_R8_SINT;
			case RG8_UINT -> VK12.VK_FORMAT_R8G8_UINT;
			case RG8_SINT -> VK12.VK_FORMAT_R8G8_SINT;
			case RGBA8_UINT -> VK12.VK_FORMAT_R8G8B8A8_UINT;
			case RGBA8_SINT -> VK12.VK_FORMAT_R8G8B8A8_SINT;
			case R16_UINT -> VK12.VK_FORMAT_R16_UINT;
			case R16_SINT -> VK12.VK_FORMAT_R16_SINT;
			case RG16_UINT -> VK12.VK_FORMAT_R16G16_UINT;
			case RG16_SINT -> VK12.VK_FORMAT_R16G16_SINT;
			case RGBA16_UINT -> VK12.VK_FORMAT_R16G16B16A16_UINT;
			case RGBA16_SINT -> VK12.VK_FORMAT_R16G16B16A16_SINT;
			case R32_UINT -> VK12.VK_FORMAT_R32_UINT;
			case R32_SINT -> VK12.VK_FORMAT_R32_SINT;
			case RG32_UINT -> VK12.VK_FORMAT_R32G32_UINT;
			case RG32_SINT -> VK12.VK_FORMAT_R32G32_SINT;
			case RGBA32_UINT -> VK12.VK_FORMAT_R32G32B32A32_UINT;
			case RGBA32_SINT -> VK12.VK_FORMAT_R32G32B32A32_SINT;
			case R16_FLOAT -> VK12.VK_FORMAT_R16_SFLOAT;
			case RG16_FLOAT -> VK12.VK_FORMAT_R16G16_SFLOAT;
			case RGBA16_FLOAT -> VK12.VK_FORMAT_R16G16B16A16_SFLOAT;
			case R32_FLOAT -> VK12.VK_FORMAT_R32_SFLOAT;
			case RG32_FLOAT -> VK12.VK_FORMAT_R32G32_SFLOAT;
			case RGBA32_FLOAT -> VK12.VK_FORMAT_R32G32B32A32_SFLOAT;
			case RGB10A2_UNORM -> VK12.VK_FORMAT_A2B10G10R10_UNORM_PACK32;
			case RGB10A2_UINT -> VK12.VK_FORMAT_A2B10G10R10_UINT_PACK32;
			case RG11B10_FLOAT -> VK12.VK_FORMAT_B10G11R11_UFLOAT_PACK32;
		};
	}
}
