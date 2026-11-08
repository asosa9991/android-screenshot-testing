// Triage helper: where do two screenshots differ? Used by flake-check.sh.
// java tooling/screenshots/PngDiff.java golden.png actual.png
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class PngDiff {
    public static void main(String[] a) throws Exception {
        BufferedImage x = ImageIO.read(new File(a[0])), y = ImageIO.read(new File(a[1]));
        int w = x.getWidth(), h = x.getHeight(), n = 0, minX = w, minY = h, maxX = -1, maxY = -1, maxDelta = 0;
        TreeMap<Integer, Integer> bands = new TreeMap<>();
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int p = x.getRGB(i, j), q = y.getRGB(i, j);
            if (p != q) {
                n++; minX = Math.min(minX, i); maxX = Math.max(maxX, i); minY = Math.min(minY, j); maxY = Math.max(maxY, j);
                bands.merge(j / 100 * 100, 1, Integer::sum);
                for (int s = 0; s < 24; s += 8) maxDelta = Math.max(maxDelta, Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
            }
        }
        System.out.printf("%s: %d px differ, box x=%d..%d y=%d..%d, max channel delta=%d, rows(by 100px)=%s%n",
            new File(a[0]).getName(), n, minX, maxX, minY, maxY, maxDelta, bands);
    }
}
