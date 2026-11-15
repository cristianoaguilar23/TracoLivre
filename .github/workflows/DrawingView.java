package br.com.cristianoaguilar.tracolivre;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.BlurMaskFilter;
import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Stack;

/** Offline drawing surface with independent layers, pinch zoom and flood fill. */
public class DrawingView extends View {
    public static final int TOOL_PEN = 0;
    public static final int TOOL_ERASER = 1;
    public static final int TOOL_LINE = 2;
    public static final int TOOL_RECT = 3;
    public static final int TOOL_CIRCLE = 4;
    public static final int TOOL_TEXT = 5;
    public static final int TOOL_FILL = 6;

    public static final int BRUSH_PENCIL = 0;
    public static final int BRUSH_MARKER = 1;
    public static final int BRUSH_WATERCOLOR = 2;
    public static final int BRUSH_CHALK = 3;
    public static final int BRUSH_SPRAY = 4;

    private static final int MIN_ZOOM_PERCENT = 50;
    private static final int MAX_ZOOM_PERCENT = 500;
    private static final int FILL_TOLERANCE = 36;
    private static final long MAX_FILL_PIXELS = 6_000_000L;

    private static class Action {
        final Path path;
        final Paint paint;
        final int tool;
        final float sx;
        final float sy;
        float ex;
        float ey;
        String text;
        Bitmap bitmap;

        Action(Path path, Paint paint, int tool, float x, float y) {
            this.path = path;
            this.paint = paint;
            this.tool = tool;
            this.sx = x;
            this.sy = y;
            this.ex = x;
            this.ey = y;
        }
    }

    private static class Layer {
        String name;
        boolean visible = true;
        final ArrayList<Action> actions = new ArrayList<>();
        final Stack<Action> redo = new Stack<>();
        Bitmap cache;

        Layer(String name) {
            this.name = name;
        }
    }

    /** Small primitive stack avoids boxing millions of pixel indexes during flood fill. */
    private static class IntStack {
        private int[] values = new int[1024];
        private int size;

        void push(int value) {
            if (size == values.length) {
                int next = Math.min(Integer.MAX_VALUE - 8, values.length * 2);
                int[] grown = new int[next];
                System.arraycopy(values, 0, grown, 0, size);
                values = grown;
            }
            values[size++] = value;
        }

        int pop() {
            return values[--size];
        }

        boolean isEmpty() {
            return size == 0;
        }
    }

    private final ArrayList<Layer> layers = new ArrayList<>();
    private final ScaleGestureDetector scaleDetector;

    private int activeLayer = 0;
    private Bitmap backgroundBitmap;
    private int color = Color.BLACK;
    private int tool = TOOL_PEN;
    private int brushType = BRUSH_PENCIL;
    private float width = 8f;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private String pendingText = "";
    private Action current;
    private boolean multiTouch;
    private float lastMidX;
    private float lastMidY;

