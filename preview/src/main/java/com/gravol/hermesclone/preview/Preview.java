package com.gravol.hermesclone.preview;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Headless PNG renderer of the Phase-1 session home, mirroring the Android app's
 * HomeScreen.kt (same palette from Theme.kt, same tree layout) against the official
 * tour screenshot 01-launch.png. Used only as the `--screenshot` target for Craft
 * Anything's app-screenshot check; the real app is the Android APK.
 */
public final class Preview {

    // dp -> px on the 1080x2400 (420 dpi) Pixel-class canvas, same as the tour shots.
    static final float S = 2.625f;
    static final int W = 1080, H = 2400;

    // Palette — identical to app Theme.kt (sampled from shots-tour-2026-11-22).
    static final Color BG        = new Color(0x000022);
    static final Color SURFACE   = new Color(0x0C1A3C);
    static final Color HIGHLIGHT = new Color(0x1D2761);
    static final Color PURPLE    = new Color(0x8B5CF6);
    static final Color GREEN     = new Color(0x66DD4B);
    static final Color TEAL      = new Color(0x4BDDC7);
    static final Color LIVE_BLUE = new Color(0x4D8DFF);
    static final Color TEXT      = new Color(0xE8EAF6);
    static final Color MUTED     = new Color(0x9FA8DA);
    static final Color BORDER    = new Color(0x2A3A6A);

    record Session(String title, String time, boolean working) {}
    record Group(String name, Color avatar, String time, List<Session> sessions, int total) {}

    // Sample data matching the tour shot (the clone's own mock gateway uses the same).
    static final List<Group> GROUPS = List.of(
        new Group("Hermes", PURPLE, "5w", List.of(
            new Session("Check craft anything nous app progress", null, true),
            new Session("Fix nuvio AppImage launch crash", "5m", false),
            new Session("Save config and switch display orientation", "12h", false),
            new Session("Check craft anything nous app clone progress", "15h", false),
            new Session("Run Hy4-Flash-Strata model", "18h", false)), 932),
        new Group("link-curator", TEAL, "5w", List.of(), 101),
        new Group("clerk", GREEN, null, List.of(), 9),
        new Group("job-logger", PURPLE, null, List.of(), 13));

