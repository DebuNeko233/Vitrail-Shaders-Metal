package dev.vitrail.screen;

import dev.vitrail.HostReport;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Which screen a door of this mod to the packs opens.
 * <p>
 * Vulkan, and a Metal session that has passed the explicit developer validation gate, open this
 * engine's own settings. OpenGL keeps the old offer to switch to Vulkan. An actual Metal device that
 * is still blocked by the validation gate gets a Metal-specific explanation instead, so it is never
 * mislabeled as OpenGL and Vitrail never rewrites Metallum's preference on that path.
 * <p>
 * Off a backend this engine draws on, a pack picked or a setting moved on its screen would change
 * nothing on screen, and beside Iris would write files Iris never reads while the pack Iris draws
 * stayed where it was. The page of Sodium's video settings and the Config button of NeoForge's mod
 * list therefore open {@link BackendPlaceholder} there. The device is asked rather than the options:
 * a launch argument or a failed preferred backend can leave the saved preference naming something
 * other than the backend this session actually uses.
 */
public final class PackScreens {

	private static final String METAL = "Metal";

	private PackScreens() {
	}

	/** The screen to open over {@code parent}. */
	public static Screen open(@Nullable Screen parent) {
		if (HostReport.otherBackend()) {
			if (METAL.equals(HostReport.backend())) {
				return BackendPlaceholder.metalValidation(parent);
			}

			return new BackendPlaceholder(parent);
		}

		return new SettingsScreen(parent);
	}
}
