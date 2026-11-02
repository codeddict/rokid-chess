package com.sjtec.rokidchess;

import org.junit.*;
import static org.junit.Assert.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.util.*;

/** Executes the production Java tracking code with a desktop OpenCV native test runtime. */
public class TrackerTest {
    @BeforeClass public static void nativeRuntime(){File nativeFile=new File(System.getProperty("chess.opencv.native",""));Assume.assumeTrue(nativeFile.isFile());System.load(nativeFile.getAbsolutePath());}
    static Mat picture(Game game){
        Mat image=new Mat(720,960,CvType.CV_8UC3,new Scalar(175,180,170));
        char[] pieces=Game.pieces(game.board);Random random=new Random(42);
        for(int i=0;i<64;i++){
            int row=i/8,col=i%8,x=150+col*75,y=60+row*75;int v=(row+col)%2==0?220:115;
            Imgproc.rectangle(image,new Point(x,y),new Point(x+75,y+75),new Scalar(v,v,v),-1);
            for(int k=0;k<8;k++){int xx=x+5+random.nextInt(65),yy=y+5+random.nextInt(65);Imgproc.circle(image,new Point(xx,yy),1,new Scalar(v-20,v-20,v-20),-1);}
            Imgproc.putText(image,Integer.toString(i),new Point(x+2,y+12),Imgproc.FONT_HERSHEY_PLAIN,.6,new Scalar(60,60,60),1);
            if(pieces[i]!='.'){
                int piece=Character.isUpperCase(pieces[i])?250:15;
                Imgproc.circle(image,new Point(x+38,y+39),23,new Scalar(piece,piece,piece),-1);
                Imgproc.circle(image,new Point(x+38,y+39),23,new Scalar(60,60,60),2);
                Imgproc.putText(image,Character.toString(pieces[i]),new Point(x+23,y+48),Imgproc.FONT_HERSHEY_SIMPLEX,.7,new Scalar(120,120,120),2);
            }
        }
        return image;
    }
    static Point[] corners(){return new Point[]{new Point(150,60),new Point(750,60),new Point(750,660),new Point(150,660)};}
    @Test public void actualImageMoveConsensusAndReferenceUpdate(){
        Game game=new Game();Mat before=picture(game);
        try(LiveTracker tracker=new LiveTracker()){
            tracker.calibrate(before,corners());Game observed=new Game();observed.move("e2e4");Mat after=picture(observed);
            try(LiveTracker.Frame one=tracker.observe(after,game.board,1000);LiveTracker.Frame two=tracker.observe(after,game.board,1500)){
                assertEquals(one.reason,"e2e4",one.move);assertFalse(one.stable);assertTrue(two.stable);
                float[] corners=two.cameraCorners();assertNotNull(corners);assertEquals(150,corners[0],3);assertEquals(60,corners[1],3);assertEquals(750,corners[4],3);assertEquals(660,corners[5],3);
                tracker.accept(two);game.move(two.move);
            }
            try(LiveTracker.Frame same=tracker.observe(after,game.board,2000)){assertEquals("변화 없음",same.reason);assertEquals("",same.move);}
            observed.move("e7e5");Mat black=picture(observed);
            try(LiveTracker.Frame one=tracker.observe(black,game.board,2500);LiveTracker.Frame two=tracker.observe(black,game.board,3000)){assertEquals(one.reason,"e7e5",one.move);assertTrue(two.stable);}
            after.release();black.release();
        }finally{before.release();}
    }
    @Test public void cameraMotionAndBrightnessRemainAligned(){
        Game game=new Game();Mat before=picture(game),shifted=new Mat();Mat h=Mat.eye(3,3,CvType.CV_64F);h.put(0,2,12);h.put(1,2,8);
        Imgproc.warpPerspective(before,shifted,h,before.size());Core.add(shifted,new Scalar(15,15,15),shifted);
        try(LiveTracker tracker=new LiveTracker()){
            tracker.calibrate(before,corners());try(LiveTracker.Frame frame=tracker.observe(shifted,game.board,1000)){assertEquals(frame.reason,"",frame.move);assertEquals("변화 없음",frame.reason);}
        }finally{before.release();shifted.release();h.release();}
    }
    @Test public void occlusionAndTrackingLossNeverCommit(){
        Game game=new Game();Mat before=picture(game),hand=before.clone(),blank=new Mat(720,960,CvType.CV_8UC3,new Scalar(0));
        Imgproc.rectangle(hand,new Point(300,240),new Point(650,570),new Scalar(70,130,200),-1);
        try(LiveTracker tracker=new LiveTracker()){
            tracker.calibrate(before,corners());
            for(Mat image:List.of(hand,blank))try(LiveTracker.Frame f=tracker.observe(image,game.board,1000)){assertEquals("",f.move);assertFalse(f.stable);}
        }finally{before.release();hand.release();blank.release();}
    }
    @Test public void moveSurvivesPerspectiveChange(){
        Game game=new Game(),observed=new Game();Mat before=picture(game);observed.move("e2e4");Mat after=picture(observed),moved=new Mat();
        Mat h=Mat.eye(3,3,CvType.CV_64F);h.put(0,1,.018);h.put(0,2,12);h.put(1,0,-.012);h.put(1,2,11);h.put(2,0,.000025);
        Imgproc.warpPerspective(after,moved,h,after.size());
        try(LiveTracker tracker=new LiveTracker()){
            tracker.calibrate(before,corners());
            try(LiveTracker.Frame one=tracker.observe(moved,game.board,1000);LiveTracker.Frame two=tracker.observe(moved,game.board,1500)){assertEquals(one.reason,"e2e4",one.move);assertTrue(two.stable);}
        }finally{before.release();after.release();moved.release();h.release();}
    }
    @Test public void desktopVisionLatency(){
        Game game=new Game();Mat before=picture(game);long[] times=new long[20];
        try(LiveTracker tracker=new LiveTracker()){
            tracker.calibrate(before,corners());
            for(int i=0;i<times.length;i++){long start=System.nanoTime();try(LiveTracker.Frame frame=tracker.observe(before,game.board,1000+i*500)){assertEquals("변화 없음",frame.reason);}times[i]=(System.nanoTime()-start)/1_000_000;}
            Arrays.sort(times);System.out.println("Desktop Java/OpenCV tracking p50="+times[10]+"ms p95="+times[18]+"ms");
        }finally{before.release();}
    }
}
