package com.sjtec.rokidchess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.Consumer;

/** Coordinate-labelled board; taps are validated against server-supplied legal moves. */
final class BoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final char[] pieces = new char[64];
    private boolean flipped;
    private String selected = "", hint = "";
    private final Consumer<String> tap;
    public BoardView(Context context) { this(context, square -> {}); }
    BoardView(Context context, Consumer<String> tap) { super(context); this.tap = tap; }
    void setPosition(String fen, boolean flip, String selection, String recommendation) {
        java.util.Arrays.fill(pieces, ' ');
        int i = 0;
        for (char c : fen.split(" ")[0].toCharArray()) {
            if (c == '/') continue;
            if (c >= '1' && c <= '8') i += c - '0';
            else if (i < 64) pieces[i++] = c;
        }
        flipped = flip; selected = selection; hint = recommendation; invalidate();
    }
    @Override protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w); setMeasuredDimension(size, size);
    }
    private String square(int index) { return "" + (char)('a' + index % 8) + (8 - index / 8); }
    @Override protected void onDraw(Canvas canvas) {
        float cell = getWidth() / 8f;
        String codes = "KQRBNPkqrbnp", symbols = "♔♕♖♗♘♙♚♛♜♝♞♟";
        for (int display = 0; display < 64; display++) {
            int index = flipped ? 63 - display : display;
            String sq = square(index);
            int row = display / 8, col = display % 8;
            boolean highlighted = hint.length() >= 4 && (hint.startsWith(sq) || hint.substring(2,4).equals(sq));
            paint.setColor(sq.equals(selected) ? 0xFFF2C94C : highlighted ? 0xFF99C9AF :
                (row + col) % 2 == 0 ? 0xFFF0E8D8 : 0xFF899E8B);
            canvas.drawRect(col*cell, row*cell, (col+1)*cell, (row+1)*cell, paint);
            paint.setColor(Color.BLACK); paint.setTextSize(cell*.22f); paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(sq, col*cell+2, row*cell+cell*.22f, paint);
            int symbol = codes.indexOf(pieces[index]);
            if (symbol >= 0) {
                paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(cell*.72f);
                canvas.drawText(symbols.substring(symbol,symbol+1), (col+.5f)*cell, (row+.83f)*cell, paint);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float cell = getWidth()/8f;
            int col = (int)(event.getX()/cell), row = (int)(event.getY()/cell);
            if (col >= 0 && col < 8 && row >= 0 && row < 8) {
                int index = row*8+col; tap.accept(square(flipped ? 63-index : index)); performClick();
            }
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
