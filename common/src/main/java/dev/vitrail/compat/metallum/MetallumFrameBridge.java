package dev.vitrail.compat.metallum;

import com.mojang.blaze3d.textures.GpuTexture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Late-bound calls into Metallum's stable frame bridge.
 * <p>
 * This is the replacement for naming the executing encoder's class: the backend is asked whether it supports
 * frame-resource operations, and if it does, the operations are performed through the flat bridge - which
 * addresses the active generation through a neutral capability. Nothing here knows a generation package, so a
 * class that moves on the Metallum side cannot silently stop this working.
 */
public final class MetallumFrameBridge {

	private static final String CLASS_NAME = "com.metallum.render.MetalFrameBridge";

	private static Methods methods;

	private MetallumFrameBridge() {
	}

	/** Whether the flat bridge is present and this encoder can be asked for frame-resource operations. */
	public static boolean supports(Object encoder) {
		Methods resolved = methodsOrNull();
		return resolved != null && resultOf(invokeQuietly(resolved.supports(), encoder));
	}

	public static boolean generateMipmaps(Object encoder, GpuTexture texture) {
		return resultOf(invoke(methods().generateMipmaps(), encoder, texture));
	}

	public static boolean clearStorageTexture(Object encoder, GpuTexture texture, int dimensions) {
		return resultOf(invoke(methods().clearStorageTexture(), encoder, texture, dimensions));
	}

	public static boolean copyStorageTextureRegion(
			Object encoder,
			GpuTexture source,
			GpuTexture destination,
			int sourceX,
			int sourceY,
			int sourceZ,
			int destinationX,
			int destinationY,
			int destinationZ,
			int width,
			int height,
			int depth) {
		return resultOf(invoke(methods().copyStorageTextureRegion(), encoder, source, destination,
				sourceX, sourceY, sourceZ, destinationX, destinationY, destinationZ, width, height, depth));
	}

	private static synchronized Methods methods() {
		if (methods == null) {
			BridgeCensus.lookedUp();
			methods = new Methods(
					method("supports", Object.class),
					method("generateMipmaps", Object.class, GpuTexture.class),
					method("clearStorageTexture", Object.class, GpuTexture.class, int.class),
					method("copyStorageTextureRegion", Object.class, GpuTexture.class, GpuTexture.class,
							int.class, int.class, int.class, int.class, int.class, int.class,
							int.class, int.class, int.class));
		}
		return methods;
	}

	private static synchronized Methods methodsOrNull() {
		try {
			return methods();
		} catch (IllegalStateException unavailable) {
			// A capability question: an absent bridge is a no, not a failure.
			return null;
		}
	}

	private record Methods(Method supports, Method generateMipmaps, Method clearStorageTexture,
			Method copyStorageTextureRegion) {
	}

	private static Method method(String name, Class<?>... parameterTypes) {
		try {
			Class<?> bridge = Class.forName(CLASS_NAME, false, MetallumFrameBridge.class.getClassLoader());
			return bridge.getMethod(name, parameterTypes);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Metallum frame bridge is unavailable or incompatible", e);
		}
	}

	private static Object invoke(Method method, Object... arguments) {
		long began = System.nanoTime();
		try {
			Object result = method.invoke(null, arguments);
			BridgeCensus.invoked(BridgeCensus.FRAME, arguments.length, System.nanoTime() - began);
			return result;
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Metallum frame bridge is not accessible", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException("Metallum frame bridge failed", cause);
		}
	}

	private static Object invokeQuietly(Method method, Object... arguments) {
		try {
			return invoke(method, arguments);
		} catch (RuntimeException | LinkageError unavailable) {
			return null;
		}
	}

	private static boolean resultOf(Object result) {
		return result instanceof Boolean accepted && accepted;
	}
}
