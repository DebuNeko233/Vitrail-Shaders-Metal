package dev.vitrail.compat.metallum;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.textures.GpuSampler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Late-bound call into Metallum's optional native comparison-sampler bridge. */
public final class MetallumSamplerBridge {

	private static final String CLASS_NAME = "com.metallum.render.MetalSamplerBridge";
	private static final String BACKEND_NAME = "com.metallum.render.MetalDevice";

	private MetallumSamplerBridge() {
	}

	/** Whether the opaque backend is Metallum's Metal device, without loading Metallum classes. */
	public static boolean supports(Object backend) {
		return backend != null && BACKEND_NAME.equals(backend.getClass().getName());
	}

	public static GpuSampler comparisonSampler(Object backend, GpuSampler template,
			CompareOp compareOp) {
		Object result = invoke(Methods.COMPARISON, backend, template, compareOp);
		if (result instanceof GpuSampler sampler) {
			return sampler;
		}
		throw new IllegalStateException("Metallum returned a non-GpuSampler comparison resource");
	}

	/** Defers optional Metallum class lookup until a Metallum backend actually asks for the seam. */
	private static final class Methods {
		private static final Method COMPARISON = method("comparisonSampler",
				Object.class, GpuSampler.class, CompareOp.class);
	}

	private static Method method(String name, Class<?>... parameterTypes) {
		try {
			Class<?> bridge = Class.forName(CLASS_NAME, false,
					MetallumSamplerBridge.class.getClassLoader());
			return bridge.getMethod(name, parameterTypes);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Metallum sampler bridge is unavailable or incompatible", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invoke(Method method, Object... arguments) {
		try {
			return (T) method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot access Metallum sampler bridge", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Metallum sampler bridge failed", cause);
		}
	}
}
