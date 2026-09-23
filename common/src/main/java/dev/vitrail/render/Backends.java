package dev.vitrail.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import dev.vitrail.compat.metallum.MetallumEncoderCapabilities;
import dev.vitrail.Vitrail;
import dev.vitrail.compat.metallum.MetallumFrameBridge;
import dev.vitrail.mixin.access.CommandEncoderAccessor;
import dev.vitrail.render.compute.ComputeCommands;
import dev.vitrail.render.storage.StorageImageCommands;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The backend object behind one of the game's encoder wrappers.
 * <p>
 * {@link CommandEncoder} is a new object on every call that forwards to the encoder that really holds
 * the command buffer, and the capabilities this engine adds - mipmaps, storage images, compute, what a
 * pass needs of its attachments, MetalFX - are mixed into the <strong>backend</strong>. So a capability
 * asked of the wrapper is a capability that is never there: the check compiles, the code runs, and the
 * answer is always no. That is how the store half of P1 and its storage boundary came to be measured as
 * worth nothing: they were asked of the wrapper and never reached the backend at all.
 * <p>
 * Everything that asks a capability has to come through here. A few of the older callers already did,
 * which is what made the difference between them invisible.
 */
public final class Backends {

	private Backends() {
	}

	/**
	 * The backend behind this encoder, or null where there is none.
	 * <p>
	 * Null covers a backend that is not this engine's at all, and an encoder that is not a wrapper this
	 * game built - the accessor is mixed into the wrapper class, so anything else answers null rather
	 * than throwing.
	 */
	public static @Nullable CommandEncoderBackend encoder(@Nullable Object encoder) {
		return encoder instanceof CommandEncoderAccessor accessor ? accessor.vitrail$backend() : null;
	}

	/**
	 * The object that answers this engine's capabilities for this encoder, or null where there is none.
	 * <p>
	 * {@link #encoder} answers "which backend is this", which is not the same question: a backend may carry
	 * the capabilities itself, may be one the optional Metallum adapter can speak for, or may be neither. A
	 * caller that means to <em>use</em> a capability asks here; a caller that needs backend identity still
	 * asks {@link #encoder}. The distinction is the whole point: asking the wrapper was the bug this class
	 * exists to prevent, and asking the raw backend is the next one - it silently answers no for every
	 * backend that does not happen to carry them.
	 */
	public static @Nullable Object capabilities(@Nullable Object encoder) {
		CommandEncoderBackend backend = encoder(encoder);
		if (backend == null) {
			return null;
		}

		if (carriesCapabilities(backend)) {
			return backend;
		}

		if (MetallumFrameBridge.supports(backend)) {
			return adapterFor(backend);
		}

		// Neither one of this engine's own carriers nor the backend it draws through. The backend is
		// still handed back, because a capability query that threw would take a frame down for a
		// session that is already not drawing packs - but it is named once, which is the difference
		// between a session that quietly answers no to every capability and one whose log says why.
		// There is no third backend this engine supports: the one it requires is named in the mod
		// metadata, and a session without it refuses to read a pack at all.
		announce(backend);

		return backend;
	}

	/** Backend classes already named, so a frame does not repeat a line that describes the session. */
	private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();

	private static void announce(CommandEncoderBackend backend) {
		String name = backend.getClass().getName();
		if (ANNOUNCED.add(name)) {
			Vitrail.logger().error("The {} encoder is neither one of this engine's capability carriers "
					+ "nor the backend it draws through, so every capability it is asked for will "
					+ "answer no: a shader pack's passes would be recorded without mipmaps, storage "
					+ "images, compute, attachment contents or render scale", name);
		}
	}

	private static boolean carriesCapabilities(CommandEncoderBackend backend) {
		return backend instanceof MipmapCommands
				|| backend instanceof StorageImageCommands
				|| backend instanceof ComputeCommands
				|| backend instanceof AttachmentCommands
				|| backend instanceof ScaleCommands;
	}

	/**
	 * One adapter per backend, weakly held: the capability query is on the frame path, and an adapter made per
	 * call would be an allocation per pass. Weak keys so a backend the game has released is not kept alive by
	 * this cache.
	 */
	private static final Map<CommandEncoderBackend, MetallumEncoderCapabilities> METALLUM =
			Collections.synchronizedMap(new WeakHashMap<>());

	private static MetallumEncoderCapabilities adapterFor(CommandEncoderBackend backend) {
		synchronized (METALLUM) {
			return METALLUM.computeIfAbsent(backend, MetallumEncoderCapabilities::new);
		}
	}
}
