package dev.vitrail.render.storage;

import dev.vitrail.mixin.access.CommandEncoderAccessor;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;

/**
 * The one recording helper storage work needs that sits outside a dynamic render pass.
 * <p>
 * <strong>Ending the pass is the whole of the ordering this engine asks for.</strong> Metal expresses
 * a dependency as an encoder boundary rather than as a mask: a render encoder is ended, a blit or a
 * compute encoder is encoded and ended, and the backend's own fence chain carries what a later reader
 * has to wait for. So the operations a pack-visible contract needs are stated as intent and nothing
 * here names a stage, an access flag or a layout.
 * <p>
 * An earlier shape of this built the driver's memory barriers directly - source and destination stage
 * masks, storage/sampled/transfer access bits, one barrier to order a copy against the next - because
 * the deleted backend had no other way to say "what the shaders wrote is visible to this transfer".
 * That vocabulary is gone with it. Where the effects those barriers protected still exist, they are
 * published as facts the backend acts on: {@code MetallumAttachmentBridge} tells the next pass what it
 * reads and what it overwrites, and the backend's own storage-write tracking decides where a boundary
 * is owed.
 */
public final class GpuRecording {

	private GpuRecording() {
	}

	/**
	 * Ends the encoder's open pass, if there is one, so a clear, copy or dispatch can be recorded.
	 * <p>
	 * Storage housekeeping can legally arrive between passes - shadow custom-image clears do - so this
	 * is "end if any" rather than "end": the backend answers the ordinary no-pass state by doing
	 * nothing, which is what keeps the call site from having to know whether a pass is open.
	 */
	public static void endPass(CommandEncoder encoder) {
		CommandEncoderBackend backend = ((CommandEncoderAccessor) encoder).vitrail$backend();
		backend.submitRenderPass();
	}
}
