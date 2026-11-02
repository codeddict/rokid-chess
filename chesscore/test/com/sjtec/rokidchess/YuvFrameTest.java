package com.sjtec.rokidchess;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
public class YuvFrameTest {
    @Test public void fullHdPreviewPlanesCanBeConverted(){int width=1920,height=1080,size=width*height;byte[] y=new byte[size],u=new byte[size/4],v=new byte[size/4];java.util.Arrays.fill(u,(byte)64);java.util.Arrays.fill(v,(byte)128);byte[] out=YuvFrame.nv21(width,height,new ByteBuffer[]{ByteBuffer.wrap(y),ByteBuffer.wrap(u),ByteBuffer.wrap(v)},new int[]{width,width/2,width/2},new int[]{1,1,1});assertEquals(size*3/2,out.length);assertEquals((byte)128,out[size]);assertEquals((byte)64,out[size+1]);}
    @Test public void planarWithPadding(){byte[] y={1,2,3,4,99,99,5,6,7,8,99,99};byte[] u={11,12,99},v={21,22,99};assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,21,11,22,12},YuvFrame.nv21(4,2,new ByteBuffer[]{ByteBuffer.wrap(y),ByteBuffer.wrap(u),ByteBuffer.wrap(v)},new int[]{6,3,3},new int[]{1,1,1}));}
    @Test public void interleavedChromaAndBufferOffset(){byte[] y={99,1,2,3,4,5,6,7,8};byte[] uv={99,11,21,12,22};ByteBuffer yy=ByteBuffer.wrap(y),u=ByteBuffer.wrap(uv),v=ByteBuffer.wrap(uv);yy.position(1);u.position(1);v.position(2);assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,21,11,22,12},YuvFrame.nv21(4,2,new ByteBuffer[]{yy,u,v},new int[]{4,4,4},new int[]{1,2,2}));}
    @Test(expected=IllegalArgumentException.class) public void oversizedDimensionsRejected(){YuvFrame.nv21(4000,3000,new ByteBuffer[3],new int[3],new int[3]);}
}
