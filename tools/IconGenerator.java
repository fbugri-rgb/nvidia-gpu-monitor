import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tekent het app-icoon en schrijft alle formaten weg:
 * PNG's voor de vensters (resources/), en .ico/.png/.icns voor jpackage (packaging/).
 * <p>
 * Uitvoeren vanuit de projectmap: {@code java tools/IconGenerator.java}
 */
public class IconGenerator {
    private static final Color BACKGROUND_TOP = new Color(0x2B2D31);
    private static final Color BACKGROUND_BOTTOM = new Color(0x18191C);
    private static final Color GRID = new Color(0x3A3C42);
    private static final Color GREEN = new Color(0x76B900);

    // De grafieklijn, als fracties van het tekenvlak (0 = onder, 1 = boven)
    private static final double[] GRAPH = {0.30, 0.42, 0.36, 0.58, 0.50, 0.78, 0.66, 0.88};

    public static void main(String[] args) throws IOException {
        Path resources = Path.of("resources", "nvidiamonitor", "icons");
        Path packaging = Path.of("packaging");
        Files.createDirectories(resources);
        Files.createDirectories(packaging);

        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            ImageIO.write(draw(size), "png", resources.resolve("icon-" + size + ".png").toFile());
        }
        ImageIO.write(draw(512), "png", packaging.resolve("NvidiaGpuMonitor.png").toFile());
        writeIco(packaging.resolve("NvidiaGpuMonitor.ico"), 16, 24, 32, 48, 64, 128, 256);
        writeIcns(packaging.resolve("NvidiaGpuMonitor.icns"));
        System.out.println("Icons written to " + resources + " and " + packaging);
    }

    static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 100.0, size / 100.0); // alles hieronder op een vlak van 100 x 100
        boolean tiny = size <= 24;

        // achtergrond: afgeronde tegel
        RoundRectangle2D tile = new RoundRectangle2D.Double(2, 2, 96, 96, 22, 22);
        g.setPaint(new GradientPaint(0, 2, BACKGROUND_TOP, 0, 98, BACKGROUND_BOTTOM));
        g.fill(tile);

        // rasterlijnen (te druk op kleine formaten)
        if (!tiny) {
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.5f));
            for (int y = 30; y <= 70; y += 20) {
                g.drawLine(14, y, 86, y);
            }
        }

        // grafiek met gevulde oppervlakte eronder
        double left = 12, right = 88, bottom = 84, top = 16;
        Path2D line = new Path2D.Double();
        for (int i = 0; i < GRAPH.length; i++) {
            double x = left + (right - left) * i / (GRAPH.length - 1);
            double y = bottom - (bottom - top) * GRAPH[i];
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        Path2D area = new Path2D.Double(line);
        area.lineTo(right, bottom);
        area.lineTo(left, bottom);
        area.closePath();
        g.setPaint(new GradientPaint(0, (float) top, withAlpha(GREEN, 150), 0, (float) bottom, withAlpha(GREEN, 40)));
        g.fill(area);
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(tiny ? 9f : 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        // groene rand
        g.setColor(withAlpha(GREEN, 170));
        g.setStroke(new BasicStroke(tiny ? 5f : 3f));
        g.draw(new RoundRectangle2D.Double(3.5, 3.5, 93, 93, 20, 20));

        g.dispose();
        return image;
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(draw(size), "png", out);
        return out.toByteArray();
    }

    /** Windows .ico met PNG-afbeeldingen erin (ondersteund sinds Windows Vista). */
    private static void writeIco(Path file, int... sizes) throws IOException {
        byte[][] images = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            images[i] = png(sizes[i]);
        }
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = header.capacity();
        for (int i = 0; i < sizes.length; i++) {
            header.put((byte) (sizes[i] % 256)) // 256 wordt als 0 opgeslagen
                    .put((byte) (sizes[i] % 256))
                    .put((byte) 0).put((byte) 0)
                    .putShort((short) 1).putShort((short) 32)
                    .putInt(images[i].length).putInt(offset);
            offset += images[i].length;
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(header.array());
            for (byte[] image : images) {
                out.write(image);
            }
        }
    }

    /** macOS .icns met PNG-afbeeldingen erin. */
    private static void writeIcns(Path file) throws IOException {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("ic07", 128);
        types.put("ic08", 256);
        types.put("ic09", 512);
        types.put("ic10", 1024);
        types.put("ic11", 32);  // 16@2x
        types.put("ic12", 64);  // 32@2x
        types.put("ic13", 256); // 128@2x
        types.put("ic14", 512); // 256@2x

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (Map.Entry<String, Integer> type : types.entrySet()) {
            byte[] image = png(type.getValue());
            data.writeBytes(type.getKey());
            data.writeInt(8 + image.length);
            data.write(image);
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeBytes("icns");
            out.writeInt(8 + body.size());
            body.writeTo(out);
        }
    }
}
