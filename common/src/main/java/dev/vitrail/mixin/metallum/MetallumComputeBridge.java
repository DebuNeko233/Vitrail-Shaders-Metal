package dev.vitrail.mixin.metallum;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Late-bound calls into Metallum's optional compute bridge.
 * <p>
 * The Metal backend is not on Vitrail's common compile classpath. Keeping the reflection in one
 * optional adapter preserves that boundary while the public Vitrail capability interfaces stay
 * expressed entirely in Minecraft/JDK types.
 */
final class MetallumComputeBridge {

	private static final String CLASS_NAME = "com.metallum.render.MetalComputeBridge";

	private static final Method COMPILE = method("compile",
			Object.class, String.class, ByteBuffer.class);
	private static final Method DISPATCH = method("dispatch",
			Object.class, Object.class, Map.class, Map.class, Map.class,
			int.class, int.class, int.class, int.class, int.class, int.class);
	private static final Method CLOSE = method("close", Object.class);

	private MetallumComputeBridge() {
	}

	static Object compile(Object backend, String label, ByteBuffer spirv) {
		return invoke(COMPILE, backend, label, spirv);
	}

	static boolean dispatch(
			Object encoder,
			Object pipeline,
			Map<String, GpuBufferSlice> buffers,
			Map<String, GpuTextureView> textures,
			Map<String, GpuSampler> samplers,
			int groupsX,
			int groupsY,
			int groupsZ,
			int localX,
			int localY,
			int localZ) {
		Object result = invoke(DISPATCH, encoder, pipeline, buffers, textures, samplers,
				groupsX, groupsY, groupsZ, localX, localY, localZ);
		return result instanceof Boolean accepted && accepted;
	}

	static void close(Object pipeline) {
		invoke(CLOSE, pipeline);
	}

	private static Method method(String name, Class<?>... parameterTypes) {
		try {
			Class<?> bridge = Class.forName(CLASS_NAME, false,
					MetallumComputeBridge.class.getClassLoader());
			return bridge.getMethod(name, parameterTypes);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Metallum compute bridge is unavailable or incompatible", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invoke(Method method, Object... arguments) {
		try {
			return (T) method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot access Metallum compute bridge", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Metallum compute bridge failed", cause);
		}
	}
}
