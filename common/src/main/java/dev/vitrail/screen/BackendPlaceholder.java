package dev.vitrail.screen;

import dev.vitrail.HostReport;
import dev.vitrail.ScreenText;
import dev.vitrail.Vitrail;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * What a door of this mod to the pack screen opens on a session that cannot reach the Metal path.
 * <p>
 * There is one such screen and one message, where there used to be two: a session on Metal that never
 * got a device and a session on some other backend are the same statement now. Metal is the only
 * backend this engine draws on, so a pack picked or a setting moved on this engine's own screens
 * would change nothing on screen, and the one useful thing to say is the clause
 * {@link HostReport#diagnosis()} returns - which of the facts the Metal path needs is missing, and
 * therefore what to do about it.
 * <p>
 * <strong>This screen never writes the graphics API.</strong> An earlier shape of it offered a button
 * that switched the game to the other backend and closed it, which was the reference's gesture for the
 * same situation the other way round: Iris opens such a screen in place of its pack screen on a
 * renderer it cannot use ({@code IrisConfig.java:50-51}, {@code MixinMinecraft_Keybinds.java:23-27}),
 * and the layout,
 * the words with the two backends swapped and what the switch did were
 * {@code ShaderPackScreenPlaceholder.java}'s, line for line. There is no other backend to switch to
 * here, and the preference the Metal path is selected through belongs to the game and to Metallum
 * rather than to this mod, so the button that remains only goes back.
 */
public final class BackendPlaceholder extends Screen {

	private final @Nullable Screen parent;

	private @Nullable MultiLineLabel message;

	public BackendPlaceholder(@Nullable Screen parent) {
		super(Component.literal(Vitrail.MOD_NAME));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		MultiLineLabel label = MultiLineLabel.create(this.font,
				Component.translatable(ScreenText.BACKEND_PLACEHOLDER, HostReport.diagnosis()),
				this.width - 50);
		this.message = label;
		int textSize = (label.getLineCount() + 1) * 9;

		this.addRenderableWidget(Button.builder(Component.translatable(ScreenText.BACKEND_RETURN),
						_ -> onClose())
				.bounds(this.width / 2 - 75, 100 + textSize, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 50, -1);

		MultiLineLabel label = this.message;
		if (label != null) {
			label.visitLines(TextAlignment.CENTER, this.width / 2, 70, 9, graphics.textRenderer());
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(this.parent);
	}
}
