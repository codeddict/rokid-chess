package com.sjtec.rokidchess;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoRequestTest {
    @Test public void onlyOnePhotoCanBeInFlight(){PhotoRequest p=new PhotoRequest();assertTrue(p.begin()>0);assertEquals(0,p.begin());assertTrue(p.complete());assertFalse(p.complete());assertTrue(p.begin()>0);}
    @Test public void cancelledCallbackMustDrainBeforeNextRequest(){PhotoRequest p=new PhotoRequest();p.begin();p.cancel();assertEquals(0,p.begin());assertFalse(p.complete());assertTrue(p.begin()>0);assertTrue(p.complete());}
    @Test public void timeoutCannotAttributeLatePhotoToNewRequest(){PhotoRequest p=new PhotoRequest();long id=p.begin();assertTrue(p.expire(id));assertTrue(p.broken());assertEquals(0,p.begin());assertFalse(p.complete());assertEquals(0,p.begin());p.reset();assertTrue(p.begin()>id);assertTrue(p.complete());}
    @Test public void oldTimeoutCannotCancelNewPhoto(){PhotoRequest p=new PhotoRequest();long old=p.begin();assertTrue(p.complete());long newer=p.begin();assertTrue(newer>old);assertFalse(p.expire(old));assertTrue(p.pending());assertTrue(p.complete());}
    @Test public void cancelledTimeoutStillRequiresFreshSession(){PhotoRequest p=new PhotoRequest();long id=p.begin();p.cancel();assertFalse(p.expire(id));assertTrue(p.broken());assertFalse(p.complete());assertEquals(0,p.begin());}
    @Test public void disconnectDuringPhotoRejectsLateResult(){PhotoRequest p=new PhotoRequest();p.begin();p.invalidate();assertFalse(p.complete());assertEquals(0,p.begin());p.reset();assertTrue(p.begin()>0);assertTrue(p.complete());}
}
