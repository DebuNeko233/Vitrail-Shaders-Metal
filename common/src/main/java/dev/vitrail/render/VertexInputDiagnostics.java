package dev.vitrail.render;

import dev.vitrail.glsl.EntityVertex;
import dev.vitrail.glsl.LinesVertex;

import java.util.HashSet;
import java.util.Set;

/**
 * Classifies synthesized vertex inputs for diagnostics without changing a mesh ABI.
 * <p>
 * Two questions stay separate here. {@link Inputs#answered()} is what the mesh really carries;
 * {@link Inputs#referenceDefaults()} is what the Iris 26.1 reference format also leaves without a
 * backing vertex array. The latter does not claim value-for-value parity: on Iris those unbacked
 * locations are OpenGL generic-attribute state, while Vitrail supplies deterministic synthesized
 * constants. It only says that absence from the vertex buffer is not a Vitrail-only missing field.
 * <p>
 * {@link Inputs#diagnosticAnswered()} is the union consumed by GeometryProgram's existing
 * missing-input filter. Keeping the union behind a diagnostic-named method prevents a reference
 * default from being mistaken for an element the mesh really owns.
 */
final class VertexInputDiagnostics {

	private static final Set<String> ENTITY_REFERENCE_DEFAULTS = Set.of("at_midBlock");
	private static final Set<String> PARTICLE_REFERENCE_DEFAULTS =
			Set.of("mc_Entity", "mc_midTexCoord", "at_tangent");

	private VertexInputDiagnostics() {
	}

	record Inputs(Set<String> answered, Set<String> referenceDefaults) {

		Inputs {
			answered = Set.copyOf(answered);
			referenceDefaults = Set.copyOf(referenceDefaults);
		}

		Set<String> diagnosticAnswered() {
			if (this.referenceDefaults.isEmpty()) {
				return this.answered;
			}

			Set<String> accepted = new HashSet<>(this.answered);
			accepted.addAll(this.referenceDefaults);
			return Set.copyOf(accepted);
		}
	}

	static Inputs entity(EntityDraw.Element element) {
		if (element.glint() || element.text()) {
			return new Inputs(Set.of(), Set.of());
		}

		if (element.lines()) {
			return new Inputs(LinesVertex.ANSWERED, Set.of());
		}

		return new Inputs(EntityVertex.ANSWERED, ENTITY_REFERENCE_DEFAULTS);
	}

	static Inputs particle() {
		return new Inputs(Set.of(), PARTICLE_REFERENCE_DEFAULTS);
	}

	static Inputs weather() {
		return particle();
	}
}
