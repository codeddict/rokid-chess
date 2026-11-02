package com.sjtec.rokidchess;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

/** Match Android BitmapFactory pixel coordinates, regardless of camera EXIF rotation. */
final class CameraImage {
    static final long MAX_PIXELS=2_500_000; // Includes 1920x1080 (2,073,600), unlike a strict 2,000,000 cap.
    static Mat decode(byte[] bytes){MatOfByte buffer=new MatOfByte(bytes);try{Mat result=Imgcodecs.imdecode(buffer,Imgcodecs.IMREAD_COLOR|Imgcodecs.IMREAD_IGNORE_ORIENTATION);if(result.empty()){result.release();throw new IllegalArgumentException("사진을 읽지 못했습니다.");}if(result.total()>MAX_PIXELS){result.release();throw new IllegalArgumentException("사진 해상도가 너무 큽니다.");}return result;}finally{buffer.release();}}
}
