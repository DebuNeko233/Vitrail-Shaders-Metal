import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the batched PHASE 10 Deferred MRT and mipmap checkpoints. */
public final class VerifyDeferredTailScreenshot {
    private VerifyDeferredTailScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 2 || !("--mrt".equals(args[0]) || "--mipmap".equals(args[0]))) {
            System.err.println("Usage: java tests/VerifyDeferredTailScreenshot.java <--mrt|--mipmap> <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);
        Counts c = count(image);
        System.out.println("deferred tail screenshot swatches: BLUE=" + c.blue + " RED=" + c.red
                + " CYAN=" + c.cyan + " MAGENTA=" + c.magenta + " OTHER=" + c.other + " mode=" + args[0]);
        boolean pass = "--mrt".equals(args[0]) ? mrt(c) : mipmap(c);
        if (!pass) throw new AssertionError("Deferred " + args[0].substring(2) + " screenshot contract failed");
        System.out.println("PHASE 10 deferred " + args[0].substring(2) + " screenshot check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0=image.getWidth()/20, x1=image.getWidth()*19/20, y0=image.getHeight()/20, y1=image.getHeight()*19/20;
        int step=Math.max(1, Math.max(image.getWidth(), image.getHeight())/1600);
        long blue=0, red=0, cyan=0, magenta=0, other=0;
        for (int y=y0; y<y1; y+=step) for (int x=x0; x<x1; x+=step) {
            int p=image.getRGB(x,y), r=(p>>>16)&255, g=(p>>>8)&255, b=p&255;
            if (b>=160 && r<=80 && g<=80) blue++;
            else if (r>=160 && g<=80 && b<=80) red++;
            else if (g>=160 && b>=160 && r<=80) cyan++;
            else if (r>=160 && b>=160 && g<=100) magenta++;
            else other++;
        }
        return new Counts(blue,red,cyan,magenta,other);
    }

    private static boolean mrt(Counts c) {
        long total=c.total();
        return total>0 && c.blue*4>=total && c.red*4>=total
                && (c.blue+c.red)*100>=total*90 && c.magenta*100<=total;
    }

    private static boolean mipmap(Counts c) {
        long total=c.total();
        return total>0 && c.cyan>=1024 && c.cyan*100>=total*90 && c.magenta*100<=total;
    }

    private static void selfTest() {
        BufferedImage mrt=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) mrt.setRGB(x,y,x<240?0xFF0000FF:0xFFFF0000);
        BufferedImage mip=solid(0xFF00FFFF);
        BufferedImage bad=solid(0xFFFF00FF);
        if (!mrt(count(mrt)) || mipmap(count(mrt))) throw new AssertionError("MRT self-test classification failed");
        if (!mipmap(count(mip)) || mrt(count(mip))) throw new AssertionError("Mipmap self-test classification failed");
        if (mrt(count(bad)) || mipmap(count(bad))) throw new AssertionError("MAGENTA failure image passed");
        System.out.println("Deferred tail screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) image.setRGB(x,y,argb);
        return image;
    }

    private record Counts(long blue,long red,long cyan,long magenta,long other) {
        long total() { return blue+red+cyan+magenta+other; }
    }
}
