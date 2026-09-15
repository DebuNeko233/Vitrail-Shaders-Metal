import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the final PHASE 9 shadow mipmap checkpoint. */
public final class VerifyShadowMipmapScreenshot {
    private VerifyShadowMipmapScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyShadowMipmapScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts counts = count(image);
        System.out.println("shadow mipmap screenshot swatches: GREEN=" + counts.green()
                + " BLUE=" + counts.blue() + " MAGENTA=" + counts.magenta());
        if (!passes(counts)) {
            throw new AssertionError("Expected both GREEN reduced-depth and BLUE matching-depth regions, with low MAGENTA failure area");
        }
        System.out.println("PHASE 9 shadow mipmaps screenshot check: PASS");
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
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (b >= 120 && b >= r * 2 && b >= g * 2) blue++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(green, blue, magenta);
    }

    private static boolean passes(Counts counts) {
        int success = counts.green() + counts.blue();
        return counts.green() >= 64 && counts.blue() >= 64
                && counts.magenta() <= Math.max(32, success / 20);
    }

    private static void selfTest() {
        BufferedImage good = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(good, 0, 0, 480, 300, 0xFF0000CC);
        fill(good, 100, 90, 380, 250, 0xFF00CC00);
        Counts goodCounts = count(good);
        if (!passes(goodCounts)) throw new AssertionError("good self-test failed: " + goodCounts);

        BufferedImage clamped = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(clamped, 0, 0, 480, 300, 0xFF0000CC);
        Counts clampedCounts = count(clamped);
        if (passes(clampedCounts)) throw new AssertionError("level-zero clamp unexpectedly passed: " + clampedCounts);

        BufferedImage invalid = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(invalid, 0, 0, 480, 300, 0xFFFF00FF);
        Counts invalidCounts = count(invalid);
        if (passes(invalidCounts)) throw new AssertionError("invalid depth unexpectedly passed: " + invalidCounts);

        System.out.println("Shadow mipmap screenshot verifier self-test: PASS good=" + goodCounts
                + " clamped=" + clampedCounts + " invalid=" + invalidCounts);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int green, int blue, int magenta) {
    }
}
