import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the PHASE 15 Compute / Storage checkpoint. */
public final class VerifyComputeStorageScreenshot {
    private VerifyComputeStorageScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 1) {
            System.err.println("Usage: java tests/VerifyComputeStorageScreenshot.java <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[0]);
        Counts c = count(image);
        System.out.println("compute/storage screenshot swatches: GREEN=" + c.green + " MAGENTA=" + c.magenta + " OTHER=" + c.other);
        if (!pass(c)) throw new AssertionError("PHASE 15 Compute / Storage screenshot contract failed");
        System.out.println("PHASE 15 Compute / Storage screenshot check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0=image.getWidth()/20, x1=image.getWidth()*19/20, y0=image.getHeight()/20, y1=image.getHeight()*19/20;
        int step=Math.max(1, Math.max(image.getWidth(), image.getHeight())/1600);
        long green=0, magenta=0, other=0;
        for (int y=y0; y<y1; y+=step) for (int x=x0; x<x1; x+=step) {
            int p=image.getRGB(x,y), r=(p>>>16)&255, g=(p>>>8)&255, b=p&255;
            if (g>=160 && r<=80 && b<=80) green++;
            else if (r>=160 && b>=160 && g<=100) magenta++;
            else other++;
        }
        return new Counts(green,magenta,other);
    }

    private static boolean pass(Counts c) {
        long total=c.total();
        return total>0 && c.green>=1024 && c.green*100>=total*90 && c.magenta*100<=total;
    }

    private static void selfTest() {
        if (!pass(count(solid(0xFF00FF00)))) throw new AssertionError("GREEN success image rejected");
        if (pass(count(solid(0xFFFF00FF)))) throw new AssertionError("MAGENTA failure image accepted");
        if (pass(count(solid(0xFF000000)))) throw new AssertionError("BLACK image accepted");
        System.out.println("Compute/storage screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) image.setRGB(x,y,argb);
        return image;
    }

    private record Counts(long green,long magenta,long other) {
        long total() { return green+magenta+other; }
    }
}