    public static void main(String[] args) throws Exception {
        String out = null;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--screenshot")) out = args[i + 1];
        }
        BufferedImage img = render();
        if (out != null) {
            File f = new File(out);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f.getAbsolutePath());
            return;
        }
        // Started without the flag: open as usual when a display exists.
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("headless: pass --screenshot <file.png>");
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("Hermes Clone");
            javax.swing.JLabel label = new javax.swing.JLabel(new javax.swing.ImageIcon(img));
            frame.add(label);
            frame.pack();
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    static BufferedImage render() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG);
        g.fillRect(0, 0, W, H);

        drawStatusBar(g);
        drawTopBar(g);
        drawTree(g);
        drawFab(g);
        g.dispose();
        return img;
    }

    // ---- pieces ----------------------------------------------------------------

    static void drawStatusBar(Graphics2D g) {
        int h = px(24);
        g.setColor(TEXT);
        g.setFont(font(Font.BOLD, 12));
        g.drawString("13:08", px(16), h - px(8));
        // battery (green, charging) + a couple of signal bars, right aligned
        int bx = W - px(16) - px(22);
        g.setColor(GREEN);
        g.fillRoundRect(bx, (h - px(10)) / 2, px(20), px(10), px(3), px(3));
        g.setColor(MUTED);
        int x = bx - px(10);
        for (int i = 0; i < 4; i++) {
            int bh = px(4 + i * 2);
            g.fillRect(x, h - px(8) - bh + px(3), px(3), bh);
            x += px(5);
        }
    }

    static void drawTopBar(Graphics2D g) {
        int cy = px(24 + 32);
        // gear (left) — gateway/settings entry (official: dark circle, light outline)
        iconCircle(g, px(16), cy, SURFACE);
        gear(g, px(16) + px(24), cy, px(22), TEXT);
        // filter + search (right)
        iconCircle(g, W - px(16) - px(48) * 2 - px(12), cy, SURFACE);
        filter(g, W - px(16) - px(48) - px(12) - px(24) + px(24), cy, px(22), TEXT);
        iconCircle(g, W - px(16) - px(48), cy, SURFACE);
        search(g, W - px(16) - px(24), cy, px(22), TEXT);
    }

    static void iconCircle(Graphics2D g, int x, int cy, Color fill) {
        int d = px(48);
        int y = cy - d / 2;
        g.setColor(fill);
        g.fill(new Ellipse2D.Float(x, y, d, d));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(px(1.4f)));
        g.draw(new Ellipse2D.Float(x, y, d, d));
    }

    static void gear(Graphics2D g, int cx, int cy, int size, Color c) {
        g.setColor(c);
        float r = size / 2f, ri = r * 0.62f;
        g.setStroke(new BasicStroke(px(2.4f)));
        g.draw(new Ellipse2D.Float(cx - ri, cy - ri, ri * 2, ri * 2));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int x1 = (int) (cx + Math.cos(a) * (ri + px(1))), y1 = (int) (cy + Math.sin(a) * (ri + px(1)));
            int x2 = (int) (cx + Math.cos(a) * r), y2 = (int) (cy + Math.sin(a) * r);
            g.drawLine(x1, y1, x2, y2);
        }
    }

    static void filter(Graphics2D g, int cx, int cy, int size, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(px(2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Material FilterList: left-aligned lines of decreasing width
        float w = size / 2f, x0 = cx - w;
        g.drawLine((int) x0, cy - (int) (w * 0.7f), (int) (x0 + 2 * w), cy - (int) (w * 0.7f));
        g.drawLine((int) x0, cy, (int) (x0 + w * 1.3f), cy);
        g.drawLine((int) x0, cy + (int) (w * 0.7f), (int) (x0 + w * 0.6f), cy + (int) (w * 0.7f));
    }

    static void search(Graphics2D g, int cx, int cy, int size, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(px(2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float r = size / 2f;
        // lens centered slightly up-left of (cx,cy), handle to the lower-right
        float hx = cx - 0.4f * r, hy = cy - 0.4f * r, hr = 0.6f * r;
        g.draw(new Ellipse2D.Float(hx - hr, hy - hr, hr * 2, hr * 2));
        g.drawLine((int) (hx + hr * 0.7f), (int) (hy + hr * 0.7f), (int) (cx + r), (int) (cy + r));
    }

    static void drawTree(Graphics2D g) {
        float y = px(24 + 64 + 12);
        for (Group grp : GROUPS) {
            y = groupHeader(g, grp, y);
            if (!grp.sessions().isEmpty()) {
                for (Session s : grp.sessions()) y = sessionRow(g, grp, s, y);
            }
            if (grp.total() > 0) y = moreRow(g, grp, y);
            y += px(10);
        }
    }

    static float groupHeader(Graphics2D g, Group grp, float y) {
        float ax = px(16), as = px(40);
        // avatar: rounded square with two "eyes" (neutral placeholder art), or a
        // droplet for the teal one — matching the clone's AgentAvatar placeholders.
        if (grp.avatar() == TEAL) {
            droplet(g, ax, y, as, grp.avatar());
        } else {
            g.setPaint(new LinearGradientPaint(
                new java.awt.geom.Point2D.Float(ax, y), new java.awt.geom.Point2D.Float(ax, y + as),
                new float[]{0f, 1f},
                new Color[]{grp.avatar(), new Color(grp.avatar().getRed(), grp.avatar().getGreen(),
                        grp.avatar().getBlue(), 140)}));
            g.fill(new RoundRectangle2D.Float(ax, y, as, as, px(12), px(12)));
            eyes(g, ax + as / 2, y + as * 0.38f, as, Color.WHITE);
        }
        float tx = ax + as + px(14);
        g.setColor(TEXT);
        g.setFont(font(Font.BOLD, 16));
        g.drawString(grp.name(), tx, y + as / 2 + px(6));
        if (grp.time() != null) {
            g.setColor(MUTED);
            g.setFont(font(Font.PLAIN, 13));
            g.drawString(grp.time(), W - px(16) - g.getFontMetrics().stringWidth(grp.time()),
                y + as / 2 + px(5));
        }
        return y + as + px(12);
    }

    static void eyes(Graphics2D g, float cx, float cy, float size, Color c) {
        g.setColor(c);
        float r = size * 0.09f, gap = size * 0.22f;
        g.fill(new Ellipse2D.Float(cx - gap - r, cy - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Float(cx + gap - r, cy - r, r * 2, r * 2));
    }

    static void droplet(Graphics2D g, float x, float y, float size, Color c) {
        Path2D.Float p = new Path2D.Float();
        float cx = x + size / 2;
        p.moveTo(cx, y + size * 0.06f);
        p.curveTo(x + size * 1.05f, y + size * 0.65f, x + size * 0.85f, y + size, cx, y + size);
        p.curveTo(x + size * 0.15f, y + size, x - size * 0.05f, y + size * 0.65f, cx, y + size * 0.06f);
        p.closePath();
        g.setPaint(new LinearGradientPaint(
            new java.awt.geom.Point2D.Float(x, y), new java.awt.geom.Point2D.Float(x, y + size),
            new float[]{0f, 1f}, new Color[]{c, new Color(0x2AB8A6)}));
        g.fill(p);
        g.setColor(new Color(255, 255, 255, 200));
        float r = size * 0.07f;
        g.fill(new Ellipse2D.Float(cx - r * 2.2f, y + size * 0.42f, r * 2, r * 2));
    }

    static float sessionRow(Graphics2D g, Group grp, Session s, float y) {
        float rh = px(48);
        // tree connector line
        g.setColor(HIGHLIGHT);
        g.fill(new java.awt.geom.Rectangle2D.Float(px(38), y, px(2), rh));
        // live status dot
        g.setColor(s.working() ? LIVE_BLUE : new Color(LIVE_BLUE.getRed(), LIVE_BLUE.getGreen(),
                LIVE_BLUE.getBlue(), 115));
        float d = px(8);
        g.fill(new Ellipse2D.Float(px(52), y + rh / 2 - d / 2, d, d));
        // title
        g.setColor(TEXT);
        g.setFont(font(Font.PLAIN, 15));
        String t = s.title();
        float maxW = W - px(16) - px(72) - px(150);
        while (g.getFontMetrics().stringWidth(t) > maxW && t.length() > 4) {
            t = t.substring(0, t.length() - 2);
        }
        if (!t.equals(s.title())) t = t + "…";
        g.drawString(t, px(72), y + rh / 2 + px(5));
        // right side: Working + spinner, or timestamp
        if (s.working()) {
            g.setColor(LIVE_BLUE);
            g.setFont(font(Font.BOLD, 13));
            String w = "Working";
            int tw = g.getFontMetrics().stringWidth(w);
            int rx = W - px(16) - tw;
            g.drawString(w, rx, y + rh / 2 + px(5));
            g.setStroke(new BasicStroke(px(2.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float sd = px(14);
            g.draw(new Arc2D.Float(rx - sd - px(8), y + rh / 2 - sd / 2, sd, sd,
                90, -270, Arc2D.OPEN));
        } else if (s.time() != null) {
            g.setColor(MUTED);
            g.setFont(font(Font.PLAIN, 13));
            g.drawString(s.time(), W - px(16) - g.getFontMetrics().stringWidth(s.time()),
                y + rh / 2 + px(5));
        }
        return y + rh;
    }

    static float moreRow(Graphics2D g, Group grp, float y) {
        float rh = px(44);
        g.setColor(MUTED);
        g.setFont(font(Font.PLAIN, 14));
        String label = grp.total() + " more sessions";
        g.drawString(label, px(72), y + rh / 2 + px(5));
        // chevron ">"
        g.setStroke(new BasicStroke(px(2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float cx = W - px(24), cy = y + rh / 2, a = px(5);
        g.drawLine((int) (cx - a), (int) (cy - a), (int) (cx + a * 0.4), (int) cy);
        g.drawLine((int) (cx + a * 0.4), (int) cy, (int) (cx - a), (int) (cy + a));
        return y + rh;
    }

    static void drawFab(Graphics2D g) {
        float d = px(56);
        float x = W - d - px(16), y = H - d - px(28);
        g.setColor(SURFACE);
        g.fill(new Ellipse2D.Float(x, y, d, d));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(px(1.4f)));
        g.draw(new Ellipse2D.Float(x, y, d, d));
        // pencil (compose): body stroke, tip triangle, eraser tick
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(px(3.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float cx = x + d / 2, cy = y + d / 2, r = px(11);
        float bx = cx - r, by = cy + r;                 // tip (lower-left)
        float ex = cx + r * 0.7f, ey = cy - r * 0.7f;   // eraser end (upper-right)
        g.drawLine((int) (bx + px(3)), (int) (by - px(3)), (int) ex, (int) ey);
        Path2D.Float tip = new Path2D.Float();
        tip.moveTo(bx, by);
        tip.lineTo(bx + px(7), by - px(2));
        tip.lineTo(bx + px(2), by - px(7));
        tip.closePath();
        g.fill(tip);
        g.drawLine((int) (ex - px(4)), (int) (ey - px(4)), (int) (ex + px(2)), (int) (ey + px(2)));
    }

    // ---- helpers ----------------------------------------------------------------

    static int px(float dp) { return Math.round(dp * S); }

    static Font font(int style, float sp) {
        return Font.decode("SansSerif-" + (style == Font.BOLD ? "bold" : "plain") + "-" + Math.round(sp * S));
    }
}
