package dev.vitrail.render.compute;

import java.nio.ByteBuffer;

/**
 * Backend-owned compute-pipeline lifetime missing from Minecraft 26.2's public GPU facade.
 * <p>
 * Vitrail owns translated shader-pack SPIR-V and when a compute program is needed. The backend owns
 * conversion to its native shader language, native pipeline creation, and destruction. The returned
 * token is deliberately opaque: callers may only hand it to {@link ComputeCommands} or back to
 * {@link #vitrail$closeCompute(Object)}.
 */
public interface ComputeDeviceBackend {

	/** Compiles one shader-pack compute SPIR-V module into a backend-owned opaque pipeline token. */
	Object vitrail$compileCompute(String label, ByteBuffer spirv);

	/** Releases a token returned by {@link #vitrail$compileCompute(String, ByteBuffer)}. */
	void vitrail$closeCompute(Object pipeline);
}
