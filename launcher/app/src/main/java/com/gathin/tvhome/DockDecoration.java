package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    private final boolean cosy;
    // Cosy has a soft, close-fitting band. Give its ends more breathing room than its top/bottom.
    private static final float COSY_SIDE_PAD_DP = 40f;
    private static final float COSY_VERTICAL_PAD_DP = 9f;
    private static final int COSY_BAND_COLOR = 0x2015242B; // ~13% opacity

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names) {
        this(dockSize, density, translucent, names, false);
    }

    DockDecoration(int dockSize, float density, boolean translucent, boolean names, boolean cosy) {
        this.cosy = cosy;
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        band.setColor(cosy ? COSY_BAND_COLOR : (translucent ? 0x8C121014 : 0xFF141416));
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(cosy ? 0x00FFFFFF : (translucent ? 0x26FFFFFF : 0x1FFFFFFF));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float sidePad = (cosy ? COSY_SIDE_PAD_DP : 14f) * density;
        float verticalPad = (cosy ? COSY_VERTICAL_PAD_DP : 14f) * density;
        // Keep the band fully inside the grid: a wider end cap is fine where there is room,
        // but drawing beyond the first/last cell would slice its rounded corner off (#454).
        // This also accommodates a narrower grid or a changed TV display scaling.
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -verticalPad);
        rect.bottom += namesRoom;
        float radius = (cosy ? 22f : 26f) * density;
        c.drawRoundRect(rect, radius, radius, band);
        if (!cosy) c.drawRoundRect(rect, radius, radius, edge);
    }
}
