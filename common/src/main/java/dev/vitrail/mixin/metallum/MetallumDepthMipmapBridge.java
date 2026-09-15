package dev.vitrail.mixin.metallum;

import com.mojang.blaze3d.textures.GpuTexture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Late-bound access to Metallum's generic D32 progressive-nearest mipmap path. */
final class MetallumDepthMipmapBridge {

	private static final String CLASS_NAME = "com.metallum.render.MetalDepthMipmapBridge";

	private static boolean resolved;
	private static Method generate;

	private MetallumDepthMipmapBridge() {
	}

	static boolean generate(Object encoder, GpuTexture texture) {
		Method method = method();
		if (method == null) {
			return false;
		}

		Object result = invoke(method, encoder, texture);
		return result instanceof Boolean accepted && accepted;
	}

	private static synchronized Method method() {
		// Resolve inside the call, not class initialization: the optional backend may be older than
		// this Vitrail build and that mismatch is a normal capability failure, not a shadow-stage
		// error. Cache the negative result as well so an older backend does not pay reflection every
		// frame while its sampler correctly stays clamped to level zero.
		if (!resolved) {
			resolved = true;
			try {
				Class<?> bridge = Class.forName(CLASS_NAME, false,
						MetallumDepthMipmapBridge.class.getClassLoader());
				generate = bridge.getMethod("generate", Object.class, GpuTexture.class);
			} catch (ReflectiveOperationException ignored) {
				generate = null;
			}
		}
		return generate;
	}

	private static Object invoke(Method method, Object... arguments) {
		try {
			return method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot access Metallum depth mipmap bridge", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Metallum depth mipmap bridge failed", cause);
		}
	}
}
