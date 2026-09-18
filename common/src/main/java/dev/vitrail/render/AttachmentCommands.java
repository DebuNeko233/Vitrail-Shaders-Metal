package dev.vitrail.render;

/**
 * Backend command-encoder capability for one already-decided statement about a render pass's
 * attachments.
 * <p>
 * Whether an attachment's contents are read after a pass, and whether the pass writes every pixel of
 * them anyway, are lifetime facts that stay above this seam: Vitrail decides them from the pack's
 * declarations, its own schedule and the pass's own text, and the backend only turns them into the
 * load and store actions its own API chooses between. Neither fact names a resource, which is the
 * whole point of the shape - a pass says "nothing reads this afterwards" and never says "this is
 * colortex7".
 * <p>
 * The conservative answer is {@code readAfterwards = true} and {@code overwritten = false} at every
 * slot, which is what a backend does when this is never called and what every call site must fall
 * back to when it does not know. A wrong "nothing reads this" is not a slower frame, it is a wrong
 * image that reads as a shader-pack defect.
 */
public interface AttachmentCommands {

	/**
	 * Declares what the next render pass created on this encoder needs of each of its colour
	 * attachments, by slot. Read once and dropped by that pass, so nothing leaks onto the next.
	 *
	 * @param readAfterwards one entry per colour attachment slot; false only from a positive fact
	 *                       that nothing reads the contents once this pass has ended
	 * @param overwritten    one entry per slot; true only from a pass that writes every pixel of it
	 */
	void vitrail$setNextPassContents(boolean[] readAfterwards, boolean[] overwritten);
}
