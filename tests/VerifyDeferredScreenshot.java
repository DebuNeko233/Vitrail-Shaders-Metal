import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for PHASE 10 deferred-family ordering. */
public final class VerifyDeferredScreenshot {
    private VerifyDeferredScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyDeferredScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts counts = count(image);
        System.out.println("deferred screenshot swatches: BLUE=" + counts.blue()
                + " RED=" + counts.red() + " GREEN=" + counts.green()
                + " MAGENTA=" + counts.magenta() + " OTHER=" + counts.other());
        if (!passes(counts)) throw new AssertionError("Expected deferred -> deferred1 -> deferred2 to finish overwhelmingly BLUE");
        System.out.println("PHASE 10 deferred screenshot check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0 = image.getWidth() / 20, x1 = image.getWidth() * 19 / 20;
        int y0 = image.getHeight() / 20, y1 = image.getHeight() * 19 / 20;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 1600);
        int blue = 0, red = 0, green = 0, magenta = 0, other = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
                if (b >= 160 && r <= 80 && g <= 80) blue++;
                else if (r >= 160 && g <= 80 && b <= 80) red++;
                else if (g >= 160 && r <= 80 && b <= 80) green++;
                else if (r >= 160 && b >= 160 && g <= 100) magenta++;
                else other++;
            }
        }
        return new Counts(blue, red, green, magenta, other);
    }

    private static boolean passes(Counts counts) {
        int total = counts.blue() + counts.red() + counts.green() + counts.magenta() + counts.other();
        return total > 0 && counts.blue() >= 1024 && counts.blue() * 100L >= total * 90L
                && counts.red() * 100L <= total && counts.green() * 100L <= total
                && counts.magenta() * 100L <= total;
    }

    private static void selfTest() {
        BufferedImage good = solid(480, 300, 0xFF0000FF);
        if (!passes(count(good))) throw new AssertionError("BLUE success image failed");
        for (int failure : new int[] {0xFFFF0000, 0xFF00FF00, 0xFFFF00FF, 0xFF000000}) {
            Counts counts = count(solid(480, 300, failure));
            if (passes(counts)) throw new AssertionError("Failure swatch unexpectedly passed: " + counts);
        }
        System.out.println("Deferred screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        return image;
    }

    private record Counts(int blue, int red, int green, int magenta, int other) {}
}
