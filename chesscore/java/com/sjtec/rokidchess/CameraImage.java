package com.sjtec.rokidchess;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

/** Match Android BitmapFactory pixel coordinates, regardless of camera EXIF rotation. */
final class CameraImage {
    static Mat decode(byte[] bytes){MatOfByte buffer=new MatOfByte(bytes);try{Mat result=Imgcodecs.imdecode(buffer,Imgcodecs.IMREAD_COLOR|Imgcodecs.IMREAD_IGNORE_ORIENTATION);if(result.empty()){result.release();throw new IllegalArgumentException("사진을 읽지 못했습니다.");}if(result.total()>2_000_000){result.release();throw new IllegalArgumentException("사진 해상도가 너무 큽니다.");}return result;}finally{buffer.release();}}
}
