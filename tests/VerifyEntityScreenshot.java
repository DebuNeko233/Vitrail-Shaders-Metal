import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the PHASE 7 ordinary-entity fixture. */
public final class VerifyEntityScreenshot {
    private VerifyEntityScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyEntityScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) {
            throw new IllegalArgumentException("Not a readable image: " + args[0]);
        }

        Counts counts = count(image);
        System.out.println("Entity screenshot swatches: RED=" + counts.red()
                + " GREEN=" + counts.green() + " MAGENTA=" + counts.magenta()
                + " greenMinimum=" + counts.greenMinimum());
        if (!counts.passes()) {
            throw new AssertionError("Expected red terrain, a visible green ordinary entity, and no substantial magenta ABI failure region");
        }
        System.out.println("Entity screenshot vertex-ABI colour check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) {
            throw new IllegalArgumentException("Screenshot is too small: "
                    + image.getWidth() + "x" + image.getHeight());
        }

        int x0 = image.getWidth() / 20;
        int x1 = image.getWidth() * 19 / 20;
        int y0 = image.getHeight() / 20;
        int y1 = image.getHeight() * 19 / 20;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 1400);
        int red = 0;
        int green = 0;
        int magenta = 0;

        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (r >= 180 && g <= 90 && b <= 90) red++;
                if (g >= 180 && r <= 90 && b <= 90) green++;
                if (r >= 180 && g <= 90 && b >= 180) magenta++;
            }
        }

        int greenMinimum = 64;
        return new Counts(red, green, magenta, greenMinimum);
    }

    private static void selfTest() {
        BufferedImage pass = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(pass, 0, 0, 480, 300, 0xFFFF0000);
        fill(pass, 190, 80, 290, 240, 0xFF00FF00);
        Counts good = count(pass);
        if (!good.passes()) {
            throw new AssertionError("Verifier pass self-test failed: " + good);
        }

        BufferedImage fail = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(fail, 0, 0, 480, 300, 0xFFFF0000);
        fill(fail, 190, 80, 290, 240, 0xFFFF00FF);
        Counts bad = count(fail);
        if (bad.passes()) {
            throw new AssertionError("Verifier failure self-test unexpectedly passed: " + bad);
        }
        System.out.println("Entity screenshot verifier self-test: PASS " + good);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int red, int green, int magenta, int greenMinimum) {
        boolean passes() {
            int magentaMaximum = Math.max(24, green / 8);
            return red >= 256 && green >= greenMinimum && magenta <= magentaMaximum;
        }
    }
}
