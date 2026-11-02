package com.sjtec.rokidchess;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameRequestTest {
    @Test public void photoDeliveryCannotBeOvertakenByANewUiRequest(){FrameRequest g=new FrameRequest();long id=g.begin();int e=g.session(0);assertTrue(g.finish(id,e));assertEquals(0,g.begin());g.delivered(e-1);assertEquals(0,g.begin());g.delivered(e);assertTrue(g.begin()>id);}
    @Test public void previewFramesCompleteRequestWithoutAnyJpegCallback(){FrameRequest g=new FrameRequest();long id=g.begin();int e=g.session(0);assertFalse(g.frame(e,100,true));assertTrue(g.frame(e,450,true));assertTrue(g.finish(id,e));assertFalse(g.finish(id,e));}
    @Test public void warmupFramesAreDrainedUntilReady(){FrameRequest g=new FrameRequest();long id=g.begin();int e=g.session(0);g.warm(e,1000);for(int i=0;i<20;i++)assertFalse(g.frame(e,1100+i,true));assertTrue(g.frame(e,1400,true));assertTrue(g.finish(id,e));}
    @Test public void liveRequestsWaitForNewFramesInsteadOfUsingThePreviousImage(){FrameRequest g=new FrameRequest();long first=g.begin();int e=g.session(0);g.frame(e,450,true);g.frame(e,500,true);g.finish(first,e);g.delivered(e);for(int i=0;i<10;i++)g.frame(e,600+i,true);long second=g.begin();assertFalse(g.frame(e,700,true));assertTrue(g.frame(e,750,true));assertTrue(g.finish(second,e));}
    @Test public void fallbackPreservesRequestButInvalidatesOldSessionCallbacks(){FrameRequest g=new FrameRequest();long id=g.begin();int old=g.session(0);int newer=g.session(5000);assertFalse(g.matches(id,old));assertFalse(g.frame(old,6000,true));assertFalse(g.frame(newer,5400,true));assertTrue(g.frame(newer,5450,true));assertTrue(g.finish(id,newer));}
    @Test public void oldTimeoutCannotCancelANewPhoto(){FrameRequest g=new FrameRequest();long old=g.begin();int e=g.session(0);g.frame(e,500,false);g.finish(old,e);g.delivered(e);long next=g.begin();assertEquals(0,g.begin());assertFalse(g.matches(old,e));assertTrue(g.matches(next,e));}
    @Test public void pauseInvalidatesPendingFramesAndTimeouts(){FrameRequest g=new FrameRequest();long id=g.begin();int e=g.session(0);g.cancel();assertFalse(g.matches(id,e));assertFalse(g.frame(e,1000,false));assertFalse(g.finish(id,e));assertTrue(g.begin()>id);}
    @Test public void jpegFallbackCanDeliverItsFirstRequestedImage(){FrameRequest g=new FrameRequest();long id=g.begin();int e=g.session(0);assertTrue(g.frame(e,1,false));assertTrue(g.finish(id,e));}
}
