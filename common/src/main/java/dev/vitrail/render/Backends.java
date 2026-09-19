package dev.vitrail.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import dev.vitrail.mixin.access.CommandEncoderAccessor;
import org.jspecify.annotations.Nullable;

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
}
