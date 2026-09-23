package dev.vitrail.render.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Names the resources one already-compiled compute SPIR-V module actually carries.
 * <p>
 * This is deliberately reflection only. Vitrail needs the names so it can resolve shader-pack
 * policy to Minecraft facade objects; native binding indices, shader-language conversion and
 * pipeline creation stay with the active backend. The four resource classes match the current
 * compute seam and are the same SPIRV-Cross resource classes the Metallum compute bridge reads.
 */
public record ComputeResources(
		List<String> uniformBuffers,
		List<String> storageBuffers,
		List<String> sampledImages,
		List<String> storageImages) {

	public ComputeResources {
		uniformBuffers = List.copyOf(uniformBuffers);
		storageBuffers = List.copyOf(storageBuffers);
		sampledImages = List.copyOf(sampledImages);
		storageImages = List.copyOf(storageImages);
	}

	/** Reflects the resource names without modifying binding decorations in {@code spirv}. */
	public static ComputeResources inspect(ByteBuffer spirv) {
		if (spirv == null) {
			throw new IllegalArgumentException("Compute SPIR-V is null");
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pContext = stack.mallocPointer(1);
			check(Spvc.spvc_context_create(pContext), "spvc_context_create");
			long context = pContext.get(0);
			try {
				IntBuffer words = spirv.duplicate().asIntBuffer();
				PointerBuffer pIr = stack.mallocPointer(1);
				check(Spvc.spvc_context_parse_spirv(context, words, words.remaining(), pIr),
						"spvc_context_parse_spirv");

				PointerBuffer pCompiler = stack.mallocPointer(1);
				check(Spvc.spvc_context_create_compiler(
						context,
						Spvc.SPVC_BACKEND_NONE,
						pIr.get(0),
						Spvc.SPVC_CAPTURE_MODE_COPY,
						pCompiler), "spvc_context_create_compiler");
				long compiler = pCompiler.get(0);

				PointerBuffer pResources = stack.mallocPointer(1);
				check(Spvc.spvc_compiler_create_shader_resources(compiler, pResources),
						"spvc_compiler_create_shader_resources");
				long resources = pResources.get(0);

				return new ComputeResources(
						collect(stack, compiler, resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER),
						collect(stack, compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER),
						collect(stack, compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE),
						collect(stack, compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE));
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}
	}

	private static List<String> collect(MemoryStack stack, long compiler, long resources,
			int resourceType) {
		PointerBuffer pList = stack.mallocPointer(1);
		PointerBuffer pCount = stack.mallocPointer(1);
		check(Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount),
				"spvc_resources_get_resource_list_for_type(" + resourceType + ")");
		int count = (int) pCount.get(0);
		if (count == 0) {
			return List.of();
		}

		LinkedHashSet<String> names = new LinkedHashSet<>();
		SpvcReflectedResource.Buffer reflected = SpvcReflectedResource.create(pList.get(0), count);
		for (int i = 0; i < count; i++) {
			String name = resourceName(compiler, reflected.get(i));
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		return new ArrayList<>(names);
	}

	private static String resourceName(long compiler, SpvcReflectedResource resource) {
		String direct = resource.nameString();
		if (direct != null && !direct.isEmpty()) {
			return direct;
		}
		String fromId = Spvc.spvc_compiler_get_name(compiler, resource.id());
		if (fromId != null && !fromId.isEmpty()) {
			return fromId;
		}
		String fromType = Spvc.spvc_compiler_get_name(compiler, resource.type_id());
		return fromType == null ? "" : fromType;
	}

	private static void check(int result, String stage) {
		if (result != Spvc.SPVC_SUCCESS) {
			throw new IllegalStateException("SPIRV-Cross error at " + stage + ": " + result);
		}
	}
}
