package dev.vitrail.screen;

import dev.vitrail.HostReport;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Which screen a door of this mod to the packs opens.
 * <p>
 * A session that reached the Metal path opens this engine's own settings. Every session that did not
 * gets {@link BackendPlaceholder} instead, which says which of the facts the Metal path needs is
 * missing: a pack picked or a setting moved on this engine's screens would change nothing on screen,
 * and beside Iris would write files Iris never reads while the pack Iris draws stayed where it was.
 * <p>
 * The device is asked rather than the options: a launch argument or a failed preferred backend can
 * leave the saved preference naming something other than the backend this session actually uses.
 */
public final class PackScreens {

	private PackScreens() {
	}

	/** The screen to open over {@code parent}. */
	public static Screen open(@Nullable Screen parent) {
		if (HostReport.otherBackend()) {
			return new BackendPlaceholder(parent);
		}

		return new SettingsScreen(parent);
	}
}
