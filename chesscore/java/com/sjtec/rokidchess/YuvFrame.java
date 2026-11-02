package com.sjtec.rokidchess;
import java.nio.ByteBuffer;

/** Handles planar and interleaved camera chroma, padded rows and nonzero buffer positions. */
final class YuvFrame {
    static byte[] nv21(int width,int height,ByteBuffer[] planes,int[] rows,int[] pixels){
        if(width<=0||height<=0||width%2!=0||height%2!=0||(long)width*height>CameraImage.MAX_PIXELS||planes.length!=3||rows.length!=3||pixels.length!=3)
            throw new IllegalArgumentException("영상 크기 오류");
        byte[] out=new byte[width*height*3/2];int position=0;
        for(int row=0;row<height;row++)for(int col=0;col<width;col++)out[position++]=planes[0].get(planes[0].position()+row*rows[0]+col*pixels[0]);
        for(int row=0;row<height/2;row++)for(int col=0;col<width/2;col++){
            out[position++]=planes[2].get(planes[2].position()+row*rows[2]+col*pixels[2]);
            out[position++]=planes[1].get(planes[1].position()+row*rows[1]+col*pixels[1]);
        }
        return out;
    }
}
