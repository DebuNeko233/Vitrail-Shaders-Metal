package dev.vitrail.render;

import java.lang.reflect.Method;

/**
 * Reads Metallum's narrow public integration API without putting Metallum on Vitrail's compile
 * classpath.
 * <p>
 * A preference is deliberately not treated as proof that Metal works. The runtime path is opened
 * once a compatible API reports "Prefer Metal" and Vitrail's Metal capability provider has run
 * after successful device creation, which is the same three-part answer it has always required;
 * what is gone is the fourth, a system property a developer had to pass to be allowed in at all.
 * Metal is the maintained path, so it turns on where it works and the property is no longer read.
 */
public final class MetallumStatus {

	/** The Metallum integration contract this Vitrail build understands. */
	public static final int SUPPORTED_API_VERSION = 1;

	private static final String API_CLASS = "com.metallum.api.MetallumApi";
	private static final Status MISSING = new Status(false, -1, false, false);
	private static final Status INCOMPATIBLE = new Status(true, -1, false, false);

	private static volatile Status status;
	private static volatile Boolean backgroundPipelinePrecompile;
	private static volatile String metalApiGeneration;
	private static volatile String deviceMetalApiGeneration;

	private MetallumStatus() {
	}

	/** Whether a compatible Metallum API is present and the user explicitly selected Prefer Metal. */
	public static boolean compatibleAndPreferred() {
		Status known = status();
		return known.compatible() && known.metalPreferred();
	}

	/**
	 * Whether this compatible Metallum build explicitly guarantees that the public pipeline
	 * precompile entry point may run on a background worker.
	 * <p>
	 * This capability was added compatibly to API v1. An older v1 build therefore stays a valid
	 * Metal integration but answers false here, leaving family pipelines on their first-draw path
	 * rather than guessing that its implementation caches are thread-safe.
	 */
	public static boolean backgroundPipelinePrecompile() {
		if (!status().compatible()) {
			return false;
		}

		Boolean known = backgroundPipelinePrecompile;
		if (known != null) {
			return known;
		}

		synchronized (MetallumStatus.class) {
			known = backgroundPipelinePrecompile;
			if (known == null) {
				known = probeBackgroundPipelinePrecompile();
				backgroundPipelinePrecompile = known;
			}
			return known;
		}
	}

	/**
	 * Whether the Metal backend is ready to draw a pack, which is the whole of the question now that
	 * the developer opt-in is gone.
	 * <p>
	 * {@link BufferBlending#served()} is published by the Metal backend mixin only after
	 * {@code MetalBackend#createDevice} returns successfully, so it keeps a preference from being
	 * mistaken for a live device. A Metallum that is missing, that answers a different API version,
	 * or whose owner has not selected Prefer Metal fails on the first condition, and a session whose
	 * device never came up fails on the second; there is no longer a property that can open this on
	 * a session the other three answers refuse, and none that can close it on a session they allow.
	 */
	public static boolean renderingEnabled() {
		return compatibleAndPreferred()
				&& BufferBlending.served();
	}

	/** Snapshot of the optional API contract. Preference changes require restart, so caching is safe. */
	public static Status status() {
		Status known = status;
		if (known != null) {
			return known;
		}

		synchronized (MetallumStatus.class) {
			known = status;
			if (known == null) {
				known = probe();
				status = known;
			}
		}
		return known;
	}

	private static Status probe() {
		try {
			Class<?> api = Class.forName(API_CLASS, true, MetallumStatus.class.getClassLoader());
			Method versionMethod = api.getMethod("apiVersion");
			Object versionValue = versionMethod.invoke(null);
			if (!(versionValue instanceof Integer version)) {
				return INCOMPATIBLE;
			}

			if (version != SUPPORTED_API_VERSION) {
				return new Status(true, version, false, false);
			}

			Method preferredMethod = api.getMethod("isMetalPreferred");
			Object preferredValue = preferredMethod.invoke(null);
			if (!(preferredValue instanceof Boolean preferred)) {
				return new Status(true, version, false, false);
			}

			return new Status(true, version, true, preferred);
		} catch (ClassNotFoundException ignored) {
			return MISSING;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
			return INCOMPATIBLE;
		}
	}

	/**
	 * The generation of the Metal API this session's frames are encoded through, as one word, or empty.
	 * <p>
	 * This is the fact a capture is read for - which API produced the picture - and it is not the device's
	 * newest family: a device that can run Metal 4 still has every frame encoded through Metal 3's command
	 * buffer, and showing the capability here is what made the F3 screen claim Metal 4 for a session that
	 * never ran one. {@link #deviceMetalApiGeneration()} is the other question.
	 * <p>
	 * Read once and kept: a session cannot change its GPU, and the generation it executes is fixed when the
	 * device is created.
	 */
	public static String metalApiGeneration() {
		String known = metalApiGeneration;
		if (known == null) {
			known = readString("metalApiGeneration");
			metalApiGeneration = known;
		}

		return known;
	}

	/**
	 * The newest generation of the Metal API the device this session came up on can run, as one word, or
	 * empty.
	 * <p>
	 * A capability and not a fact about the frame: Apple has no API version to query, so what a device can
	 * run is a set of families and this is the newest one it answers for. Nothing renders through Metal 4
	 * yet, so a session can answer {@code Metal 4} here and {@code Metal 3} above at the same time - and the
	 * F3 screen says both rather than letting the capability read as the execution.
	 */
	public static String deviceMetalApiGeneration() {
		String known = deviceMetalApiGeneration;
		if (known == null) {
			known = readString("deviceMetalApiGeneration");
			deviceMetalApiGeneration = known;
		}

		return known;
	}

	/** One string from the integration API, or empty where there is none to read. */
	private static String readString(final String name) {
		try {
			Class<?> api = Class.forName(API_CLASS, true, MetallumStatus.class.getClassLoader());
			Method method = api.getMethod(name);
			return method.invoke(null) instanceof String value ? value : "";
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
			return "";
		}
	}

	private static boolean probeBackgroundPipelinePrecompile() {
		try {
			Class<?> api = Class.forName(API_CLASS, true, MetallumStatus.class.getClassLoader());
			Method method = api.getMethod("supportsBackgroundPipelinePrecompile");
			return method.invoke(null) instanceof Boolean supported && supported;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	/** Optional Metallum API state, kept independent of Metallum implementation classes. */
	public record Status(boolean present, int apiVersion, boolean compatible, boolean metalPreferred) {
	}
}
