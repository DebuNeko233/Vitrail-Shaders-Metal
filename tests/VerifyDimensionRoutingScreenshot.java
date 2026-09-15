import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pixel verifier for the batched PHASE 13 Dimension Routing checkpoints. */
public final class VerifyDimensionRoutingScreenshot {
    private VerifyDimensionRoutingScreenshot() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        if (args.length != 2 || !expected().containsKey(args[0])) {
            System.err.println("Usage: java tests/VerifyDimensionRoutingScreenshot.java "
                    + "<--convention-overworld|--convention-nether|--convention-end"
                    + "|--properties-overworld|--properties-nether|--properties-fallback> "
                    + "<minecraft-screenshot.png>");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[1]));
        if (image == null) throw new IllegalArgumentException("Not a readable image: " + args[1]);
        Counts c = count(image);
        System.out.println("dimension screenshot swatches: GREEN=" + c.green + " RED=" + c.red
                + " BLUE=" + c.blue + " CYAN=" + c.cyan + " YELLOW=" + c.yellow
                + " WHITE=" + c.white + " MAGENTA=" + c.magenta + " OTHER=" + c.other
                + " mode=" + args[0]);
        if (!pass(args[0], c)) throw new AssertionError("Dimension routing screenshot contract failed for " + args[0]);
        System.out.println("PHASE 13 dimension " + args[0].substring(2) + " screenshot check: PASS");
    }

    private static Map<String, String> expected() {
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("--convention-overworld", "GREEN");
        modes.put("--convention-nether", "RED");
        modes.put("--convention-end", "BLUE");
        modes.put("--properties-overworld", "CYAN");
        modes.put("--properties-nether", "YELLOW");
        modes.put("--properties-fallback", "WHITE");
        return modes;
    }

    private static Counts count(BufferedImage image) {
        if (image.getWidth() < 64 || image.getHeight() < 64) throw new IllegalArgumentException("Screenshot is too small");
        int x0=image.getWidth()/20, x1=image.getWidth()*19/20, y0=image.getHeight()/20, y1=image.getHeight()*19/20;
        int step=Math.max(1, Math.max(image.getWidth(), image.getHeight())/1600);
        long green=0, red=0, blue=0, cyan=0, yellow=0, white=0, magenta=0, other=0;
        for (int y=y0; y<y1; y+=step) for (int x=x0; x<x1; x+=step) {
            int p=image.getRGB(x,y), r=(p>>>16)&255, g=(p>>>8)&255, b=p&255;
            if (g>=160 && r<=80 && b<=80) green++;
            else if (r>=160 && g<=80 && b<=80) red++;
            else if (b>=160 && r<=80 && g<=80) blue++;
            else if (g>=160 && b>=160 && r<=80) cyan++;
            else if (r>=160 && g>=160 && b<=80) yellow++;
            else if (r>=180 && g>=180 && b>=180) white++;
            else if (r>=160 && b>=160 && g<=100) magenta++;
            else other++;
        }
        return new Counts(green,red,blue,cyan,yellow,white,magenta,other);
    }

    private static boolean pass(String mode, Counts c) {
        long total=c.total(), wanted=switch (expected().get(mode)) {
            case "GREEN" -> c.green;
            case "RED" -> c.red;
            case "BLUE" -> c.blue;
            case "CYAN" -> c.cyan;
            case "YELLOW" -> c.yellow;
            case "WHITE" -> c.white;
            default -> 0;
        };
        return total>0 && wanted>=1024 && wanted*100>=total*90 && c.magenta*100<=total;
    }

    private static void selfTest() {
        Map<String,Integer> solids=Map.of(
                "--convention-overworld",0xFF00FF00,
                "--convention-nether",0xFFFF0000,
                "--convention-end",0xFF0000FF,
                "--properties-overworld",0xFF00FFFF,
                "--properties-nether",0xFFFFFF00,
                "--properties-fallback",0xFFFFFFFF);
        for (Map.Entry<String,Integer> entry:solids.entrySet()) {
            Counts c=count(solid(entry.getValue()));
            if (!pass(entry.getKey(),c)) throw new AssertionError("Expected mode rejected: "+entry.getKey());
            for (String other:solids.keySet()) if (!other.equals(entry.getKey()) && pass(other,c))
                throw new AssertionError(entry.getKey()+" image also passed "+other);
        }
        Counts failure=count(solid(0xFFFF00FF));
        for (String mode:solids.keySet()) if (pass(mode,failure)) throw new AssertionError("MAGENTA failure image passed "+mode);
        System.out.println("Dimension routing screenshot verifier self-test: PASS");
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image=new BufferedImage(480,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for (int x=0;x<480;x++) image.setRGB(x,y,argb);
        return image;
    }

    private record Counts(long green,long red,long blue,long cyan,long yellow,long white,long magenta,long other) {
        long total() { return green+red+blue+cyan+yellow+white+magenta+other; }
    }
}
