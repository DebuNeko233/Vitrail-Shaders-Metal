import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Pixel verifier for the batched PHASE 12 Final checkpoints. */
public final class VerifyFinalScreenshot {
    private VerifyFinalScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 2 || !("--direct".equals(args[0]) || "--chain".equals(args[0]))) {
            System.err.println("Usage: java tests/VerifyFinalScreenshot.java <--direct|--chain> <minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);
        Counts c = count(image);
        System.out.println("final screenshot swatches: GREEN=" + c.green + " BLUE=" + c.blue
                + " YELLOW=" + c.yellow + " MAGENTA=" + c.magenta + " OTHER=" + c.other
                + " mode=" + args[0]);
        boolean pass = "--direct".equals(args[0]) ? direct(c) : chain(c);
        if (!pass) throw new AssertionError("Final " + args[0].substring(2) + " screenshot contract failed");
        System.out.println("PHASE 12 final " + args[0].substring(2) + " screenshot check: PASS");
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0=image.getWidth()/20, x1=image.getWidth()*19/20, y0=image.getHeight()/20, y1=image.getHeight()*19/20;
        int step=Math.max(1, Math.max(image.getWidth(), image.getHeight())/1600);
        long green=0, blue=0, yellow=0, magenta=0, other=0;
        long leftBlue=0, leftTotal=0, rightYellow=0, rightTotal=0;
        int leftEnd=image.getWidth()*47/100, rightStart=image.getWidth()*53/100;
        for (int y=y0; y<y1; y+=step) for (int x=x0; x<x1; x+=step) {
            int p=image.getRGB(x,y), r=(p>>>16)&255, g=(p>>>8)&255, b=p&255;
            boolean isGreen=g>=160 && r<=80 && b<=80;
            boolean isBlue=b>=160 && r<=80 && g<=80;
            boolean isYellow=r>=160 && g>=160 && b<=80;
            boolean isMagenta=r>=160 && b>=160 && g<=100;
            if (isGreen) green++;
            else if (isBlue) blue++;
            else if (isYellow) yellow++;
            else if (isMagenta) magenta++;
            else other++;
            if (x < leftEnd) { leftTotal++; if (isBlue) leftBlue++; }
            if (x >= rightStart) { rightTotal++; if (isYellow) rightYellow++; }
        }
        return new Counts(green,blue,yellow,magenta,other,leftBlue,leftTotal,rightYellow,rightTotal);
    }

    private static boolean direct(Counts c) {
        long total=c.total();
        return total>0 && c.green>=1024 && c.green*100>=total*90 && c.magenta*100<=total;
    }

    private static boolean chain(Counts c) {
        long total=c.total();
        return total>0 && c.leftTotal>0 && c.rightTotal>0
                && c.leftBlue*100>=c.leftTotal*90
                && c.rightYellow*100>=c.rightTotal*90
                && c.magenta*100<=total;
    }

    private static void selfTest() {
        BufferedImage green=solid(0xFF00FF00);
        BufferedImage split=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) split.setRGB(x,y,x<240?0xFF0000FF:0xFFFFFF00);
        BufferedImage magenta=solid(0xFFFF00FF);
        Counts g=count(green), s=count(split), m=count(magenta);
        if (!direct(g) || chain(g)) throw new AssertionError("Direct self-test classification failed");
        if (!chain(s) || direct(s)) throw new AssertionError("Chain self-test classification failed");
        if (direct(m) || chain(m)) throw new AssertionError("Failure image passed");
        System.out.println("Final screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) image.setRGB(x,y,argb);
        return image;
    }

    private record Counts(long green,long blue,long yellow,long magenta,long other,
                          long leftBlue,long leftTotal,long rightYellow,long rightTotal) {
        long total() { return green+blue+yellow+magenta+other; }
    }
}
