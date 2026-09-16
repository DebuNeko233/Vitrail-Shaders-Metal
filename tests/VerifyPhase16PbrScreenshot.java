import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the PHASE 16 static PBR companion-map checkpoint. */
public final class VerifyPhase16PbrScreenshot {
    private VerifyPhase16PbrScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyPhase16PbrScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts c = count(image);
        System.out.println("PHASE 16 PBR: GREEN=" + c.green + " MAGENTA=" + c.magenta + " OTHER=" + c.other);
        if (!pass(c)) throw new AssertionError("PHASE 16 PBR screenshot contract failed");
        System.out.println("PHASE 16 PBR normal/specular: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0 = image.getWidth() / 20, x1 = image.getWidth() * 19 / 20;
        int y0 = image.getHeight() / 20, y1 = image.getHeight() * 19 / 20;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 1600);
        long green = 0, magenta = 0, other = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int p = image.getRGB(x, y), r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
                if (g >= 160 && r <= 80 && b <= 80) green++;
                else if (r >= 160 && b >= 160 && g <= 100) magenta++;
                else other++;
            }
        }
        return new Counts(green, magenta, other);
    }

    private static boolean pass(Counts c) {
        return c.green >= 512 && c.magenta <= Math.max(32, c.green / 50);
    }

    private static void selfTest() {
        if (!pass(count(marker(false)))) throw new AssertionError("GREEN marker image rejected");
        if (pass(count(marker(true)))) throw new AssertionError("MAGENTA marker image accepted");
        if (pass(count(solid(0xFF050505)))) throw new AssertionError("No-marker image accepted");
        System.out.println("PHASE 16 PBR screenshot verifier self-test: PASS");
    }

    private static BufferedImage marker(boolean fail) {
        BufferedImage image = solid(0xFF050505);
        int colour = fail ? 0xFFFF00FF : 0xFF00FF00;
        for (int y = 80; y < 320; y++) for (int x = 180; x < 620; x++) image.setRGB(x, y, colour);
        return image;
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, argb);
        return image;
    }

    private record Counts(long green, long magenta, long other) {}
}
