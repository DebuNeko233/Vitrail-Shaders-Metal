import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for PHASE 10 deferred depth sampling. */
public final class VerifyDeferredDepthScreenshot {
    private VerifyDeferredDepthScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyDeferredDepthScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts counts = count(image);
        System.out.println("deferred depth screenshot swatches: RED=" + counts.red()
                + " YELLOW=" + counts.yellow()
                + " MAGENTA=" + counts.magenta()
                + " OTHER=" + counts.other());
        if (!passes(counts)) {
            throw new AssertionError("Expected both constant and varying deferred depth regions with no substantial MAGENTA failure area");
        }
        System.out.println("PHASE 10 deferred depth screenshot check: PASS");
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

        int red = 0;
        int yellow = 0;
        int magenta = 0;
        int other = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (r >= 120 && r >= g * 2 && r >= b * 2) red++;
                else if (r >= 120 && g >= 120 && b <= 100) yellow++;
                else if (r >= 160 && b >= 160 && g <= 110) magenta++;
                else other++;
            }
        }
        return new Counts(red, yellow, magenta, other);
    }

    private static boolean passes(Counts counts) {
        int success = counts.red() + counts.yellow();
        int magentaMaximum = Math.max(32, success / 20);
        return counts.red() >= 64 && counts.yellow() >= 64 && counts.magenta() <= magentaMaximum;
    }

    private static void selfTest() {
        BufferedImage good = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(good, 0, 0, 480, 300, 0xFFCC0000);
        fill(good, 100, 80, 380, 260, 0xFFFFFF00);
        Counts accepted = count(good);
        if (!passes(accepted)) throw new AssertionError("RED+YELLOW success self-test failed: " + accepted);

        BufferedImage onlyRed = solid(480, 300, 0xFFCC0000);
        BufferedImage onlyYellow = solid(480, 300, 0xFFFFFF00);
        BufferedImage invalid = solid(480, 300, 0xFFFF00FF);
        if (passes(count(onlyRed))) throw new AssertionError("All-RED image must not prove live depth variation");
        if (passes(count(onlyYellow))) throw new AssertionError("All-YELLOW image must not prove constant-depth area");
        if (passes(count(invalid))) throw new AssertionError("MAGENTA invalid-depth image unexpectedly passed");

        System.out.println("Deferred depth screenshot verifier self-test: PASS " + accepted);
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, width, height, argb);
        return image;
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int red, int yellow, int magenta, int other) {
    }
}
