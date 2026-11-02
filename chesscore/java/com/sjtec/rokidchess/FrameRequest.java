package com.sjtec.rokidchess;

/** A camera-handler state machine: request identity, fresh preview frames and stale callbacks. */
final class FrameRequest {
    private long next,request,warmUntil;private int epoch,frames,minimum;private boolean delivering;
    long begin(){if(request!=0||delivering)return 0;request=++next;minimum=frames;return request;}
    int session(long now){epoch++;delivering=false;frames=minimum=0;warmUntil=now+400;return epoch;}
    void warm(int token,long now){if(token==epoch)warmUntil=now+400;}
    boolean frame(int token,long now,boolean preview){if(token!=epoch)return false;frames++;return request!=0&&frames>minimum+(preview?1:0)&&(!preview||now>=warmUntil);}
    boolean finish(long id,int token){if(!matches(id,token))return false;request=0;delivering=true;return true;}
    boolean delivering(){return delivering;}
    void delivered(int token){if(token==epoch)delivering=false;}
    boolean matches(long id,int token){return id!=0&&id==request&&token==epoch;}
    long request(){return request;}
    int epoch(){return epoch;}
    int frames(){return frames;}
    void cancel(){request=0;delivering=false;epoch++;frames=minimum=0;}
}
