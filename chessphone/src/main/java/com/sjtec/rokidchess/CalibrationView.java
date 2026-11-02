package com.sjtec.rokidchess;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.opencv.core.Point;
import java.util.*;

/** Fit-centre preview; returns corners in the unrotated camera JPEG coordinate system. */
final class CalibrationView extends View {
    private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Point> corners=new ArrayList<>();private final Runnable changed;
    CalibrationView(Context c,byte[] jpeg,Runnable changed){super(c);image=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);this.changed=changed;}
    boolean complete(){return image!=null&&corners.size()==4;}
    int selectedCount(){return corners.size();}
    Point[] corners(){return corners.toArray(new Point[0]);}
    void reset(){corners.clear();changed.run();invalidate();}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w);int height=image==null?width:Math.round(width*image.getHeight()/(float)image.getWidth());
        setMeasuredDimension(width,Math.min(height,(int)(getResources().getDisplayMetrics().heightPixels*.6)));
    }
    private float scale(){return image==null?1:Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(Color.BLACK);if(image==null)return;float scale=scale();
        float left=(getWidth()-image.getWidth()*scale)/2,top=(getHeight()-image.getHeight()*scale)/2;
        canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);canvas.drawBitmap(image,0,0,paint);
        paint.setColor(Color.GREEN);paint.setStrokeWidth(3/scale);paint.setTextSize(22/scale);
        String[] names={"a8","h8","h1","a1"};
        for(int i=0;i<corners.size();i++){
            Point p=corners.get(i);canvas.drawCircle((float)p.x,(float)p.y,7/scale,paint);canvas.drawText(names[i],(float)p.x+9/scale,(float)p.y-5/scale,paint);
            if(i>0){Point previous=corners.get(i-1);canvas.drawLine((float)previous.x,(float)previous.y,(float)p.x,(float)p.y,paint);}
        }
        if(complete()){Point p=corners.get(0),last=corners.get(3);canvas.drawLine((float)last.x,(float)last.y,(float)p.x,(float)p.y,paint);}
        canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP&&image!=null&&corners.size()<4){
            float scale=scale(),left=(getWidth()-image.getWidth()*scale)/2,top=(getHeight()-image.getHeight()*scale)/2;
            double x=(e.getX()-left)/scale,y=(e.getY()-top)/scale;
            if(x>=0&&y>=0&&x<image.getWidth()&&y<image.getHeight()){corners.add(new Point(x,y));changed.run();invalidate();performClick();}
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
