package dev.vitrail.render;

import java.lang.reflect.Method;

/**
 * Reads Metallum's narrow public integration API without putting Metallum on Vitrail's compile
 * classpath.
 * <p>
 * A preference is deliberately not treated as proof that Metal works. The runtime path is opened
 * only for developer smoke testing, after a compatible API reports "Prefer Metal" and Vitrail's
 * Metal capability provider has run after successful device creation. The explicit system property
 * stays off by default until the Apple-Silicon runtime matrix has been completed.
 */
public final class MetallumStatus {

	/** The Metallum integration contract this Vitrail build understands. */
	public static final int SUPPORTED_API_VERSION = 1;

	/** Developer-only opt-in for the unvalidated Metal shader-pack path. */
	public static final String SMOKE_PROPERTY = "vitrail.experimentalMetal";

	private static final String API_CLASS = "com.metallum.api.MetallumApi";
	private static final Status MISSING = new Status(false, -1, false, false);
	private static final Status INCOMPATIBLE = new Status(true, -1, false, false);

	private static volatile Status status;
	private static volatile Boolean backgroundPipelinePrecompile;

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
	 * Whether the Metal backend has reached Vitrail's current validation-only rendering gate.
	 * <p>
	 * {@link BufferBlending#served()} is published by the Metal backend mixin only after
	 * {@code MetalBackend#createDevice} returns successfully, so it keeps a preference from being
	 * mistaken for a live device. The system property is intentionally the final condition.
	 */
	public static boolean renderingEnabled() {
		return compatibleAndPreferred()
				&& BufferBlending.served()
				&& Boolean.getBoolean(SMOKE_PROPERTY);
	}

	/** Whether the explicit developer smoke-test switch is on for this JVM. */
	public static boolean smokeEnabled() {
		return Boolean.getBoolean(SMOKE_PROPERTY);
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
