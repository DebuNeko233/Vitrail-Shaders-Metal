import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Developer-only verifier for the mrt-contract smoke pack's final screenshot.
 *
 * Run directly with Java 25 source-file mode:
 *   java tests/VerifyMrtScreenshot.java /path/to/screenshot.png
 */
public final class VerifyMrtScreenshot {
    private enum Swatch {
        RED,
        GREEN,
        BLUE,
        WHITE,
        OTHER
    }

    private static final Set<Swatch> EXPECTED = EnumSet.of(
            Swatch.RED,
            Swatch.GREEN,
            Swatch.BLUE,
            Swatch.WHITE
    );

    private VerifyMrtScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyMrtScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) {
            throw new IllegalArgumentException("Not a readable image: " + args[0]);
        }

        List<Swatch> quadrants = classifyQuadrants(image);
        System.out.println("MRT screenshot quadrant swatches: " + quadrants);
        if (!passes(quadrants)) {
            throw new AssertionError(
                    "Expected one red, green, blue and white quadrant in any orientation, got " + quadrants
            );
        }
        System.out.println("MRT screenshot pixel check: PASS");
    }

    private static List<Swatch> classifyQuadrants(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 16 || height < 16) {
            throw new IllegalArgumentException("Screenshot is too small: " + width + "x" + height);
        }

        List<Swatch> result = new ArrayList<>(4);
        result.add(classifyRegion(image, width / 4, height / 4));
        result.add(classifyRegion(image, width * 3 / 4, height / 4));
        result.add(classifyRegion(image, width / 4, height * 3 / 4));
        result.add(classifyRegion(image, width * 3 / 4, height * 3 / 4));
        return result;
    }

    private static Swatch classifyRegion(BufferedImage image, int centerX, int centerY) {
        int radiusX = Math.max(1, image.getWidth() / 24);
        int radiusY = Math.max(1, image.getHeight() / 24);
        Map<Swatch, Integer> counts = new HashMap<>();
        int samples = 0;

        for (int gy = -2; gy <= 2; gy++) {
            for (int gx = -2; gx <= 2; gx++) {
                int x = clamp(centerX + gx * radiusX / 2, 0, image.getWidth() - 1);
                int y = clamp(centerY + gy * radiusY / 2, 0, image.getHeight() - 1);
                Swatch swatch = classifyPixel(image.getRGB(x, y));
                counts.merge(swatch, 1, Integer::sum);
                samples++;
            }
        }

        Swatch best = Swatch.OTHER;
        int bestCount = 0;
        for (Swatch swatch : EXPECTED) {
            int count = counts.getOrDefault(swatch, 0);
            if (count > bestCount) {
                best = swatch;
                bestCount = count;
            }
        }

        return bestCount * 5 >= samples * 3 ? best : Swatch.OTHER;
    }

    private static Swatch classifyPixel(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        if (red >= 180 && green <= 100 && blue <= 100) {
            return Swatch.RED;
        }
        if (green >= 180 && red <= 100 && blue <= 100) {
            return Swatch.GREEN;
        }
        if (blue >= 180 && red <= 100 && green <= 100) {
            return Swatch.BLUE;
        }
        if (red >= 180 && green >= 180 && blue >= 180) {
            return Swatch.WHITE;
        }
        return Swatch.OTHER;
    }

    private static boolean passes(List<Swatch> quadrants) {
        return quadrants.size() == 4 && Set.copyOf(quadrants).equals(EXPECTED);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void selfTest() {
        BufferedImage image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 200, 120, 0xFFFF0000);
        fill(image, 200, 0, 400, 120, 0xFF00FF00);
        fill(image, 0, 120, 200, 240, 0xFF0000FF);
        fill(image, 200, 120, 400, 240, 0xFFFFFFFF);

        List<Swatch> quadrants = classifyQuadrants(image);
        if (!passes(quadrants)) {
            throw new AssertionError("Verifier self-test failed: " + quadrants);
        }
        System.out.println("MRT screenshot verifier self-test: PASS " + quadrants);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}
