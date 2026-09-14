import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the final PHASE 8 depth checkpoints. */
public final class VerifyDepthFinalScreenshot {
    private VerifyDepthFinalScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 2 || mode(args[0]) == null) {
            System.err.println("Usage: java tests/VerifyDepthFinalScreenshot.java <--pre-hand|--conversion> <minecraft-screenshot.png>");
            System.exit(2);
        }

        Mode mode = mode(args[0]);
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);

        Counts counts = count(image);
        System.out.println("depth final screenshot swatches: GREEN=" + counts.green()
                + " CYAN=" + counts.cyan()
                + " WHITE=" + counts.white()
                + " BLUE=" + counts.blue()
                + " MAGENTA=" + counts.magenta()
                + " mode=" + mode.label);
        if (!mode.passes(counts)) throw new AssertionError(mode.failure);
        System.out.println(mode.label + " depth check: PASS");
    }

    private static Mode mode(String arg) {
        return switch (arg) {
            case "--pre-hand" -> Mode.PRE_HAND;
            case "--conversion" -> Mode.CONVERSION;
            default -> null;
        };
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) {
            throw new IllegalArgumentException("Screenshot is too small: " + image.getWidth() + "x" + image.getHeight());
        }
        int x0 = image.getWidth() / 20;
        int x1 = image.getWidth() * 19 / 20;
        int y0 = image.getHeight() / 20;
        int y1 = image.getHeight() * 19 / 20;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 1600);

        int green = 0;
        int cyan = 0;
        int white = 0;
        int blue = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (g >= 120 && b >= 120 && r <= 100) cyan++;
                if (r >= 160 && g >= 160 && b >= 160
                        && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 48) white++;
                if (b >= 120 && b >= r * 2 && b >= g * 2) blue++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(green, cyan, white, blue, magenta);
    }

    private static void selfTest() {
        BufferedImage pre = diagnostic(0xFF00CCCC, 0xFF00CC00);
        BufferedImage conversion = diagnostic(0xFFFFFFFF, 0xFF00CC00);
        fill(conversion, 200, 60, 300, 130, 0xFF0000CC);
        Counts preCounts = count(pre);
        Counts conversionCounts = count(conversion);

        if (!Mode.PRE_HAND.passes(preCounts)) throw new AssertionError("pre-hand pass self-test failed: " + preCounts);
        if (!Mode.CONVERSION.passes(conversionCounts)) throw new AssertionError("conversion pass self-test failed: " + conversionCounts);
        if (Mode.CONVERSION.passes(preCounts) || Mode.PRE_HAND.passes(conversionCounts)) {
            throw new AssertionError("A mode accepted another mode's diagnostic colours");
        }

        BufferedImage invalid = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(invalid, 0, 0, 480, 300, 0xFFFF00FF);
        Counts bad = count(invalid);
        for (Mode mode : Mode.values()) {
            if (mode.passes(bad)) throw new AssertionError(mode.label + " invalid self-test unexpectedly passed: " + bad);
        }

        System.out.println("Depth final screenshot verifier self-test: PASS pre-hand=" + preCounts
                + " conversion=" + conversionCounts);
    }

    private static BufferedImage diagnostic(int background, int foreground) {
        BufferedImage image = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 480, 300, background);
        fill(image, 100, 100, 380, 270, foreground);
        return image;
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private enum Mode {
        PRE_HAND("pre-hand", "Expected cyan matching world regions and green same-frame hand-only depth difference, with no substantial magenta mismatch"),
        CONVERSION("conversion", "Expected green converted near-plane hand depth and white converted far-plane clear depth, with blue mid-depth allowed and no substantial magenta invalid depth");

        final String label;
        final String failure;

        Mode(String label, String failure) {
            this.label = label;
            this.failure = failure;
        }

        boolean passes(Counts counts) {
            return switch (this) {
                case PRE_HAND -> {
                    int success = counts.green() + counts.cyan();
                    int magentaMaximum = Math.max(32, success / 20);
                    yield counts.green() >= 64 && counts.cyan() >= 64
                            && counts.white() < 64 && counts.magenta() <= magentaMaximum;
                }
                case CONVERSION -> {
                    int success = counts.green() + counts.white() + counts.blue();
                    int magentaMaximum = Math.max(32, success / 20);
                    yield counts.green() >= 64 && counts.white() >= 64
                            && counts.cyan() < 64 && counts.magenta() <= magentaMaximum;
                }
            };
        }
    }

    private record Counts(int green, int cyan, int white, int blue, int magenta) {
    }
}
