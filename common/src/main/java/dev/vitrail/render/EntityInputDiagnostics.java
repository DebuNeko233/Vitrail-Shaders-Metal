package dev.vitrail.render;

import java.util.Set;

/**
 * Entity-family bridge to the shared vertex-input diagnostic classifier.
 * <p>
 * Kept as the call site named by {@link EntityProgram}; the real mesh-backed inputs and the
 * reference-unbacked inputs are separated by {@link VertexInputDiagnostics}. Nothing returned here
 * is used to change the entity vertex format or stride.
 */
final class EntityInputDiagnostics {

	private EntityInputDiagnostics() {
	}

	static Set<String> answered(EntityDraw.Element element) {
		return VertexInputDiagnostics.entity(element).diagnosticAnswered();
	}
}
