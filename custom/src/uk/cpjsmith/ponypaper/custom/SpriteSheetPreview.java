package uk.cpjsmith.ponypaper.custom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Spritesheet viewer used for plain preview and for the two-phase anchor picker
 * (select a frame, then place feet on a zoomed frame with an optional pixel grid).
 * In place mode, Ctrl+scroll (Meta+scroll on macOS) zooms toward the cursor.
 */
public class SpriteSheetPreview extends JComponent
        implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    /** Interaction mode for the component. */
    public enum Mode {
        /** Hover highlights a frame; no selection or placement (legacy Preview). */
        PREVIEW,
        /** Click selects a frame for anchor placement. */
        SELECT_FRAME,
        /** Click/drag places the feet hotspot within the selected frame. */
        PLACE_ANCHOR
    }

    /** Notified when the user changes selection, anchors, or mode-related state. */
    public interface Listener {
        void onStatusChanged(String status);

        void onFrameSelected(int frameIndex);

        void onAnchorChanged(float anchorX, float anchorY);
    }

    private final Image image;
    private final int frameCount;
    private final int imageWidth;
    private final int imageHeight;
    private final int frameWidth;
    private final int frameHeight;

    private Mode mode = Mode.PREVIEW;
    private int highlightIndex = -1;
    private int selectedFrame = -1;
    private float anchorX = Float.NaN;
    private float anchorY = Float.NaN;
    private boolean showGrid = true;
    private int placeZoom = 4;
    private Listener listener;

    /**
     * Preview-only constructor (hover highlight of frames). Same behaviour as the
     * original Image Preview dialog.
     */
    public SpriteSheetPreview(Image image, int frameCount) {
        this(image, frameCount, Mode.PREVIEW);
    }

    public SpriteSheetPreview(Image image, int frameCount, Mode mode) {
        if (image == null) {
            throw new IllegalArgumentException("image");
        }
        if (frameCount < 1) {
            throw new IllegalArgumentException("frameCount must be >= 1");
        }
        this.image = image;
        this.frameCount = frameCount;
        this.imageWidth = image.getWidth(null);
        this.imageHeight = image.getHeight(null);
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("image has no size");
        }
        this.frameWidth = Math.max(1, imageWidth / frameCount);
        this.frameHeight = imageHeight;
        this.mode = mode != null ? mode : Mode.PREVIEW;

        setFocusable(true);
        setPreferredSize(preferredSizeForMode());
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        updateStatus();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        updateStatus();
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        if (mode == null) {
            mode = Mode.PREVIEW;
        }
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        if (mode != Mode.PLACE_ANCHOR) {
            highlightIndex = -1;
        }
        setPreferredSize(preferredSizeForMode());
        revalidate();
        repaint();
        updateStatus();
    }

    public int getSelectedFrame() {
        return selectedFrame;
    }

    public void setSelectedFrame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= frameCount) {
            selectedFrame = -1;
        } else {
            selectedFrame = frameIndex;
        }
        repaint();
        updateStatus();
    }

    public float getAnchorX() {
        return anchorX;
    }

    public float getAnchorY() {
        return anchorY;
    }

    public void setAnchors(float anchorX, float anchorY) {
        this.anchorX = sanitizeAnchor(anchorX);
        this.anchorY = sanitizeAnchor(anchorY);
        repaint();
        updateStatus();
        fireAnchorChanged();
    }

    public void clearAnchors() {
        setAnchors(Float.NaN, Float.NaN);
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        repaint();
    }

    public int getPlaceZoom() {
        return placeZoom;
    }

    /**
     * Integer zoom used in {@link Mode#PLACE_ANCHOR} so source pixels map to clean blocks.
     * Range is 1–16.
     */
    public void setPlaceZoom(int placeZoom) {
        int z = Math.max(1, Math.min(16, placeZoom));
        if (this.placeZoom == z) {
            return;
        }
        this.placeZoom = z;
        if (mode == Mode.PLACE_ANCHOR) {
            setPreferredSize(preferredSizeForMode());
            revalidate();
        }
        repaint();
        updateStatus();
    }

    /**
     * Changes place-mode zoom while keeping the source pixel under {@code mouseInPreview}
     * fixed in the enclosing {@link JScrollPane} viewport (cursor-centered zoom).
     * No-op when not in {@link Mode#PLACE_ANCHOR} or when zoom is unchanged after clamping.
     *
     * @param mouseInPreview mouse location in this component's coordinates
     * @param newZoom        desired integer zoom (clamped to 1–16)
     */
    public void zoomToward(Point mouseInPreview, int newZoom) {
        if (mode != Mode.PLACE_ANCHOR || selectedFrame < 0 || mouseInPreview == null) {
            setPlaceZoom(newZoom);
            return;
        }
        int z = Math.max(1, Math.min(16, newZoom));
        if (z == placeZoom) {
            return;
        }

        Rectangle bounds = getImageBounds();
        float srcX;
        float srcY;
        if (bounds.width > 0 && bounds.height > 0 && bounds.contains(mouseInPreview)) {
            srcX = (mouseInPreview.x - bounds.x) * (float) frameWidth / bounds.width;
            srcY = (mouseInPreview.y - bounds.y) * (float) frameHeight / bounds.height;
        } else {
            // Outside the image: zoom around frame centre.
            srcX = frameWidth / 2f;
            srcY = frameHeight / 2f;
        }
        srcX = clamp(srcX, 0f, frameWidth);
        srcY = clamp(srcY, 0f, frameHeight);

        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        JViewport viewport = scroll != null ? scroll.getViewport() : null;
        Point mouseInViewport = viewport != null
                ? SwingUtilities.convertPoint(this, mouseInPreview, viewport)
                : null;

        setPlaceZoom(z);

        if (viewport == null || mouseInViewport == null) {
            return;
        }

        // Apply new preferred size immediately so bounds / view size match the zoom.
        Dimension pref = getPreferredSize();
        setSize(pref);
        viewport.setViewSize(pref);

        Rectangle newBounds = getImageBounds();
        int contentX = newBounds.x + Math.round(srcX * newBounds.width / (float) frameWidth);
        int contentY = newBounds.y + Math.round(srcY * newBounds.height / (float) frameHeight);

        int viewX = contentX - mouseInViewport.x;
        int viewY = contentY - mouseInViewport.y;

        Dimension extent = viewport.getExtentSize();
        int maxX = Math.max(0, pref.width - extent.width);
        int maxY = Math.max(0, pref.height - extent.height);
        viewX = Math.max(0, Math.min(maxX, viewX));
        viewY = Math.max(0, Math.min(maxY, viewY));

        viewport.setViewPosition(new Point(viewX, viewY));
    }

    /**
     * Chooses an integer zoom so the larger frame edge is roughly {@code targetPx} on screen.
     */
    public void setAutoPlaceZoom(int targetPx) {
        int longer = Math.max(frameWidth, frameHeight);
        if (longer <= 0) {
            setPlaceZoom(4);
            return;
        }
        int z = Math.round((float) targetPx / longer);
        setPlaceZoom(Math.max(2, Math.min(8, z)));
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    /**
     * Default feet position when anchors are unset: centre X, bottom Y.
     */
    public float getDefaultAnchorX() {
        return frameWidth / 2f;
    }

    public float getDefaultAnchorY() {
        return frameHeight;
    }

    private static float sanitizeAnchor(float value) {
        if (Float.isNaN(value) || value < 0f) {
            return Float.NaN;
        }
        return value;
    }

    private Dimension preferredSizeForMode() {
        if (mode == Mode.PLACE_ANCHOR && selectedFrame >= 0) {
            return new Dimension(frameWidth * placeZoom, frameHeight * placeZoom);
        }
        return new Dimension(imageWidth, imageHeight);
    }

    private Rectangle getImageBounds() {
        int drawW;
        int drawH;
        if (mode == Mode.PLACE_ANCHOR && selectedFrame >= 0) {
            drawW = frameWidth * placeZoom;
            drawH = frameHeight * placeZoom;
        } else {
            drawW = imageWidth;
            drawH = imageHeight;
        }

        int componentWidth = getWidth();
        int componentHeight = getHeight();

        Rectangle result = new Rectangle(0, 0, componentWidth, componentHeight);
        if (drawW < componentWidth) {
            result.x = (componentWidth - drawW) / 2;
            result.width = drawW;
        }
        if (drawH < componentHeight) {
            result.y = (componentHeight - drawH) / 2;
            result.height = drawH;
        }
        return result;
    }

    private void highlightFrame(MouseEvent e) {
        if (mode == Mode.PLACE_ANCHOR) {
            return;
        }
        Rectangle bounds = getImageBounds();
        if (bounds.contains(e.getPoint()) && bounds.width > 0) {
            highlightIndex = (e.getX() - bounds.x) * frameCount / bounds.width;
            if (highlightIndex < 0) {
                highlightIndex = 0;
            }
            if (highlightIndex >= frameCount) {
                highlightIndex = frameCount - 1;
            }
        } else {
            highlightIndex = -1;
        }
        repaint();
        updateStatus();
    }

    private void highlightNone() {
        if (mode == Mode.PLACE_ANCHOR) {
            return;
        }
        highlightIndex = -1;
        repaint();
        updateStatus();
    }

    /**
     * Maps a component mouse point to frame-local pixel coordinates while placing.
     * Returns null if outside the image.
     */
    private Point.Float frameLocalFromMouse(MouseEvent e) {
        if (mode != Mode.PLACE_ANCHOR || selectedFrame < 0) {
            return null;
        }
        Rectangle bounds = getImageBounds();
        if (!bounds.contains(e.getPoint()) || bounds.width <= 0 || bounds.height <= 0) {
            return null;
        }
        float fx = (e.getX() - bounds.x) * (float) frameWidth / bounds.width;
        float fy = (e.getY() - bounds.y) * (float) frameHeight / bounds.height;
        fx = clamp(fx, 0f, frameWidth);
        fy = clamp(fy, 0f, frameHeight);
        return new Point.Float(fx, fy);
    }

    /**
     * Maps a sheet-view mouse point to a frame index, or -1 if outside.
     */
    private int frameIndexFromMouse(MouseEvent e) {
        Rectangle bounds = getImageBounds();
        if (!bounds.contains(e.getPoint()) || bounds.width <= 0) {
            return -1;
        }
        int index = (e.getX() - bounds.x) * frameCount / bounds.width;
        if (index < 0) {
            index = 0;
        }
        if (index >= frameCount) {
            index = frameCount - 1;
        }
        return index;
    }

    private void placeAnchorAt(MouseEvent e) {
        Point.Float local = frameLocalFromMouse(e);
        if (local == null) {
            return;
        }
        anchorX = local.x;
        anchorY = local.y;
        repaint();
        updateStatus();
        fireAnchorChanged();
    }

    private void nudgeAnchor(float dx, float dy) {
        float x = Float.isNaN(anchorX) ? getDefaultAnchorX() : anchorX;
        float y = Float.isNaN(anchorY) ? getDefaultAnchorY() : anchorY;
        setAnchors(clamp(x + dx, 0f, frameWidth), clamp(y + dy, 0f, frameHeight));
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private void fireAnchorChanged() {
        if (listener != null) {
            listener.onAnchorChanged(anchorX, anchorY);
        }
    }

    private void updateStatus() {
        if (listener == null) {
            return;
        }
        listener.onStatusChanged(buildStatusText());
    }

    String buildStatusText() {
        if (mode == Mode.PLACE_ANCHOR) {
            String coords;
            if (Float.isNaN(anchorX) || Float.isNaN(anchorY)) {
                coords = String.format(
                        "default %.1f, %.1f (centre / bottom)",
                        getDefaultAnchorX(),
                        getDefaultAnchorY());
            } else {
                coords = String.format("%.1f, %.1f", anchorX, anchorY);
            }
            return String.format(
                    "Frame %d / %d @ %dx — click or drag to set feet (%s). Arrows nudge 1px (Shift: 5). Ctrl+scroll zooms.",
                    selectedFrame + 1,
                    frameCount,
                    placeZoom,
                    coords);
        }
        int hover = highlightIndex;
        if (mode == Mode.SELECT_FRAME) {
            if (hover >= 0) {
                return String.format(
                        "Frame %d / %d — click to place anchors on this frame.",
                        hover + 1,
                        frameCount);
            }
            return String.format(
                    "Hover a frame, then click to place anchors (%d frames, %d×%d px each).",
                    frameCount,
                    frameWidth,
                    frameHeight);
        }
        if (hover >= 0) {
            return String.format("Frame %d / %d", hover + 1, frameCount);
        }
        return String.format("%d frames, %d×%d px each", frameCount, frameWidth, frameHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Rectangle bounds = getImageBounds();
            if (mode == Mode.PLACE_ANCHOR && selectedFrame >= 0) {
                paintPlaceMode(g2, bounds);
            } else {
                paintSheetMode(g2, bounds);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintSheetMode(Graphics2D g2, Rectangle bounds) {
        g2.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);

        int focus = highlightIndex;
        if (mode == Mode.SELECT_FRAME && selectedFrame >= 0 && focus < 0) {
            focus = selectedFrame;
        }
        if (focus >= 0 && frameCount > 0) {
            g2.setColor(new Color(0, 0, 0, 0x66));
            int highlightStart = bounds.x + bounds.width * focus / frameCount;
            int highlightEnd = bounds.x + bounds.width * (focus + 1) / frameCount;
            g2.fillRect(bounds.x, bounds.y, highlightStart - bounds.x, bounds.height);
            g2.fillRect(highlightEnd, bounds.y, bounds.x + bounds.width - highlightEnd, bounds.height);

            if (mode == Mode.SELECT_FRAME) {
                g2.setColor(new Color(0x33, 0x99, 0xff, 0xcc));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(highlightStart, bounds.y, Math.max(1, highlightEnd - highlightStart - 1), bounds.height - 1);
            }
        }

        // Show existing / default anchor on the hovered or selected frame in sheet view.
        int markFrame = focus >= 0 ? focus : selectedFrame;
        if (markFrame >= 0 && mode == Mode.SELECT_FRAME) {
            float ax = Float.isNaN(anchorX) ? getDefaultAnchorX() : anchorX;
            float ay = Float.isNaN(anchorY) ? getDefaultAnchorY() : anchorY;
            float sheetX = markFrame * frameWidth + ax;
            float sheetY = ay;
            int cx = bounds.x + Math.round(sheetX * bounds.width / (float) imageWidth);
            int cy = bounds.y + Math.round(sheetY * bounds.height / (float) imageHeight);
            paintCrosshair(g2, cx, cy, Float.isNaN(anchorX) || Float.isNaN(anchorY));
        }
    }

    private void paintPlaceMode(Graphics2D g2, Rectangle bounds) {
        // Nearest-neighbour so pixels stay blocks under integer zoom.
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int srcX = selectedFrame * frameWidth;
        // Clip source width if the last frame is short due to non-divisible sheet width.
        int srcW = Math.min(frameWidth, imageWidth - srcX);
        if (srcW <= 0) {
            srcW = frameWidth;
        }

        g2.drawImage(
                image,
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                srcX,
                0,
                srcX + srcW,
                frameHeight,
                null);

        if (showGrid && placeZoom >= 2) {
            paintPixelGrid(g2, bounds);
        }

        float ax = Float.isNaN(anchorX) ? getDefaultAnchorX() : anchorX;
        float ay = Float.isNaN(anchorY) ? getDefaultAnchorY() : anchorY;
        int cx = bounds.x + Math.round(ax * bounds.width / (float) frameWidth);
        int cy = bounds.y + Math.round(ay * bounds.height / (float) frameHeight);
        paintCrosshair(g2, cx, cy, Float.isNaN(anchorX) || Float.isNaN(anchorY));
    }

    private void paintPixelGrid(Graphics2D g2, Rectangle bounds) {
        // Dual low-opacity grid so it reads on both light and dark sprites.
        Color dark = new Color(0, 0, 0, 40);
        Color light = new Color(255, 255, 255, 36);
        g2.setStroke(new BasicStroke(1f));

        for (int gx = 0; gx <= frameWidth; gx++) {
            int x = bounds.x + Math.round(gx * bounds.width / (float) frameWidth);
            g2.setColor(dark);
            g2.drawLine(x, bounds.y, x, bounds.y + bounds.height);
            g2.setColor(light);
            g2.drawLine(x + 1, bounds.y, x + 1, bounds.y + bounds.height);
        }
        for (int gy = 0; gy <= frameHeight; gy++) {
            int y = bounds.y + Math.round(gy * bounds.height / (float) frameHeight);
            g2.setColor(dark);
            g2.drawLine(bounds.x, y, bounds.x + bounds.width, y);
            g2.setColor(light);
            g2.drawLine(bounds.x, y + 1, bounds.x + bounds.width, y + 1);
        }

        // Slightly stronger lines every 8 source pixels for orientation.
        Color majorDark = new Color(0, 0, 0, 70);
        Color majorLight = new Color(255, 255, 255, 55);
        for (int gx = 0; gx <= frameWidth; gx += 8) {
            int x = bounds.x + Math.round(gx * bounds.width / (float) frameWidth);
            g2.setColor(majorDark);
            g2.drawLine(x, bounds.y, x, bounds.y + bounds.height);
            g2.setColor(majorLight);
            g2.drawLine(x + 1, bounds.y, x + 1, bounds.y + bounds.height);
        }
        for (int gy = 0; gy <= frameHeight; gy += 8) {
            int y = bounds.y + Math.round(gy * bounds.height / (float) frameHeight);
            g2.setColor(majorDark);
            g2.drawLine(bounds.x, y, bounds.x + bounds.width, y);
            g2.setColor(majorLight);
            g2.drawLine(bounds.x, y + 1, bounds.x + bounds.width, y + 1);
        }
    }

    private void paintCrosshair(Graphics2D g2, int cx, int cy, boolean isDefault) {
        int arm = 10;
        Color ring = isDefault ? new Color(0xff, 0xcc, 0x00, 0xaa) : new Color(0xff, 0x33, 0x33, 0xee);
        Color core = isDefault ? new Color(0xff, 0xcc, 0x00, 0x66) : new Color(0xff, 0x33, 0x33, 0x99);

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(ring);
        g2.drawLine(cx - arm, cy, cx + arm, cy);
        g2.drawLine(cx, cy - arm, cx, cy + arm);
        g2.setColor(core);
        g2.fillOval(cx - 3, cy - 3, 7, 7);
        g2.setColor(ring);
        g2.drawOval(cx - 4, cy - 4, 9, 9);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (mode == Mode.SELECT_FRAME && e.getButton() == MouseEvent.BUTTON1) {
            int index = frameIndexFromMouse(e);
            if (index >= 0) {
                selectedFrame = index;
                if (listener != null) {
                    listener.onFrameSelected(index);
                }
                repaint();
                updateStatus();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (mode == Mode.PLACE_ANCHOR && e.getButton() == MouseEvent.BUTTON1) {
            placeAnchorAt(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        highlightFrame(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        highlightNone();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (mode == Mode.PLACE_ANCHOR) {
            placeAnchorAt(e);
        } else {
            highlightFrame(e);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        highlightFrame(e);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // Ctrl (or Meta on macOS) + wheel zooms in place mode; otherwise forward so
        // the enclosing JScrollPane can still scroll (registering a wheel listener
        // on the view suppresses default scroll-pane delivery).
        if (mode == Mode.PLACE_ANCHOR && (e.isControlDown() || e.isMetaDown())) {
            int notches = e.getWheelRotation();
            if (notches != 0) {
                int newZoom = placeZoom + (notches < 0 ? 1 : -1);
                zoomToward(e.getPoint(), newZoom);
            }
            e.consume();
            return;
        }
        forwardWheelToScrollPane(e);
    }

    /**
     * Re-dispatches a wheel event to the enclosing scroll pane so plain scrolling
     * still works after we attach a {@link MouseWheelListener} to this view.
     */
    private void forwardWheelToScrollPane(MouseWheelEvent e) {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll == null) {
            return;
        }
        Point p = SwingUtilities.convertPoint(this, e.getPoint(), scroll);
        MouseWheelEvent copy = new MouseWheelEvent(
                scroll,
                e.getID(),
                e.getWhen(),
                e.getModifiersEx(),
                p.x,
                p.y,
                e.getXOnScreen(),
                e.getYOnScreen(),
                e.getClickCount(),
                e.isPopupTrigger(),
                e.getScrollType(),
                e.getScrollAmount(),
                e.getWheelRotation(),
                e.getPreciseWheelRotation());
        scroll.dispatchEvent(copy);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (mode != Mode.PLACE_ANCHOR) {
            return;
        }
        int step = (e.isShiftDown()) ? 5 : 1;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                nudgeAnchor(-step, 0);
                e.consume();
                break;
            case KeyEvent.VK_RIGHT:
                nudgeAnchor(step, 0);
                e.consume();
                break;
            case KeyEvent.VK_UP:
                nudgeAnchor(0, -step);
                e.consume();
                break;
            case KeyEvent.VK_DOWN:
                nudgeAnchor(0, step);
                e.consume();
                break;
            default:
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    /**
     * Ensures {@code image} is paintable as a {@link BufferedImage} if needed.
     * Not currently required for ImageIO-loaded sheets; kept for tests/helpers.
     */
    static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }
}
