import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the batched PHASE 8 depth checkpoints. */
public final class VerifyDepthBatchScreenshot {
    private VerifyDepthBatchScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 2 || mode(args[0]) == null) {
            System.err.println("Usage: java tests/VerifyDepthBatchScreenshot.java <--depthtex1|--depthtex2|--pre-translucent> <minecraft-screenshot.png>");
            System.exit(2);
        }

        Mode mode = mode(args[0]);
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);

        Counts counts = count(image);
        System.out.println("depth batch screenshot swatches: BLUE=" + counts.blue()
                + " YELLOW=" + counts.yellow()
                + " RED=" + counts.red()
                + " CYAN=" + counts.cyan()
                + " GREEN=" + counts.green()
                + " WHITE=" + counts.white()
                + " MAGENTA=" + counts.magenta()
                + " mode=" + mode.label);
        if (!mode.passes(counts)) {
            throw new AssertionError(mode.failure);
        }
        System.out.println(mode.label + " depth sampling check: PASS");
    }

    private static Mode mode(String arg) {
        return switch (arg) {
            case "--depthtex1" -> Mode.DEPTHTEX1;
            case "--depthtex2" -> Mode.DEPTHTEX2;
            case "--pre-translucent" -> Mode.PRE_TRANSLUCENT;
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

        int blue = 0;
        int yellow = 0;
        int red = 0;
        int cyan = 0;
        int green = 0;
        int white = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (b >= 120 && b >= r * 2 && b >= g * 2) blue++;
                if (r >= 120 && g >= 120 && b <= 100) yellow++;
                if (r >= 120 && r >= g * 2 && r >= b * 2) red++;
                if (g >= 120 && b >= 120 && r <= 100) cyan++;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (r >= 160 && g >= 160 && b >= 160
                        && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 48) white++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(blue, yellow, red, cyan, green, white, magenta);
    }

    private static void selfTest() {
        BufferedImage depthtex1 = diagnostic(0xFF0000CC, 0xFFFFFF00);
        BufferedImage depthtex2 = diagnostic(0xFFCC0000, 0xFF00CCCC);
        BufferedImage pre = diagnostic(0xFF00CC00, 0xFFFFFFFF);
        Counts one = count(depthtex1);
        Counts two = count(depthtex2);
        Counts early = count(pre);

        if (!Mode.DEPTHTEX1.passes(one)) throw new AssertionError("depthtex1 pass self-test failed: " + one);
        if (!Mode.DEPTHTEX2.passes(two)) throw new AssertionError("depthtex2 pass self-test failed: " + two);
        if (!Mode.PRE_TRANSLUCENT.passes(early)) throw new AssertionError("pre-translucent pass self-test failed: " + early);

        if (Mode.DEPTHTEX2.passes(one) || Mode.PRE_TRANSLUCENT.passes(one)
                || Mode.DEPTHTEX1.passes(two) || Mode.PRE_TRANSLUCENT.passes(two)
                || Mode.DEPTHTEX1.passes(early) || Mode.DEPTHTEX2.passes(early)) {
            throw new AssertionError("A mode accepted another mode's diagnostic colours");
        }

        BufferedImage invalid = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(invalid, 0, 0, 480, 300, 0xFFFF00FF);
        Counts bad = count(invalid);
        for (Mode mode : Mode.values()) {
            if (mode.passes(bad)) throw new AssertionError(mode.label + " invalid-depth self-test unexpectedly passed: " + bad);
        }

        System.out.println("Depth batch screenshot verifier self-test: PASS depthtex1=" + one
                + " depthtex2=" + two + " pre-translucent=" + early);
    }

    private static BufferedImage diagnostic(int constant, int variation) {
        BufferedImage image = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 480, 300, constant);
        fill(image, 100, 100, 380, 270, variation);
        return image;
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private enum Mode {
        DEPTHTEX1("depthtex1", "Expected blue constant opaque-depth regions and yellow live variation, with no substantial magenta invalid-depth region"),
        DEPTHTEX2("depthtex2", "Expected red constant opaque-depth regions and cyan live variation, with no substantial magenta invalid-depth region"),
        PRE_TRANSLUCENT("pre-translucent", "Expected green constant pre-translucent depth regions and white live variation, with no substantial magenta invalid-depth region");

        final String label;
        final String failure;

        Mode(String label, String failure) {
            this.label = label;
            this.failure = failure;
        }

        boolean passes(Counts counts) {
            int first;
            int second;
            switch (this) {
                case DEPTHTEX1 -> {
                    first = counts.blue();
                    second = counts.yellow();
                }
                case DEPTHTEX2 -> {
                    first = counts.red();
                    second = counts.cyan();
                }
                case PRE_TRANSLUCENT -> {
                    first = counts.green();
                    second = counts.white();
                }
                default -> throw new IllegalStateException();
            }
            int success = first + second;
            int magentaMaximum = Math.max(32, success / 20);
            return first >= 64 && second >= 64 && counts.magenta() <= magentaMaximum;
        }
    }

    private record Counts(int blue, int yellow, int red, int cyan, int green, int white, int magenta) {
    }
}
