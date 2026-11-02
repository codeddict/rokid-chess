package com.sjtec.rokidchess.glasses;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
public class YuvFrameTest {
    @Test public void planarWithPadding(){byte[] y={1,2,3,4,99,99,5,6,7,8,99,99};byte[] u={11,12,99},v={21,22,99};assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,21,11,22,12},YuvFrame.nv21(4,2,new ByteBuffer[]{ByteBuffer.wrap(y),ByteBuffer.wrap(u),ByteBuffer.wrap(v)},new int[]{6,3,3},new int[]{1,1,1}));}
    @Test public void interleavedChromaAndBufferOffset(){byte[] y={99,1,2,3,4,5,6,7,8};byte[] uv={99,11,21,12,22};ByteBuffer yy=ByteBuffer.wrap(y),u=ByteBuffer.wrap(uv),v=ByteBuffer.wrap(uv);yy.position(1);u.position(1);v.position(2);assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,21,11,22,12},YuvFrame.nv21(4,2,new ByteBuffer[]{yy,u,v},new int[]{4,4,4},new int[]{1,2,2}));}
    @Test(expected=IllegalArgumentException.class) public void oversizedDimensionsRejected(){YuvFrame.nv21(4000,3000,new ByteBuffer[3],new int[3],new int[3]);}
}
