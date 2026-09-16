import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier shared by the PHASE 7 hand_glint and hand_water_glint fixtures. */
public final class VerifyHandGlintScreenshot {
    private VerifyHandGlintScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyHandGlintScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);

        Counts counts = count(image);
        System.out.println("Hand-glint screenshot swatches: GREEN=" + counts.green()
                + " MAGENTA=" + counts.magenta() + " greenMinimum=" + counts.greenMinimum());
        if (!counts.passes()) {
            throw new AssertionError("Expected a visible green first-person glint region and no substantial magenta synthesized-input failure region");
        }
        System.out.println("Hand-glint screenshot routing/vertex-ABI colour check: PASS");
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
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(green, magenta, 24);
    }

    private static void selfTest() {
        BufferedImage pass = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(pass, 0, 0, 480, 300, 0xFF000000);
        fill(pass, 330, 185, 450, 285, 0xFF00CC00);
        Counts good = count(pass);
        if (!good.passes()) throw new AssertionError("Verifier pass self-test failed: " + good);

        BufferedImage fail = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(fail, 0, 0, 480, 300, 0xFF000000);
        fill(fail, 330, 185, 450, 285, 0xFFFF00FF);
        Counts bad = count(fail);
        if (bad.passes()) throw new AssertionError("Verifier failure self-test unexpectedly passed: " + bad);

        BufferedImage missing = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(missing, 0, 0, 480, 300, 0xFF000000);
        Counts absent = count(missing);
        if (absent.passes()) throw new AssertionError("Verifier missing-hand-glint self-test unexpectedly passed: " + absent);

        System.out.println("Hand-glint screenshot verifier self-test: PASS " + good);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int green, int magenta, int greenMinimum) {
        boolean passes() {
            int magentaMaximum = Math.max(8, green / 6);
            return green >= greenMinimum && magenta <= magentaMaximum;
        }
    }
}
