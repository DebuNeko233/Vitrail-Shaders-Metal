import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the PHASE 9 shadow-terrain checkpoint. */
public final class VerifyShadowTerrainScreenshot {
    private VerifyShadowTerrainScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyShadowTerrainScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts counts = count(image);
        System.out.println("shadow terrain screenshot swatches: GREEN=" + counts.green()
                + " YELLOW=" + counts.yellow()
                + " BLUE=" + counts.blue()
                + " MAGENTA=" + counts.magenta());
        if (!passes(counts)) {
            throw new AssertionError("Expected independent green solid, yellow cutout and blue water shadow-terrain regions with no substantial magenta ABI failure region");
        }
        System.out.println("shadow terrain routing/vertex-ABI check: PASS");
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
        int yellow = 0;
        int blue = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (r >= 120 && g >= 120 && b <= 100) yellow++;
                if (b >= 120 && b >= r * 2 && b >= g * 2) blue++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(green, yellow, blue, magenta);
    }

    private static boolean passes(Counts counts) {
        int success = counts.green() + counts.yellow() + counts.blue();
        int magentaMaximum = Math.max(32, success / 20);
        return counts.green() >= 64 && counts.yellow() >= 64 && counts.blue() >= 64
                && counts.magenta() <= magentaMaximum;
    }

    private static void selfTest() {
        BufferedImage good = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(good, 0, 0, 480, 300, 0xFF000000);
        fill(good, 40, 40, 180, 260, 0xFF00CC00);
        fill(good, 180, 40, 320, 260, 0xFFCCCC00);
        fill(good, 320, 40, 440, 260, 0xFF0000CC);
        Counts pass = count(good);
        if (!passes(pass)) throw new AssertionError("good self-test failed: " + pass);

        int[] missing = {0xFF00CC00, 0xFFCCCC00, 0xFF0000CC};
        for (int omitted = 0; omitted < missing.length; omitted++) {
            BufferedImage bad = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
            fill(bad, 0, 0, 480, 300, 0xFF000000);
            int slot = 0;
            for (int i = 0; i < missing.length; i++) {
                if (i == omitted) continue;
                int left = 60 + slot * 180;
                fill(bad, left, 70, left + 140, 240, missing[i]);
                slot++;
            }
            if (passes(count(bad))) throw new AssertionError("missing-route self-test unexpectedly passed: " + omitted);
        }

        BufferedImage magenta = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(magenta, 0, 0, 480, 300, 0xFFFF00FF);
        if (passes(count(magenta))) throw new AssertionError("magenta failure self-test unexpectedly passed");

        System.out.println("Shadow terrain screenshot verifier self-test: PASS " + pass);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int green, int yellow, int blue, int magenta) {
    }
}
