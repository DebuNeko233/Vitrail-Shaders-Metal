package dev.vitrail.render;

import dev.vitrail.glsl.EntityVertex;
import dev.vitrail.glsl.LinesVertex;

import java.util.HashSet;
import java.util.Set;

/**
 * Classifies synthesized entity vertex inputs for diagnostics without changing the vertex ABI.
 *
 * <p>The real entity mesh answers only {@link EntityVertex#ANSWERED}. Iris 26.1's entity vertex
 * format also omits {@code at_midBlock}, including for shadow entities, so a synthesized zero for
 * that name is reference parity rather than evidence of a Vitrail-only missing attribute. Glint,
 * text and line meshes keep their own narrower answers until their reference paths are audited.
 */
final class EntityInputDiagnostics {

	private static final Set<String> REFERENCE_DEFAULTS = Set.of("at_midBlock");

	private EntityInputDiagnostics() {
	}

	static Set<String> answered(EntityDraw.Element element) {
		if (element.glint() || element.text()) {
			return Set.of();
		}

		if (element.lines()) {
			return LinesVertex.ANSWERED;
		}

		Set<String> compatible = new HashSet<>(EntityVertex.ANSWERED);
		compatible.addAll(REFERENCE_DEFAULTS);
		return Set.copyOf(compatible);
	}
}
