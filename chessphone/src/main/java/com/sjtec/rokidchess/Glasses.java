package com.sjtec.rokidchess;

import android.content.Context;
import android.os.*;
import com.rokid.cxr.Caps;
import com.rokid.cxr.link.CXRLink;
import com.rokid.cxr.link.callbacks.*;
import com.rokid.cxr.link.utils.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sjtec.rokidchess.shared.ControlRequest;
import org.json.JSONObject;

/** CUSTOMAPP session carries camera frames and expiring HUD directly over CXR. */
final class Glasses implements AutoCloseable {
    static final String PACKAGE="com.sjtec.rokidchess.glasses";
    interface Listener {void status(String text);void connected(boolean ready);void frame(byte[] jpeg,long roundtrip);void frameFailed(String error);void control(ControlRequest request);void probe(JSONObject response);}
    private final Context context;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private CXRLink link;private boolean service,bt,opened,dead,installing,wantInstall,starting;
    private String pending="",hud="로키드 체스\n휴대폰에서 판을 맞추세요.";private int hudTtl;private long requested;
    private final FrameBuffer frames=new FrameBuffer();
    Glasses(Context context,Listener listener){this.context=context;this.listener=listener;}
    void connect(String token,boolean install){
        release();wantInstall=install;CXRLink current=new CXRLink(context);link=current;
        link.setCXRLinkCbk(new ICXRLinkCbk(){
            private void post(Runnable r){main.post(()->{if(!dead&&link==current)r.run();});}
            public void onCXRLConnected(boolean v){post(()->{service=v;if(!v)lost();drain();});}
            public void onGlassBtConnected(boolean v){post(()->{bt=v;if(!v)lost();drain();});}
            public void onGlassDeviceInfo(GlassInfo info){}
            public void onGlassWearingStatus(boolean v){}
            public void onGlassAiAssistStart(){}
            public void onGlassAiAssistStop(){}
            public void onGlassAiInterrupt(boolean v){}
            public void onGlassLauncherResume(){post(()->{opened=false;listener.connected(false);});}
        });
        link.setCXRCustomCmdCbk((key,payload)->{if(payload!=null&&payload.length<=310000)main.post(()->{if(!dead&&link==current)receive(key,payload);});});
        if(!link.configCXRSession(new CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP,PACKAGE))||!link.connect(token))listener.status("안경 연결 요청 실패");
    }
    private void lost(){opened=false;pending="";starting=installing=false;listener.connected(false);listener.status("안경 연결 끊김 · 다시 연결하세요.");}
    private void drain(){if(dead||link==null||!service||!bt||installing||starting||opened)return;if(wantInstall)install();else open();}
    private IGlassAppCbk callback(CXRLink current){return new IGlassAppCbk(){
        private void post(Runnable r){main.post(()->{if(!dead&&link==current)r.run();});}
        public void onInstallAppResult(boolean ok){post(()->{installing=false;wantInstall=false;if(ok)open();else listener.status("안경 설치 실패 · 다시 설치하세요.");});}
        public void onUnInstallAppResult(boolean ok){}
        public void onOpenAppResult(boolean ok){post(()->{starting=false;opened=ok;listener.connected(ok);listener.status(ok?"안경 앱 연결됨 · 카메라 권한을 허용하세요.":"안경 앱을 먼저 설치하세요.");if(ok)show(hud,hudTtl);});}
        public void onStopAppResult(boolean ok){}
        public void onGlassAppResume(boolean resumed){post(()->{opened=resumed;listener.connected(resumed);});}
        public void onQueryAppResult(boolean installed){}
    };}
    private void install(){
        installing=true;CXRLink current=link;listener.status("안경 체스 앱 설치 중 · Hi Rokid 전송 안내를 확인하세요.");
        files.execute(()->{
            try{
                File file=new File(context.getFilesDir(),"chess-glasses.apk");
                try(InputStream in=context.getAssets().open("chess-glasses.apk");OutputStream out=new FileOutputStream(file)){
                    byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                }
                main.post(()->{if(!dead&&link==current)try{current.appUploadAndInstall(file.getAbsolutePath(),callback(current));}catch(Exception e){installing=false;listener.status("안경 전송 실패");}});
            }catch(Exception e){main.post(()->{installing=false;listener.status("안경 설치 파일 준비 실패");});}
        });
        main.postDelayed(()->{if(link==current&&installing){installing=false;wantInstall=false;listener.status("안경 설치 응답 시간 초과 · 다시 연결하세요.");}},120000);
    }
    private void open(){
        if(link==null||!service||!bt)return;starting=true;CXRLink current=link;
        try{current.appStart(PACKAGE+".MainActivity",callback(current));}catch(Exception e){starting=false;listener.status("안경 앱 시작 실패");}
        main.postDelayed(()->{if(link==current&&starting){starting=false;listener.status("안경 앱 시작 응답 시간 초과");}},15000);
    }
    boolean ready(){return !dead&&service&&bt&&opened;}
    void stream(boolean enabled){
        if(!ready())return;
        try{Caps caps=new Caps();caps.writeInt32(enabled?1:0);link.sendCustomCmd("chess_stream",caps);}catch(Exception e){listener.status("영상 스트림 제어 오류");}
    }
    void show(String text,int ttl){
        hud=text;hudTtl=ttl;if(!ready())return;
        try{Caps caps=new Caps();caps.write(text);caps.writeInt32(ttl);link.sendCustomCmd("chess_hud",caps);}catch(Exception e){listener.status("안경 HUD 전송 오류");}
    }
    void state(String json){
        if(!ready())return;
        try{Caps caps=new Caps();caps.write(json);link.sendCustomCmd("chess_state",caps);}catch(Exception e){android.util.Log.w("RokidChess","설정 상태 전송 실패");}
    }
    boolean probe(String id){
        if(!ready())return false;
        try{Caps caps=new Caps();caps.write(new JSONObject().put("v",1).put("id",id).toString());link.sendCustomCmd("chess_probe",caps);return true;}
        catch(Exception e){return false;}
    }
    boolean requestFrame(){
        if(!ready()||!pending.isEmpty())return false;
        pending=UUID.randomUUID().toString();frames.begin(pending);String id=pending;requested=SystemClock.elapsedRealtime();
        try{Caps caps=new Caps();caps.write(id);link.sendCustomCmd("chess_frame_request",caps);}
        catch(Exception e){pending="";listener.frameFailed("프레임 요청 실패");return false;}
        main.postDelayed(()->{if(!dead&&pending.equals(id)){pending="";frames.clear();listener.frameFailed("프레임 수신 시간 초과");}},4000);return true;
    }
    private void receive(String key,byte[] payload){
        try{
            Caps caps=Caps.fromBytes(payload);if(caps==null)return;
            if(key.equals("chess_probe_result")){
                if(caps.size()==1&&caps.at(0).type()==Caps.Value.TYPE_STRING){try{String raw=caps.at(0).getString();if(raw.length()<=2048)listener.probe(new JSONObject(raw));}catch(Exception ignored){}}
                return;
            }
            if(key.equals("chess_control")){
                if(caps.size()==1&&caps.at(0).type()==Caps.Value.TYPE_STRING){try{listener.control(ControlRequest.parse(caps.at(0).getString()));}catch(Exception ignored){}}
                return;
            }
            if(key.equals("chess_camera_status")&&caps.size()==1){listener.status(caps.at(0).getString());return;}
            if(key.equals("chess_frame_chunk")){
                if(caps.size()!=5||caps.at(0).type()!=Caps.Value.TYPE_STRING||!pending.equals(caps.at(0).getString())||pending.isEmpty())return;
                for(int i=1;i<=3;i++)if(caps.at(i).type()!=Caps.Value.TYPE_INT32)return;
                if(caps.at(4).type()!=Caps.Value.TYPE_BINARY)return;
                Caps.Binary b=caps.at(4).getBinary();
                if(b.length>FrameBuffer.CHUNK||b.offset<0||b.offset+b.length>b.data.length)throw new IllegalArgumentException("조각 크기 오류");
                byte[] complete=frames.accept(pending,caps.at(1).getInt(),caps.at(2).getInt(),caps.at(3).getInt(),Arrays.copyOfRange(b.data,b.offset,b.offset+b.length));
                if(complete!=null){pending="";listener.frame(complete,SystemClock.elapsedRealtime()-requested);}return;
            }
            if(caps.size()!=2||caps.at(0).type()!=Caps.Value.TYPE_STRING||!pending.equals(caps.at(0).getString())||pending.isEmpty())return;
            long elapsed=SystemClock.elapsedRealtime()-requested;
            if(key.equals("chess_frame")&&caps.at(1).type()==Caps.Value.TYPE_BINARY){
                Caps.Binary b=caps.at(1).getBinary();pending="";
                if(b.length<100||b.length>300000||b.offset<0||b.offset+b.length>b.data.length){listener.frameFailed("프레임 크기 오류");return;}
                listener.frame(Arrays.copyOfRange(b.data,b.offset,b.offset+b.length),elapsed);
            }else if(key.equals("chess_frame_error")&&caps.at(1).type()==Caps.Value.TYPE_STRING){pending="";listener.frameFailed(caps.at(1).getString());}
        }catch(Exception e){pending="";listener.frameFailed("프레임 형식 오류");}
    }
    void cancelFrame(){pending="";frames.clear();}
    private void release(){CXRLink old=link;link=null;if(old!=null)try{old.disconnect();}catch(Exception ignored){}service=bt=opened=starting=installing=false;pending="";}
    @Override public void close(){dead=true;release();main.removeCallbacksAndMessages(null);files.shutdownNow();}
}
