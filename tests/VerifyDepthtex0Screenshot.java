import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the PHASE 8 depthtex0 fixture. */
public final class VerifyDepthtex0Screenshot {
    private VerifyDepthtex0Screenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyDepthtex0Screenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);

        Counts counts = count(image);
        System.out.println("depthtex0 screenshot swatches: CYAN=" + counts.cyan()
                + " GREEN=" + counts.green() + " MAGENTA=" + counts.magenta());
        if (!counts.passes()) {
            throw new AssertionError("Expected both cyan constant-depth regions and green live-depth variation, with no substantial magenta invalid-depth region");
        }
        System.out.println("depthtex0 scene-depth sampling check: PASS");
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
        int cyan = 0;
        int green = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && b >= 120 && r <= 100) cyan++;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(cyan, green, magenta);
    }

    private static void selfTest() {
        BufferedImage pass = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(pass, 0, 0, 480, 300, 0xFF00CCCC);
        fill(pass, 100, 100, 380, 270, 0xFF00CC00);
        Counts good = count(pass);
        if (!good.passes()) throw new AssertionError("Verifier pass self-test failed: " + good);

        BufferedImage fallback = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(fallback, 0, 0, 480, 300, 0xFF00CCCC);
        Counts whiteDepth = count(fallback);
        if (whiteDepth.passes()) throw new AssertionError("Constant-depth fallback self-test unexpectedly passed: " + whiteDepth);

        BufferedImage invalid = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(invalid, 0, 0, 480, 300, 0xFFFF00FF);
        Counts bad = count(invalid);
        if (bad.passes()) throw new AssertionError("Invalid-depth self-test unexpectedly passed: " + bad);

        System.out.println("depthtex0 screenshot verifier self-test: PASS " + good);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int cyan, int green, int magenta) {
        boolean passes() {
            int success = cyan + green;
            int magentaMaximum = Math.max(32, success / 20);
            return cyan >= 64 && green >= 64 && magenta <= magentaMaximum;
        }
    }
}
