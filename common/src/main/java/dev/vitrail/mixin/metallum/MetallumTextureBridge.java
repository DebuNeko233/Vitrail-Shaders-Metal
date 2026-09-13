package dev.vitrail.mixin.metallum;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Late-bound call into Metallum's optional shader-writable texture bridge. */
final class MetallumTextureBridge {

	private static final String CLASS_NAME = "com.metallum.render.MetalTextureBridge";
	private static final Method CREATE_SHADER_WRITABLE = method("createShaderWritable",
			Object.class, String.class, int.class, GpuFormat.class,
			int.class, int.class, int.class, int.class);

	private MetallumTextureBridge() {
	}

	static GpuTexture createShaderWritable(
			Object backend,
			String label,
			int usage,
			GpuFormat format,
			int width,
			int height,
			int depthOrLayers,
			int mipLevels) {
		Object result = invoke(CREATE_SHADER_WRITABLE, backend, label, usage, format,
				width, height, depthOrLayers, mipLevels);
		if (result instanceof GpuTexture texture) {
			return texture;
		}
		throw new IllegalStateException("Metallum returned a non-GpuTexture writable texture");
	}

	private static Method method(String name, Class<?>... parameterTypes) {
		try {
			Class<?> bridge = Class.forName(CLASS_NAME, false,
					MetallumTextureBridge.class.getClassLoader());
			return bridge.getMethod(name, parameterTypes);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Metallum texture bridge is unavailable or incompatible", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invoke(Method method, Object... arguments) {
		try {
			return (T) method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot access Metallum texture bridge", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Metallum texture bridge failed", cause);
		}
	}
}
