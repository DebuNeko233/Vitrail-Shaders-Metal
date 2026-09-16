package dev.vitrail.render;

import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.target.ChainPlan;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.pack.target.SamplerTypes;
import dev.vitrail.pack.texture.CustomImages;
import dev.vitrail.render.storage.StorageImages;

import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves writable image uniforms of graphics programs without teaching a backend pack policy. */
final class PackStorageImages {

	private PackStorageImages() {
	}

	/** Storage-image uniforms in declaration order, sampled aliases deliberately excluded. */
	static Set<String> names(PackProgram.Loaded loaded) {
		Set<String> names = new LinkedHashSet<>();
		for (TranslatedUnit.Uniform uniform : loaded.program().samplers()) {
			if (SamplerTypes.image(uniform.type())) {
				names.add(uniform.name());
			}
		}

		return Set.copyOf(names);
	}

	/** The writable view behind a graphics image, on the pass-start half for colorimgN. */
	static GpuTextureView view(String name, SamplerPlan.Binding binding, ColorTargets targets) {
		GpuTextureView custom = StorageImages.facadeView(name);
		if (custom != null && CustomImages.storage(name)) {
			return custom;
		}

		if (binding.kind() != SamplerPlan.Kind.COLOR_IMAGE) {
			return null;
		}

		TargetSurface surface = targets.surface(binding.index(), binding.side());
		return surface != null && surface.storage() ? surface.storageView() : null;
	}

	/** Writable images that alias a colour attachment of the same geometry pass. */
	static List<String> attachmentConflicts(Set<String> images, SamplerPlan samplers,
			List<ChainPlan.Attachment> attachments) {
		List<String> conflicts = new ArrayList<>();
		for (String name : images) {
			SamplerPlan.Binding binding = samplers.binding(name);
			if (binding.kind() != SamplerPlan.Kind.COLOR_IMAGE) {
				continue;
			}

			ChainPlan.Attachment image = new ChainPlan.Attachment(binding.index(), binding.side());
			if (attachments.contains(image)) {
				conflicts.add(name);
			}
		}

		return List.copyOf(conflicts);
	}
}
