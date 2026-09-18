package dev.vitrail.uniform.values;

import dev.vitrail.uniform.UniformCatalog;
import dev.vitrail.uniform.UniformShape;

/**
 * The four shadow matrices, which are owed to a pack whether or not a shadow pass runs.
 * <p>
 * They are not the shadow map. They are where the light is and how it looks at the world, and a
 * composite reads them to place a sample it takes from somewhere else entirely; all eight packs of
 * the corpus read the two direct ones. Iris registers them unconditionally for the same reason, and
 * they cost nothing to compute: an unshifted camera position, the sky angle, and an orthographic
 * matrix built from the pack's own distance.
 * <p>
 * <strong>What they answer is the pair the map BEING SAMPLED was drawn with, moved onto this
 * frame's camera</strong> - which is the pair the stage draws with, and it is answered through
 * {@code drawnShadowModelView} and its three siblings for that reason rather than through the
 * {@code map} fields directly.
 * <p>
 * Those four names used to be the {@code map} pair, and that was right while the shadow draw stood
 * at the end of a frame, for the next one: the map on hand then really was the one that pair
 * described. The draw moved into the frame - it has to, so that a pack which voxelises into its
 * shadow pass shares one frame with the compute that reads the volume - and the premise went with
 * it. Publishing the {@code map} fields on a frame that fills the map hands every sampling pass a
 * matrix one draw old, so every lookup lands where the caster was a frame ago: invisible while the
 * camera is still, a displaced shadow the moment it moves, and worst on the fine shadow content a
 * pack reads for leaf and grass self-shadowing. {@link ShadowGeometryValues} already overrides
 * these four for the shadow programs, which is the same answer on either kind of frame; the two
 * layers now agree everywhere, and that override is kept because it also carries names these do
 * not.
 */
public final class ShadowMatrixValues {

	private ShadowMatrixValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("shadowModelView", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelView()));
		builder.add("shadowModelViewInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelViewInverse()));
		builder.add("shadowProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjection()));
		builder.add("shadowProjectionInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjectionInverse()));
	}
}
