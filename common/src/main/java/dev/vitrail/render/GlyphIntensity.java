package dev.vitrail.render;

import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The single channel font sheets, seen through a view that reads the one channel four times.
 * <p>
 * <strong>What this exists to prevent is a glyph drawn as an opaque red box.</strong> A glyph baked
 * from a TrueType provider answers false to {@code isColored}, where the bitmap and unihex providers
 * a vanilla font is made of answer true, so its sheet is allocated {@code GpuFormat.R8_UNORM} rather
 * than {@code RGBA8_UNORM} ({@code FontTexture.java:31}) and a sampler over it hands back the
 * coverage in red, nought in green and blue, and one in alpha. The game answers that in its own
 * fragment stage, which the three grayscale pipelines compile with the {@code IS_GRAYSCALE} define
 * and which is one line, {@code vec4 texColor = texture(Sampler0, texCoord0).rrrr}
 * ({@code assets/minecraft/shaders/core/text.fsh}). A pack's {@code gbuffers_entities_translucent}
 * has no such line and no way to know it needs one: it multiplies {@code gtexture} by the vertex
 * colour, reads alpha one everywhere, and passes the alpha test over the whole quad.
 * <p>
 * <strong>This is the reference implementation's answer, moved onto the resource.</strong> Iris
 * swizzles the ALBEDO TEXTURE for as long as an intensity program is bound, setting
 * {@code GL_TEXTURE_SWIZZLE_RGBA} to red four times
 * ({@code pipeline/programs/ExtendedShader.java:198-201}, on the rows {@code ShaderKey.isIntensity}
 * names at {@code pipeline/programs/ShaderKey.java:128}) and putting the identity back at the next
 * program use ({@code gl/IrisRenderSystem.java:527-533}). That is texture state a bound program can
 * change. On this platform the equivalent is fixed when the resource is described, so a pack's
 * program cannot reach it and the engine has to hand it a resource that already reads the way the
 * pack expects: {@link IntensityViewBackend} is that request, and a backend that cannot serve it
 * returns null.
 * <p>
 * <strong>An unserved request is said out loud rather than passed over.</strong> Where no capability
 * answers, or the one that does declines, the game's view is handed back and the defect is named once
 * per session. The glyph is then drawn red, which is what the pack sees; what is not acceptable is
 * that happening with nothing anywhere to explain it. Where the fix belongs is on the backend's side
 * of the seam - a resource whose description carries the mapping - and not here: a second view of the
 * game's image cannot carry it, which is what the road this replaced tried to do and what the
 * platform does not offer.
 * <p>
 * <strong>The game's own view is untouched</strong>, which is what a view of our own buys: the game's
 * text fragment reads {@code .rrrr} off the raw sheet, and a sheet rewritten underneath it would
 * still work but every other reader of that image would silently change.
 * <p>
 * <strong>The sheets are followed rather than owned.</strong> A view holds its image alive, so ours
 * are closed as soon as the game closes the sheet under them, which is what a font reload does. The
 * map is walked at every lookup rather than emptied on a hook: it holds one entry per grayscale page,
 * which is nought for a resource pack that ships no TrueType provider.
 */
public final class GlyphIntensity {

	/**
	 * Our view per game sheet, by identity: two {@code GpuTexture} instances are the same sheet only
	 * when they are the same object, and the class defines no equality of its own.
	 */
	private static final Map<GpuTexture, GpuTextureView> VIEWS = new IdentityHashMap<>();

	/** Whether the unserved request has been named, so a frame does not repeat it. */
	private static boolean announced;

	private GlyphIntensity() {
	}

	/**
	 * The view a pack's program samples for one grayscale draw, which is ours where the backend can
	 * make one and the game's where it cannot.
	 * <p>
	 * Handing the game's back is a real answer and not a silent failure: the glyph is then drawn red,
	 * which is the defect, but the frame is drawn. The three ways in are a device this engine has not
	 * got yet, a backend that does not serve the request, and a sheet that is not single channel - the
	 * last being what would happen if a grayscale pipeline were ever handed a coloured sheet, where
	 * broadcasting one channel over four would drain the green and the blue out of a coloured glyph
	 * and give a worse picture than not swizzling at all.
	 */
	static GpuTextureView view(GpuTextureView sheet) {
		release();

		GpuTexture texture = sheet.texture();
		if (texture.isClosed() || texture.getFormat() != GpuFormat.R8_UNORM) {
			return sheet;
		}

		GpuTextureView held = VIEWS.get(texture);
		if (held != null) {
			return held;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return sheet;
		}

		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).vitrail$backend();
		if (!(backend instanceof IntensityViewBackend intensity)) {
			announce(texture.getLabel(), "the active backend does not serve the request");

			return sheet;
		}

		GpuTextureView made;
		try {
			made = intensity.vitrail$createIntensityView(sheet);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			Vitrail.logger().error("Could not build an intensity view of the font sheet "
					+ texture.getLabel() + ", so the pack reads it with one channel and draws its "
					+ "text red", e);

			return sheet;
		}

		if (made == null) {
			announce(texture.getLabel(), "the active backend cannot describe a resource that way");

			return sheet;
		}

		VIEWS.put(texture, made);

		return made;
	}

	/**
	 * Names the defect once, with what it costs and where the fix belongs. Once and not per draw: the
	 * condition is a property of the session's backend, so a second line would say nothing the first
	 * did not.
	 */
	private static void announce(String label, String why) {
		if (announced) {
			return;
		}

		announced = true;
		Vitrail.logger().warn("The font sheet {} is single channel and no intensity view was made "
				+ "for it, because {}. A shader pack's text will read that channel as red and draw "
				+ "an opaque box where the game draws a glyph. The mapping is a property of the "
				+ "resource on this platform, so the fix is a backend that can describe one; nothing "
				+ "on this side of the seam can add it to an existing image", label, why);
	}

	/**
	 * Lets go of the views whose sheet the game has closed, which is the only thing keeping those
	 * images alive by then.
	 */
	private static void release() {
		Iterator<Map.Entry<GpuTexture, GpuTextureView>> held = VIEWS.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<GpuTexture, GpuTextureView> entry = held.next();
			if (!entry.getKey().isClosed()) {
				continue;
			}

			entry.getValue().close();
			held.remove();
		}
	}
}
