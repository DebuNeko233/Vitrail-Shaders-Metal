import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the deterministic PHASE 16 advanced-feature checkpoint. */
public final class VerifyPhase16AdvancedScreenshot {
    private static final String[] NAMES = {"VOLUME", "NOISE", "BLEND", "COMPARE"};

    private VerifyPhase16AdvancedScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyPhase16AdvancedScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts[] counts = count(image);
        boolean all = true;
        for (int i = 0; i < counts.length; i++) {
            boolean pass = pass(counts[i]);
            System.out.println("PHASE 16 " + NAMES[i] + ": GREEN=" + counts[i].green
                + " MAGENTA=" + counts[i].magenta + " OTHER=" + counts[i].other
                + " -> " + (pass ? "PASS" : "FAIL"));
            all &= pass;
        }
        if (!all) throw new AssertionError("PHASE 16 deterministic advanced screenshot contract failed");
        System.out.println("PHASE 16 deterministic advanced features: PASS");
    }

    private static Counts[] count(BufferedImage image) {
        if (image.getWidth() < 160 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        Counts[] counts = {new Counts(), new Counts(), new Counts(), new Counts()};
        int y0 = image.getHeight() / 20, y1 = image.getHeight() * 19 / 20;
        int margin = Math.max(1, image.getWidth() / 80);
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 1600);
        for (int quarter = 0; quarter < 4; quarter++) {
            int x0 = image.getWidth() * quarter / 4 + margin;
            int x1 = image.getWidth() * (quarter + 1) / 4 - margin;
            for (int y = y0; y < y1; y += step) {
                for (int x = x0; x < x1; x += step) {
                    int p = image.getRGB(x, y), r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
                    if (g >= 160 && r <= 80 && b <= 80) counts[quarter].green++;
                    else if (r >= 160 && b >= 160 && g <= 100) counts[quarter].magenta++;
                    else counts[quarter].other++;
                }
            }
        }
        return counts;
    }

    private static boolean pass(Counts c) {
        long total = c.total();
        return total > 0 && c.green >= 256 && c.green * 100 >= total * 90 && c.magenta * 100 <= total;
    }

    private static void selfTest() {
        BufferedImage success = quarters(-1);
        for (Counts c : count(success)) if (!pass(c)) throw new AssertionError("GREEN quarter rejected");
        for (int fail = 0; fail < 4; fail++) {
            Counts[] counts = count(quarters(fail));
            for (int i = 0; i < 4; i++) {
                boolean shouldPass = i != fail;
                if (pass(counts[i]) != shouldPass) {
                    throw new AssertionError("Quarter self-test mismatch at fail=" + fail + " checked=" + i);
                }
            }
        }
        System.out.println("PHASE 16 advanced screenshot verifier self-test: PASS");
    }

    private static BufferedImage quarters(int magentaQuarter) {
        BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int q = Math.min(3, x * 4 / image.getWidth());
                image.setRGB(x, y, q == magentaQuarter ? 0xFFFF00FF : 0xFF00FF00);
            }
        }
        return image;
    }

    private static final class Counts {
        long green;
        long magenta;
        long other;
        long total() { return green + magenta + other; }
    }
}
