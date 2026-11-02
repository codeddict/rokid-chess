package com.sjtec.rokidchess;

import java.util.Arrays;

/** Bounded single-frame reassembly; late, missing and conflicting chunks cannot mix images. */
final class FrameBuffer {
    static final int CHUNK=12000,MAX=300000;
    private String id="";private byte[] bytes;private boolean[] seen;private int received;
    void begin(String request){clear();id=request;}
    void clear(){id="";bytes=null;seen=null;received=0;}
    byte[] accept(String request,int index,int count,int size,byte[] chunk){
        if(!id.equals(request)||id.isEmpty())return null;
        if(size<100||size>MAX||count!=(size+CHUNK-1)/CHUNK||index<0||index>=count
            ||chunk.length!=Math.min(CHUNK,size-index*CHUNK))throw new IllegalArgumentException("프레임 조각 형식 오류");
        if(bytes==null){bytes=new byte[size];seen=new boolean[count];}
        if(bytes.length!=size||seen.length!=count)throw new IllegalArgumentException("프레임 크기 충돌");
        int offset=index*CHUNK;
        if(seen[index]){
            for(int i=0;i<chunk.length;i++)if(bytes[offset+i]!=chunk[i])throw new IllegalArgumentException("프레임 조각 충돌");
            return null;
        }
        System.arraycopy(chunk,0,bytes,offset,chunk.length);seen[index]=true;received++;
        if(received!=count)return null;
        byte[] complete=bytes;clear();return complete;
    }
}
