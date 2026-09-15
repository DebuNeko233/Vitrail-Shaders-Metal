import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the batched PHASE 11 Composite flip and history checkpoints. */
public final class VerifyCompositeScreenshot {
    private VerifyCompositeScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 2 || !("--flip".equals(args[0]) || "--history".equals(args[0]))) {
            System.err.println("Usage: java tests/VerifyCompositeScreenshot.java <--flip|--history> <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);
        Counts c = count(image);
        System.out.println("composite screenshot swatches: BLUE=" + c.blue + " RED=" + c.red
                + " GREEN=" + c.green + " CYAN=" + c.cyan + " MAGENTA=" + c.magenta
                + " OTHER=" + c.other + " mode=" + args[0]);
        boolean pass = "--flip".equals(args[0]) ? flip(c) : history(c);
        if (!pass) throw new AssertionError("Composite " + args[0].substring(2) + " screenshot contract failed");
        System.out.println("PHASE 11 composite " + args[0].substring(2) + " screenshot check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0=image.getWidth()/20, x1=image.getWidth()*19/20, y0=image.getHeight()/20, y1=image.getHeight()*19/20;
        int step=Math.max(1, Math.max(image.getWidth(), image.getHeight())/1600);
        long blue=0, red=0, green=0, cyan=0, magenta=0, other=0;
        for (int y=y0; y<y1; y+=step) for (int x=x0; x<x1; x+=step) {
            int p=image.getRGB(x,y), r=(p>>>16)&255, g=(p>>>8)&255, b=p&255;
            if (b>=160 && r<=80 && g<=80) blue++;
            else if (r>=160 && g<=80 && b<=80) red++;
            else if (g>=160 && r<=80 && b<=80) green++;
            else if (g>=160 && b>=160 && r<=80) cyan++;
            else if (r>=160 && b>=160 && g<=100) magenta++;
            else other++;
        }
        return new Counts(blue,red,green,cyan,magenta,other);
    }

    private static boolean flip(Counts c) {
        long total=c.total();
        return total>0 && c.blue>=1024 && c.blue*100>=total*90
                && c.red*100<=total && c.green*100<=total && c.magenta*100<=total;
    }

    private static boolean history(Counts c) {
        long total=c.total();
        return total>0 && c.cyan>=1024 && c.cyan*100>=total*90
                && c.red*100<=total && c.magenta*100<=total;
    }

    private static void selfTest() {
        BufferedImage blue=solid(0xFF0000FF), cyan=solid(0xFF00FFFF);
        BufferedImage red=solid(0xFFFF0000), magenta=solid(0xFFFF00FF);
        if (!flip(count(blue)) || history(count(blue))) throw new AssertionError("Flip self-test classification failed");
        if (!history(count(cyan)) || flip(count(cyan))) throw new AssertionError("History self-test classification failed");
        if (flip(count(red)) || history(count(red)) || flip(count(magenta)) || history(count(magenta)))
            throw new AssertionError("Failure image passed");
        System.out.println("Composite screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) image.setRGB(x,y,argb);
        return image;
    }

    private record Counts(long blue,long red,long green,long cyan,long magenta,long other) {
        long total() { return blue+red+green+cyan+magenta+other; }
    }
}
