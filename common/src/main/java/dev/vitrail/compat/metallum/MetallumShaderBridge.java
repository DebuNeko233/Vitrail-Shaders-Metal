package dev.vitrail.compat.metallum;

import dev.vitrail.render.PackNames;
import dev.vitrail.render.RawLocals;
import dev.vitrail.render.SamplerReach;
import dev.vitrail.Vitrail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Installs this engine's half of a stage compile into the backend, through the backend's narrow
 * shader-module seam and without putting the backend on this module's compile classpath.
 * <p>
 * <strong>What is being handed over, and why it has to be.</strong> Two moments of a compile belong
 * to the pack rather than to the GPU. Between the compiler and the reflection, a pack's SPIR-V picks
 * up the values its reference implementations give it and the debug names that make a dump readable
 * ({@link RawLocals}, {@link PackNames}); after the reflection, the list of samplers a stage declares
 * can be narrowed to what its entry point really reaches ({@link SamplerReach}), which is what keeps
 * a shared include's unused declarations from costing Metal slots. Both sit inside a call the backend
 * owns, so the backend has to offer them - and it does, as
 * {@code com.metallum.api.MetallumShaderModules.Hook}.
 * <p>
 * <strong>Why a proxy and not an implementation.</strong> That interface lives in a mod this module
 * is not compiled against: the common module is loader-agnostic and must stay that way, and the
 * backend is present at runtime by virtue of being a required dependency rather than by being on a
 * classpath here. So the hook is a {@link Proxy} whose handler dispatches by method name, and every
 * value crossing the seam is a {@code String}, a {@code ByteBuffer} or a {@code List} of strings.
 * Nothing of the pack, and nothing of Minecraft, is named by the interface, which is what makes this
 * possible at all.
 * <p>
 * <strong>An absent backend is not an error here.</strong> A session without Metallum has already
 * failed a contract that {@code HostReport} names, and this installs nothing rather than throwing on
 * the way past. What would be wrong is the opposite: a backend present and this engine's half missing,
 * which is why the installation is attempted once at client init and says so when it succeeds.
 */
public final class MetallumShaderBridge {

	private static final String MODULES_CLASS = "com.metallum.api.MetallumShaderModules";
	private static final String HOOK_CLASS = "com.metallum.api.MetallumShaderModules$Hook";

	/** The seam's own version, checked before the hook is handed over. */
	private static final int SUPPORTED_API_VERSION = 1;

	private static volatile boolean attempted;

	private MetallumShaderBridge() {
	}

	/** Installs this engine's hook, once per session. Safe to call more than once. */
	public static void install() {
		if (attempted) {
			return;
		}

		attempted = true;
		try {
			Class<?> modules = Class.forName(MODULES_CLASS, true,
					MetallumShaderBridge.class.getClassLoader());
			Class<?> hook = Class.forName(HOOK_CLASS, true,
					MetallumShaderBridge.class.getClassLoader());

			Method version = modules.getMethod("apiVersion");
			if (!(version.invoke(null) instanceof Integer answered)
					|| answered != SUPPORTED_API_VERSION) {
				Vitrail.logger().error("Metallum's shader-module seam answers API v{} and this build "
						+ "understands v{}, so a pack's locals would not be zeroed and its unused "
						+ "samplers would not be narrowed; the picture is drawn all the same and this "
						+ "line is the only place it is said", version.invoke(null),
						SUPPORTED_API_VERSION);

				return;
			}

			Object installed = Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[] {hook},
					new Dispatch());
			modules.getMethod("install", hook).invoke(null, installed);
			Vitrail.logger().info("The shader-module seam is installed: this engine rewrites a "
					+ "pack's SPIR-V and answers which of its declared samplers a stage reaches");
		} catch (ClassNotFoundException absent) {
			// No Metallum in this instance. HostReport names the missing backend at startup and the
			// engine draws nothing, so there is no seam to install and nothing to say here.
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			Vitrail.logger().error("Metallum's shader-module seam could not be installed, so a "
					+ "pack's locals are not zeroed and its unused samplers are not narrowed; the "
					+ "module compiles and the picture is drawn, and this is the line that says why "
					+ "it may differ from the reference", e);
		}
	}

	/** Whether the seam was installed, for a contract to read and for the log's own accounting. */
	public static boolean installed() {
		return attempted;
	}

	/** One handler for every method of the seam: a name and two arguments, and no shared state. */
	private static final class Dispatch implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] arguments) {
			long began = System.nanoTime();
			Object answer = switch (method.getName()) {
				case "patchSpirv" -> PackNames.patch((String) arguments[0],
						RawLocals.patch((String) arguments[0], (ByteBuffer) arguments[1]));
				case "unreachedSampledImages" -> SamplerReach.unreached((String) arguments[0],
						(ByteBuffer) arguments[1], declared(arguments[2]));
				case "toString" -> "VitrailShaderModuleHook";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == arguments[0];
				default -> null;
			};

			// Counted on the same terms as every other crossing of this seam, so a compile's cost
			// sits beside a frame's in the one line rather than in a second census of its own.
			BridgeCensus.invoked(BridgeCensus.SHADER_MODULE,
					arguments == null ? 0 : arguments.length, System.nanoTime() - began);

			return answer;
		}

		@SuppressWarnings("unchecked")
		private static List<String> declared(Object argument) {
			return argument instanceof List<?> names ? (List<String>) names : List.of();
		}
	}
}
