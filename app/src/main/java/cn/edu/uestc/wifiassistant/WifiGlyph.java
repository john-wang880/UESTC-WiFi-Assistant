package cn.edu.uestc.wifiassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class WifiGlyph extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc=new RectF();
    private int accent=Ui.MINT;
    public WifiGlyph(Context context) { super(context); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void color(int color) { accent=color; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*0.39f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(0x0C77E4D0); canvas.drawCircle(cx,cy,r,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(),1)); paint.setColor(Ui.LINE);
        canvas.drawCircle(cx,cy,r,paint); canvas.drawCircle(cx,cy,r*0.82f,paint);
        paint.setStrokeWidth(Ui.dp(getContext(),5)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(accent);
        for(int i=0;i<3;i++) {
            float size=r*(0.68f-i*0.20f);
            arc.set(cx-size,cy-size*0.45f,cx+size,cy+size*1.55f);
            canvas.drawArc(arc,222,96,false,paint);
        }
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx,cy+r*0.34f,Ui.dp(getContext(),4.5f),paint);
    }
}
