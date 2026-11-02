package com.sjtec.rokidchess;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class FrameBufferTest {
    @Test public void outOfOrderDuplicateAndExactReassembly(){FrameBuffer b=new FrameBuffer();b.begin("a");byte[] image=new byte[24567];new Random(9).nextBytes(image);byte[] middle=Arrays.copyOfRange(image,12000,24000);assertNull(b.accept("a",1,3,image.length,middle));assertNull(b.accept("a",1,3,image.length,middle));assertNull(b.accept("a",2,3,image.length,Arrays.copyOfRange(image,24000,image.length)));assertArrayEquals(image,b.accept("a",0,3,image.length,Arrays.copyOfRange(image,0,12000)));}
    @Test public void lateFrameAndMissingChunkCannotMix(){FrameBuffer b=new FrameBuffer();b.begin("old");assertNull(b.accept("old",0,2,24000,new byte[12000]));b.begin("new");assertNull(b.accept("old",1,2,24000,new byte[12000]));assertNull(b.accept("new",1,2,24000,new byte[12000]));b.clear();assertNull(b.accept("new",0,2,24000,new byte[12000]));}
    @Test(expected=IllegalArgumentException.class) public void oversizedFrameRejected(){FrameBuffer b=new FrameBuffer();b.begin("a");b.accept("a",0,26,300001,new byte[12000]);}
    @Test(expected=IllegalArgumentException.class) public void conflictingDuplicateRejected(){FrameBuffer b=new FrameBuffer();b.begin("a");b.accept("a",0,2,24000,new byte[12000]);byte[] different=new byte[12000];different[3]=1;b.accept("a",0,2,24000,different);}
}
