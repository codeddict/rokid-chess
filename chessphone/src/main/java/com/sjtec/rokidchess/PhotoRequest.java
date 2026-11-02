package com.sjtec.rokidchess;

/** The SDK image callback has no request ID. Never overlap or reuse a timed-out session. */
final class PhotoRequest {
    static final long TIMEOUT_MS=15000;
    private long sequence,active;
    private boolean cancelled,broken;
    long begin(){if(active!=0||broken)return 0;active=++sequence;cancelled=false;return active;}
    boolean pending(){return active!=0;}
    boolean broken(){return broken;}
    void cancel(){if(active!=0)cancelled=true;}
    boolean complete(){if(active==0||broken)return false;boolean deliver=!cancelled;active=0;cancelled=false;return deliver;}
    boolean expire(long id){if(active!=id||id==0)return false;boolean deliver=!cancelled;active=0;broken=true;return deliver;}
    void invalidate(){active=0;broken=true;}
    void reset(){active=0;cancelled=broken=false;}
}
