package com.sjtec.rokidchess;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Real camera image with an arrow projected onto calibrated board coordinates. */
final class CameraView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap image;private float[] corners;private String move="";
    CameraView(Context context){super(context);setContentDescription("안경으로 보고 있는 실제 체스판과 추천 이동");}
    void frame(byte[] jpeg,float[] physicalCorners,String uci){
        Bitmap next=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
        if(next==null)return;image=next;corners=physicalCorners;move=uci==null?"":uci;
        requestLayout();invalidate();
    }
    void clearGuide(){move="";invalidate();}
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,image==null?width*3/4:Math.min(width,Math.round(width*image.getHeight()/(float)image.getWidth())));}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xFF14231E);paint.setTextAlign(Paint.Align.CENTER);
        if(image==null){paint.setColor(Color.WHITE);paint.setTextSize(18*getResources().getDisplayMetrics().density);
            canvas.drawText("안경으로 실제 체스판을 바라보세요",getWidth()/2f,getHeight()/2f,paint);return;}
        float scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());
        canvas.save();canvas.translate((getWidth()-image.getWidth()*scale)/2,(getHeight()-image.getHeight()*scale)/2);canvas.scale(scale,scale);canvas.drawBitmap(image,0,0,paint);
        if(corners!=null&&corners.length==8){
            paint.setColor(0xFF62E2BD);paint.setStrokeWidth(2/scale);
            for(int i=0;i<4;i++){int j=(i+1)%4;canvas.drawLine(corners[i*2],corners[i*2+1],corners[j*2],corners[j*2+1],paint);}
            if(move.matches("[a-h][1-8][a-h][1-8][qrbn]?")){
                Matrix matrix=new Matrix();matrix.setPolyToPoly(new float[]{0,0,8,0,8,8,0,8},0,corners,0,4);
                float[] points={move.charAt(0)-'a'+.5f,8-(move.charAt(1)-'0')+.5f,move.charAt(2)-'a'+.5f,8-(move.charAt(3)-'0')+.5f};matrix.mapPoints(points);
                paint.setColor(Color.YELLOW);paint.setStrokeWidth(6/scale);canvas.drawLine(points[0],points[1],points[2],points[3],paint);
                float angle=(float)Math.atan2(points[3]-points[1],points[2]-points[0]),length=20/scale;
                for(float turn:new float[]{-.6f,.6f})canvas.drawLine(points[2],points[3],points[2]-length*(float)Math.cos(angle+turn),points[3]-length*(float)Math.sin(angle+turn),paint);
                for(int i=0;i<2;i++){
                    paint.setColor(Color.YELLOW);canvas.drawCircle(points[i*2],points[i*2+1],8/scale,paint);
                    paint.setColor(Color.BLACK);paint.setTextSize(20/scale);paint.setStrokeWidth(5/scale);paint.setStyle(Paint.Style.STROKE);
                    String label=move.substring(i*2,i*2+2);canvas.drawText(label,points[i*2],points[i*2+1]-14/scale,paint);
                    paint.setColor(Color.YELLOW);paint.setStyle(Paint.Style.FILL);canvas.drawText(label,points[i*2],points[i*2+1]-14/scale,paint);
                }
            }
        }
        canvas.restore();
    }
    void release(){if(image!=null){image.recycle();image=null;}}
}
