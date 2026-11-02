package com.sjtec.rokidchess;

import org.junit.*;
import static org.junit.Assert.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.nio.ByteBuffer;
import java.io.File;

/** Regression: useful preview frames must reach calibration/vision/engine even if JPEG never arrives. */
public class StandaloneFlowTest {
    @BeforeClass public static void nativeRuntime(){File f=new File(System.getProperty("chess.opencv.native",""));Assume.assumeTrue(f.isFile());System.load(f.getAbsolutePath());}
    private final FrameRequest flow=new FrameRequest();private int epoch;private long now=1000;
    private byte[] previewPhoto(Game board){long id=flow.begin();assertTrue(id>0);if(epoch==0)epoch=flow.session(now);assertFalse(flow.frame(epoch,now,true));assertTrue(flow.frame(epoch,now+450,true));now+=1000;
        Mat bgr=TrackerTest.picture(board),i420=new Mat(),nv=new Mat(bgr.rows()*3/2,bgr.cols(),CvType.CV_8UC1),converted=new Mat();MatOfByte jpeg=new MatOfByte();MatOfInt options=new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY,75);
        try{Imgproc.cvtColor(bgr,i420,Imgproc.COLOR_BGR2YUV_I420);byte[] planes=new byte[(int)i420.total()];i420.get(0,0,planes);int y=bgr.rows()*bgr.cols(),chroma=y/4;
            byte[] nv21=YuvFrame.nv21(bgr.cols(),bgr.rows(),new ByteBuffer[]{ByteBuffer.wrap(planes,0,y),ByteBuffer.wrap(planes,y,chroma),ByteBuffer.wrap(planes,y+chroma,chroma)},new int[]{bgr.cols(),bgr.cols()/2,bgr.cols()/2},new int[]{1,1,1});
            nv.put(0,0,nv21);Imgproc.cvtColor(nv,converted,Imgproc.COLOR_YUV2BGR_NV21);assertTrue(Imgcodecs.imencode(".jpg",converted,jpeg,options));assertTrue(flow.finish(id,epoch));flow.delivered(epoch);return jpeg.toArray();
        }finally{bgr.release();i420.release();nv.release();converted.release();jpeg.release();options.release();}
    }
    @Test public void noPhoneAndNoStillJpegEventFromPreviewThroughNextMove()throws Exception{
        File engine=new File(System.getProperty("chess.root"),".tools/stockfish/stockfish/stockfish-windows-x86-64-universal.exe");Assume.assumeTrue(engine.isFile());
        Game recorded=new Game(),physical=new Game();Mat initial=CameraImage.decode(previewPhoto(physical));
        try(LiveTracker tracker=new LiveTracker();LocalCoach coach=new LocalCoach(engine.getPath())){
            tracker.calibrate(initial,TrackerTest.corners());
            for(String move:new String[]{"e2e4","e7e5"}){physical.move(move);
                for(int sample=0;sample<2;sample++){Mat image=CameraImage.decode(previewPhoto(physical));try(LiveTracker.Frame frame=tracker.observe(image,recorded.board,now)){
                    assertEquals(frame.reason,move,frame.move);assertEquals(sample==1,frame.stable);if(frame.stable){recorded.move(frame.move);tracker.accept(frame);}
                }finally{image.release();}}
            }
            assertEquals(physical.board.getFen(),recorded.board.getFen());LocalCoach.Advice advice=coach.analyse(recorded,"white","medium");assertEquals("medium",advice.mode);assertTrue(recorded.legal().contains(advice.uci));assertFalse(advice.text.isBlank());
            System.out.println("Preview YUV -> photo -> 4 corners -> e2e4/e7e5 consensus -> Stockfish: "+advice.uci);
        }finally{initial.release();}
    }
}
