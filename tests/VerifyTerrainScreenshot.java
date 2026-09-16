import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

/** Developer-only coarse pixel verifier for the terrain-contract smoke pack. */
public final class VerifyTerrainScreenshot {
    private enum Swatch { RED, GREEN, BLUE, OTHER }

    private VerifyTerrainScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyTerrainScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }

        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) {
            throw new IllegalArgumentException("Not a readable image: " + args[0]);
        }

        Counts counts = count(image);
        System.out.println("Terrain screenshot swatches: RED=" + counts.red()
                + " GREEN=" + counts.green() + " BLUE=" + counts.blue()
                + " minimum=" + counts.minimum());
        if (!counts.passes()) {
            throw new AssertionError("Expected substantial red, green and blue terrain regions");
        }
        System.out.println("Terrain screenshot pass-color check: PASS");
        System.out.println("Terrain cutout silhouette check: MANUAL (transparent texels must remain absent)");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 32 || image.getHeight() < 32) {
            throw new IllegalArgumentException("Screenshot is too small: "
                    + image.getWidth() + "x" + image.getHeight());
        }

        int x0 = image.getWidth() / 10;
        int x1 = image.getWidth() * 9 / 10;
        int y0 = image.getHeight() / 10;
        int y1 = image.getHeight() * 9 / 10;
        int stepX = Math.max(1, (x1 - x0) / 240);
        int stepY = Math.max(1, (y1 - y0) / 160);
        Map<Swatch, Integer> counts = new EnumMap<>(Swatch.class);
        int samples = 0;

        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < x1; x += stepX) {
                counts.merge(classify(image.getRGB(x, y)), 1, Integer::sum);
                samples++;
            }
        }

        int minimum = Math.max(24, samples / 800);
        return new Counts(counts.getOrDefault(Swatch.RED, 0),
                counts.getOrDefault(Swatch.GREEN, 0),
                counts.getOrDefault(Swatch.BLUE, 0), minimum);
    }

    private static Swatch classify(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        if (red >= 180 && green <= 100 && blue <= 100) return Swatch.RED;
        if (green >= 180 && red <= 100 && blue <= 100) return Swatch.GREEN;
        if (blue >= 150 && red <= 110 && green <= 110) return Swatch.BLUE;
        return Swatch.OTHER;
    }

    private static void selfTest() {
        BufferedImage image = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 160, 300, 0xFFFF0000);
        fill(image, 160, 0, 320, 300, 0xFF00FF00);
        fill(image, 320, 0, 480, 300, 0xFF0000FF);
        Counts counts = count(image);
        if (!counts.passes()) {
            throw new AssertionError("Verifier self-test failed: " + counts);
        }
        System.out.println("Terrain screenshot verifier self-test: PASS " + counts);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
        }
    }

    private record Counts(int red, int green, int blue, int minimum) {
        boolean passes() {
            return red >= minimum && green >= minimum && blue >= minimum;
        }
    }
}
