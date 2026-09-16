import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Developer-only pixel verifier for the PHASE 7 sky fixture. */
public final class VerifySkyScreenshot {
    private VerifySkyScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 2 || !("--overworld".equals(args[0]) || "--end".equals(args[0]))) {
            System.err.println("Usage: java tests/VerifySkyScreenshot.java <--overworld|--end> <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);

        Counts counts = count(image);
        boolean overworld = "--overworld".equals(args[0]);
        System.out.println("Sky screenshot swatches: CYAN=" + counts.cyan() + " YELLOW=" + counts.yellow()
                + " GREEN=" + counts.green() + " MAGENTA=" + counts.magenta() + " mode=" + (overworld ? "overworld" : "end"));
        if (!(overworld ? counts.passesOverworld() : counts.passesEnd())) {
            throw new AssertionError(overworld
                    ? "Expected cyan basic-sky pixels, yellow fading-colour sky pixels, green textured-celestial pixels and no substantial magenta ABI failure region"
                    : "Expected broad green End-sky pixels and no substantial magenta POSITION_TEX_COLOR ABI failure region");
        }
        System.out.println("Sky screenshot routing/vertex-ABI colour check: PASS");
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
        int yellow = 0;
        int green = 0;
        int magenta = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g >= 120 && b >= 120 && r <= 100) cyan++;
                if (r >= 120 && g >= 120 && b <= 100) yellow++;
                if (g >= 120 && g >= r * 2 && g >= b * 2) green++;
                if (r >= 160 && b >= 160 && g <= 110) magenta++;
            }
        }
        return new Counts(cyan, yellow, green, magenta);
    }

    private static void selfTest() {
        BufferedImage overworld = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(overworld, 0, 0, 480, 300, 0xFF000000);
        fill(overworld, 40, 30, 440, 260, 0xFF00CCCC);
        fill(overworld, 120, 150, 360, 250, 0xFFFFFF00);
        fill(overworld, 210, 70, 300, 160, 0xFF00CC00);
        Counts ow = count(overworld);
        if (!ow.passesOverworld()) throw new AssertionError("Overworld verifier pass self-test failed: " + ow);

        BufferedImage end = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(end, 0, 0, 480, 300, 0xFF00CC00);
        Counts endCounts = count(end);
        if (!endCounts.passesEnd()) throw new AssertionError("End verifier pass self-test failed: " + endCounts);

        BufferedImage fail = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(fail, 0, 0, 480, 300, 0xFFFF00FF);
        Counts bad = count(fail);
        if (bad.passesOverworld() || bad.passesEnd()) throw new AssertionError("Verifier failure self-test unexpectedly passed: " + bad);

        System.out.println("Sky screenshot verifier self-test: PASS overworld=" + ow + " end=" + endCounts);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int cyan, int yellow, int green, int magenta) {
        boolean passesOverworld() {
            int success = cyan + yellow + green;
            int magentaMaximum = Math.max(32, success / 10);
            return cyan >= 24 && yellow >= 24 && green >= 24 && magenta <= magentaMaximum;
        }

        boolean passesEnd() {
            int magentaMaximum = Math.max(32, green / 8);
            return green >= 24 && magenta <= magentaMaximum;
        }
    }
}
