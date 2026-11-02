package com.sjtec.rokidchess;
import org.junit.*;
import static org.junit.Assert.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.*;

public class CameraImageTest {
    @BeforeClass public static void nativeRuntime(){File f=new File(System.getProperty("chess.opencv.native",""));Assume.assumeTrue(f.isFile());System.load(f.getAbsolutePath());}
    @Test public void exifDoesNotRotateTheCalibrationCoordinates()throws Exception{
        Mat image=new Mat(64,96,CvType.CV_8UC3,new Scalar(50,100,150));MatOfByte jpeg=new MatOfByte();Imgcodecs.imencode(".jpg",image,jpeg);byte[] raw=jpeg.toArray();image.release();jpeg.release();
        byte[] exif={-1,-31,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        ByteArrayOutputStream output=new ByteArrayOutputStream();output.write(raw,0,2);output.write(exif);output.write(raw,2,raw.length-2);byte[] fixture=output.toByteArray();
        MatOfByte buffer=new MatOfByte(fixture);Mat rotated=Imgcodecs.imdecode(buffer,Imgcodecs.IMREAD_COLOR);Mat unchanged=CameraImage.decode(fixture);
        try{assertEquals(64,rotated.cols());assertEquals(96,rotated.rows());assertEquals(96,unchanged.cols());assertEquals(64,unchanged.rows());}finally{rotated.release();unchanged.release();buffer.release();}
    }
    @Test public void invalidPhotoDoesNotReachBoardCalibration(){try{CameraImage.decode(new byte[]{1,2,3,4});fail();}catch(IllegalArgumentException expected){}}
}
