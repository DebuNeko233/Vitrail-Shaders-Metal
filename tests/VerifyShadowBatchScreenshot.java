import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the batched PHASE 9 shadow checkpoints. */
public final class VerifyShadowBatchScreenshot {
    private VerifyShadowBatchScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 2 || mode(args[0]) == null) {
            System.err.println("Usage: java tests/VerifyShadowBatchScreenshot.java <--entities|--depth|--color> <minecraft-screenshot.png>");
            System.exit(2);
        }

        Mode mode = mode(args[0]);
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);
        Counts counts = count(image);
        System.out.println("shadow batch screenshot swatches: GREEN=" + counts.green()
                + " BLUE=" + counts.blue()
                + " CYAN=" + counts.cyan()
                + " WHITE=" + counts.white()
                + " YELLOW=" + counts.yellow()
                + " RED=" + counts.red()
                + " MAGENTA=" + counts.magenta()
                + " mode=" + mode.label);
        if (!mode.passes(counts)) throw new AssertionError(mode.failure);
        System.out.println(mode.label + " shadow check: PASS");
    }

    private static Mode mode(String arg) {
        return switch (arg) {
            case "--entities" -> Mode.ENTITIES;
            case "--depth" -> Mode.DEPTH;
            case "--color" -> Mode.COLOR;
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
        int blue = 0;
        int cyan = 0;
        int white = 0;
        int yellow = 0;
        int red = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (b >= 120 && b >= r * 2 && b >= g * 2) blue++;
                if (g >= 120 && b >= 120 && r <= 100) cyan++;
                if (r >= 160 && g >= 160 && b >= 160
                        && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 48) white++;
                if (r >= 120 && g >= 120 && b <= 100) yellow++;
                if (r >= 120 && r >= g * 2 && r >= b * 2) red++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(green, blue, cyan, white, yellow, red, magenta);
    }

    private static void selfTest() {
        BufferedImage entities = diagnostic(0xFF0000CC, 0xFF00CC00);
        BufferedImage depth = diagnostic(0xFF00CCCC, 0xFFFFFFFF);
        BufferedImage color = diagnostic(0xFFCC0000, 0xFFCCCC00);

        Counts entitiesCounts = count(entities);
        Counts depthCounts = count(depth);
        Counts colorCounts = count(color);
        if (!Mode.ENTITIES.passes(entitiesCounts)) throw new AssertionError("entities self-test failed: " + entitiesCounts);
        if (!Mode.DEPTH.passes(depthCounts)) throw new AssertionError("depth self-test failed: " + depthCounts);
        if (!Mode.COLOR.passes(colorCounts)) throw new AssertionError("color self-test failed: " + colorCounts);

        for (Mode mode : Mode.values()) {
            for (Counts other : new Counts[] {entitiesCounts, depthCounts, colorCounts}) {
                boolean own = (mode == Mode.ENTITIES && other == entitiesCounts)
                        || (mode == Mode.DEPTH && other == depthCounts)
                        || (mode == Mode.COLOR && other == colorCounts);
                if (!own && mode.passes(other)) {
                    throw new AssertionError(mode.label + " accepted another mode's diagnostic colours: " + other);
                }
            }
        }

        BufferedImage invalid = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(invalid, 0, 0, 480, 300, 0xFFFF00FF);
        Counts bad = count(invalid);
        for (Mode mode : Mode.values()) {
            if (mode.passes(bad)) throw new AssertionError(mode.label + " invalid self-test unexpectedly passed: " + bad);
        }

        System.out.println("Shadow batch screenshot verifier self-test: PASS entities=" + entitiesCounts
                + " depth=" + depthCounts + " color=" + colorCounts);
    }

    private static BufferedImage diagnostic(int background, int foreground) {
        BufferedImage image = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 480, 300, background);
        fill(image, 100, 90, 380, 250, foreground);
        return image;
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private enum Mode {
        ENTITIES("shadow-entities", "Expected green real shadow-entity regions over blue terrain, with no substantial magenta ABI failure region"),
        DEPTH("shadow-depth", "Expected cyan equal-depth regions and white post-copy translucent-depth regions, with no substantial magenta semantic failure region"),
        COLOR("shadow-color", "Expected yellow paired shadowcolor0/1 writes and red clear background, with no substantial magenta attachment mismatch region");

        final String label;
        final String failure;

        Mode(String label, String failure) {
            this.label = label;
            this.failure = failure;
        }

        boolean passes(Counts counts) {
            return switch (this) {
                case ENTITIES -> passPair(counts.green(), counts.blue(), counts.magenta())
                        && counts.cyan() < 64 && counts.white() < 64 && counts.yellow() < 64 && counts.red() < 64;
                case DEPTH -> passPair(counts.cyan(), counts.white(), counts.magenta())
                        && counts.green() < 64 && counts.blue() < 64 && counts.yellow() < 64 && counts.red() < 64;
                case COLOR -> passPair(counts.yellow(), counts.red(), counts.magenta())
                        && counts.green() < 64 && counts.blue() < 64 && counts.cyan() < 64 && counts.white() < 64;
            };
        }

        private static boolean passPair(int first, int second, int magenta) {
            int success = first + second;
            return first >= 64 && second >= 64 && magenta <= Math.max(32, success / 20);
        }
    }

    private record Counts(int green, int blue, int cyan, int white, int yellow, int red, int magenta) {
    }
}