    public DrawingView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        layers.add(new Layer("Camada 1"));

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float oldZoom = zoom;
                        float newZoom = Math.max(MIN_ZOOM_PERCENT / 100f,
                                Math.min(MAX_ZOOM_PERCENT / 100f,
                                        oldZoom * detector.getScaleFactor()));
                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        float ratio = newZoom / oldZoom;
                        panX = focusX - (focusX - panX) * ratio;
                        panY = focusY - (focusY - panY) * ratio;
                        zoom = newZoom;
                        invalidate();
                        return true;
                    }
                });
    }

    public void setColor(int value) {
        color = value;
        invalidate();
    }

    public void setTool(int value) {
        tool = value;
        invalidate();
    }

    public void setBrushType(int value) {
        brushType = Math.max(BRUSH_PENCIL, Math.min(BRUSH_SPRAY, value));
        invalidate();
    }

    public int getBrushType() { return brushType; }

    public void setStrokeWidth(float value) {
        width = Math.max(1f, value);
        invalidate();
    }

    public void setPendingText(String value) {
        pendingText = value == null ? "" : value;
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        if (backgroundBitmap != null && backgroundBitmap != bitmap && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = bitmap;
        invalidate();
    }

    public int getLayerCount() {
        return layers.size();
    }

    public int getActiveLayer() {
        return activeLayer;
    }

    public String getActiveLayerName() {
        return layers.get(activeLayer).name;
    }

    public boolean isActiveLayerVisible() {
        return layers.get(activeLayer).visible;
    }

    public String[] getLayerNames() {
        String[] names = new String[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            names[i] = (layer.visible ? "● " : "○ ") + layer.name;
        }
        return names;
    }

    public void setActiveLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            activeLayer = index;
            invalidate();
        }
    }

    public void addLayer() {
        layers.add(new Layer("Camada " + (layers.size() + 1)));
        activeLayer = layers.size() - 1;
        invalidate();
    }

    public void nextLayer() {
        if (!layers.isEmpty()) {
            activeLayer = (activeLayer + 1) % layers.size();
            invalidate();
        }
    }

    public void toggleActiveLayerVisibility() {
        Layer layer = layers.get(activeLayer);
        layer.visible = !layer.visible;
        invalidate();
    }

    public void renameActiveLayer(String name) {
        if (name != null && !name.trim().isEmpty()) {
            layers.get(activeLayer).name = name.trim();
            invalidate();
        }
    }

    public void deleteActiveLayer() {
        if (layers.size() > 1) {
            releaseLayer(layers.remove(activeLayer));
            activeLayer = Math.min(activeLayer, layers.size() - 1);
        } else {
            clearLayer(layers.get(0));
        }
        invalidate();
    }

    public void undo() {
        Layer layer = layers.get(activeLayer);
        if (!layer.actions.isEmpty()) {
            layer.redo.push(layer.actions.remove(layer.actions.size() - 1));
            invalidateLayer(layer);
            invalidate();
        }
    }

    public void redo() {
        Layer layer = layers.get(activeLayer);
        if (!layer.redo.isEmpty()) {
            layer.actions.add(layer.redo.pop());
            invalidateLayer(layer);
            invalidate();
        }
    }

    public void clear() {
        for (Layer layer : layers) {
            clearLayer(layer);
        }
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        zoom = 1f;
        panX = 0f;
        panY = 0f;
        current = null;
        invalidate();
    }

    private void clearLayer(Layer layer) {
        for (Action action : layer.actions) {
            releaseAction(action);
        }
        for (Action action : layer.redo) {
            releaseAction(action);
        }
        layer.actions.clear();
        layer.redo.clear();
        invalidateLayer(layer);
    }

    private void releaseLayer(Layer layer) {
        clearLayer(layer);
    }

    private void releaseAction(Action action) {
        if (action.bitmap != null && !action.bitmap.isRecycled()) {
            action.bitmap.recycle();
        }
    }

    private void invalidateLayer(Layer layer) {
        if (layer.cache != null && !layer.cache.isRecycled()) {
            layer.cache.recycle();
        }
        layer.cache = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(zoom, zoom);
        drawBackground(canvas);

        for (Layer layer : layers) {
            if (layer.visible) {
                Bitmap bitmap = getLayerBitmap(layer);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, 0f, 0f, null);
                }
            }
        }

        // The active stroke is previewed above the artwork. The eraser is committed
        // to its own layer on finger-up, so it never destroys other layers.
        if (current != null) {
            drawAction(canvas, current, true);
        }
        canvas.restore();
    }

    private void drawBackground(Canvas canvas) {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            canvas.drawBitmap(backgroundBitmap, null,
                    new RectF(0f, 0f, getWidth(), getHeight()), null);
        }
    }

    private Bitmap getLayerBitmap(Layer layer) {
        int bitmapWidth = Math.max(1, getWidth());
        int bitmapHeight = Math.max(1, getHeight());
        if (layer.cache != null && !layer.cache.isRecycled()
                && layer.cache.getWidth() == bitmapWidth
                && layer.cache.getHeight() == bitmapHeight) {
            return layer.cache;
        }

        invalidateLayer(layer);
        try {
            layer.cache = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas layerCanvas = new Canvas(layer.cache);
            for (Action action : layer.actions) {
                drawAction(layerCanvas, action, false);
            }
            return layer.cache;
        } catch (OutOfMemoryError error) {
            layer.cache = null;
            return null;
        }
    }

    private void drawAction(Canvas canvas, Action action, boolean preview) {
        if (action.tool == TOOL_PEN) {
            canvas.drawPath(action.path, action.paint);
        } else if (action.tool == TOOL_ERASER) {
            if (preview) {
                Paint previewPaint = new Paint(action.paint);
                previewPaint.setColor(Color.WHITE);
                previewPaint.setXfermode(null);
                canvas.drawPath(action.path, previewPaint);
            } else {
                Paint eraser = new Paint(action.paint);
                eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                canvas.drawPath(action.path, eraser);
            }
        } else if (action.tool == TOOL_TEXT) {
            Paint textPaint = new Paint(action.paint);
            textPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(action.text == null ? "" : action.text,
                    action.sx, action.sy, textPaint);
        } else if (action.tool == TOOL_FILL && action.bitmap != null
                && !action.bitmap.isRecycled()) {
            canvas.drawBitmap(action.bitmap, 0f, 0f, null);
        } else if (action.tool == TOOL_LINE) {
            canvas.drawLine(action.sx, action.sy, action.ex, action.ey, action.paint);
        } else {
            RectF rect = new RectF(Math.min(action.sx, action.ex),
                    Math.min(action.sy, action.ey),
                    Math.max(action.sx, action.ex),
                    Math.max(action.sy, action.ey));
            if (action.tool == TOOL_RECT) {
                canvas.drawRect(rect, action.paint);
            } else if (action.tool == TOOL_CIRCLE) {
                canvas.drawOval(rect, action.paint);
            }
        }
    }

    private float[] toCanvas(float x, float y) {
        return new float[]{(x - panX) / zoom, (y - panY) / zoom};
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();

        if (event.getPointerCount() > 1) {
            if (!multiTouch) {
                multiTouch = true;
                current = null;
                lastMidX = (event.getX(0) + event.getX(1)) / 2f;
                lastMidY = (event.getY(0) + event.getY(1)) / 2f;
            } else if (action == MotionEvent.ACTION_MOVE) {
                float midX = (event.getX(0) + event.getX(1)) / 2f;
                float midY = (event.getY(0) + event.getY(1)) / 2f;
                panX += midX - lastMidX;
                panY += midY - lastMidY;
                lastMidX = midX;
                lastMidY = midY;
                invalidate();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            current = null;
            multiTouch = false;
            invalidate();
            return true;
        }
        if (multiTouch) {
            multiTouch = false;
            current = null;
            return true;
        }

        float[] point = toCanvas(event.getX(), event.getY());
        float x = point[0];
        float y = point[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (tool == TOOL_TEXT) {
                    placePendingText(x, y);
                    return true;
                }
                if (tool == TOOL_FILL) {
                    floodFill(Math.round(x), Math.round(y));
                    return true;
                }
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                paint.setColor(color);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(tool == TOOL_ERASER ? width * 3f : width);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                if (tool == TOOL_PEN) {
                    if (brushType == BRUSH_MARKER) {
                        paint.setAlpha(115);
                        paint.setStrokeWidth(width * 2.2f);
                    } else if (brushType == BRUSH_WATERCOLOR) {
                        paint.setAlpha(115);
                        paint.setStrokeWidth(width * 1.8f);
                        paint.setMaskFilter(new BlurMaskFilter(Math.max(1.5f, width * 0.32f), BlurMaskFilter.Blur.NORMAL));
                    } else if (brushType == BRUSH_CHALK) {
                        paint.setAlpha(205);
                        paint.setStrokeWidth(width * 1.25f);
                        paint.setPathEffect(new DiscretePathEffect(Math.max(2f, width * 0.7f), Math.max(1f, width * 0.22f)));
                    } else if (brushType == BRUSH_SPRAY) {
                        paint.setAlpha(90);
                        paint.setStrokeWidth(width * 2.5f);
                        paint.setPathEffect(new DiscretePathEffect(Math.max(1f, width * 0.18f), Math.max(2f, width * 0.9f)));
                    }
                }
                current = new Action(new Path(), paint, tool, x, y);
                current.path.moveTo(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (current != null) {
                    current.ex = x;
                    current.ey = y;
                    if (tool == TOOL_PEN || tool == TOOL_ERASER) {
                        current.path.lineTo(x, y);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (current != null) {
                    current.ex = x;
                    current.ey = y;
                    if (tool == TOOL_PEN || tool == TOOL_ERASER) {
                        current.path.lineTo(x, y);
                    }
                    addAction(current);
                    current = null;
                    invalidate();
                }
                return true;

            default:
                return true;
        }
    }

    private void placePendingText(float x, float y) {
        if (pendingText.isEmpty()) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(color);
        paint.setTextSize(Math.max(18f, width * 4f));
        paint.setStyle(Paint.Style.FILL);
        Action action = new Action(new Path(), paint, TOOL_TEXT, x, y);
        action.text = pendingText;
        addAction(action);
        pendingText = "";
        invalidate();
    }

    private void addAction(Action action) {
        Layer layer = layers.get(activeLayer);
        layer.actions.add(action);
        for (Action old : layer.redo) {
            releaseAction(old);
        }
        layer.redo.clear();
        invalidateLayer(layer);
    }

    /** Fills a contiguous region based on the visible composite, on the active layer only. */
    private void floodFill(int x, int y) {
        int bitmapWidth = Math.max(1, getWidth());
        int bitmapHeight = Math.max(1, getHeight());
        if (x < 0 || y < 0 || x >= bitmapWidth || y >= bitmapHeight) {
            return;
        }

        Bitmap base = renderArtwork();
        if (base == null) {
            return;
        }

        long pixelCountLong = (long) bitmapWidth * (long) bitmapHeight;
        if (pixelCountLong <= 0L || pixelCountLong > MAX_FILL_PIXELS) {
            base.recycle();
            return;
        }
        int pixelCount = (int) pixelCountLong;
        int[] pixels;
        try {
            pixels = new int[pixelCount];
            base.getPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight);
        } catch (OutOfMemoryError error) {
            base.recycle();
            return;
        }

        int target = pixels[y * bitmapWidth + x];
        if (target == color) {
            base.recycle();
            return;
        }

        byte[] visited;
        try {
            visited = new byte[pixelCount];
        } catch (OutOfMemoryError error) {
            base.recycle();
            return;
        }

        int filledPixels = 0;
        try {
            IntStack stack = new IntStack();
            stack.push(y * bitmapWidth + x);

            while (!stack.isEmpty()) {
                int seed = stack.pop();
                int seedY = seed / bitmapWidth;
                int seedX = seed - seedY * bitmapWidth;
                if (!isFillCandidate(pixels, visited, seed, target)) {
                    continue;
                }

                int left = seedX;
                while (left > 0 && isFillCandidate(pixels, visited,
                        seedY * bitmapWidth + left - 1, target)) {
                    left--;
                }
                int right = seedX;
                while (right < bitmapWidth - 1 && isFillCandidate(pixels, visited,
                        seedY * bitmapWidth + right + 1, target)) {
                    right++;
                }

                for (int scanX = left; scanX <= right; scanX++) {
                    int index = seedY * bitmapWidth + scanX;
                    visited[index] = 1;
                    pixels[index] = color;
                    filledPixels++;
                }

                pushAdjacentSpans(stack, pixels, visited, target,
                        bitmapWidth, seedY - 1, left, right, bitmapHeight);
                pushAdjacentSpans(stack, pixels, visited, target,
                        bitmapWidth, seedY + 1, left, right, bitmapHeight);
            }
        } catch (OutOfMemoryError error) {
            if (!base.isRecycled()) base.recycle();
            return;
        }

        base.recycle();
        if (filledPixels == 0) {
            return;
        }

        // Pixels outside the selected region become transparent, so the fill remains
        // an editable action on the current layer instead of flattening the artwork.
        for (int i = 0; i < pixelCount; i++) {
            if (visited[i] == 0) {
                pixels[i] = Color.TRANSPARENT;
            }
        }

        Bitmap fillBitmap = null;
        try {
            fillBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                    Bitmap.Config.ARGB_8888);
            fillBitmap.setPixels(pixels, 0, bitmapWidth, 0, 0,
                    bitmapWidth, bitmapHeight);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            Action fill = new Action(new Path(), paint, TOOL_FILL, 0f, 0f);
            fill.bitmap = fillBitmap;
            addAction(fill);
            invalidate();
        } catch (OutOfMemoryError error) {
            if (fillBitmap != null && !fillBitmap.isRecycled()) fillBitmap.recycle();
            // Leave the artwork untouched if the device cannot allocate the fill layer.
        }
    }

    private boolean isFillCandidate(int[] pixels, byte[] visited, int index, int target) {
        return visited[index] == 0 && similar(pixels[index], target);
    }

    private void pushAdjacentSpans(IntStack stack, int[] pixels, byte[] visited,
                                   int target, int bitmapWidth, int row,
                                   int left, int right, int bitmapHeight) {
        if (row < 0 || row >= bitmapHeight) {
            return;
        }
        boolean inSpan = false;
        for (int x = left; x <= right; x++) {
            int index = row * bitmapWidth + x;
            boolean candidate = isFillCandidate(pixels, visited, index, target);
            if (candidate && !inSpan) {
                stack.push(index);
                inSpan = true;
            } else if (!candidate) {
                inSpan = false;
            }
        }
    }

    private boolean similar(int first, int second) {
        return Math.abs(Color.red(first) - Color.red(second)) <= FILL_TOLERANCE
                && Math.abs(Color.green(first) - Color.green(second)) <= FILL_TOLERANCE
                && Math.abs(Color.blue(first) - Color.blue(second)) <= FILL_TOLERANCE
                && Math.abs(Color.alpha(first) - Color.alpha(second)) <= FILL_TOLERANCE;
    }

    private Bitmap renderArtwork() {
        int bitmapWidth = Math.max(1, getWidth());
        int bitmapHeight = Math.max(1, getHeight());
        try {
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            drawBackground(canvas);
            for (Layer layer : layers) {
                if (layer.visible) {
                    Bitmap layerBitmap = getLayerBitmap(layer);
                    if (layerBitmap != null) {
                        canvas.drawBitmap(layerBitmap, 0f, 0f, null);
                    }
                }
            }
            return bitmap;
        } catch (OutOfMemoryError error) {
            return null;
        }
    }

    public Bitmap exportBitmap() {
        return renderArtwork();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            for (Layer layer : layers) {
                invalidateLayer(layer);
            }
        }
    }
}
