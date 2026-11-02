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
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper;
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission;
import org.json.JSONObject;

/** System-camera photos arrive via the official CXR image callback; CUSTOMAPP carries HUD/control. */
final class Glasses implements AutoCloseable {
    static final String PACKAGE="com.sjtec.rokidchess.glasses";
    interface Listener {void status(String text);void connected(boolean ready);void frame(byte[] jpeg,long roundtrip);void frameFailed(String error);void control(ControlRequest request);void probe(JSONObject response);void cameraStatus(String text);}
    private final Context context;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private CXRLink link;private boolean service,bt,opened,dead,installing,wantInstall,starting;
    private String hud="로키드 체스\n휴대폰에서 판을 맞추세요.";private int hudTtl;private long requested;
    private final PhotoRequest photo=new PhotoRequest();
    private String cameraResult="로키드 카메라: 아직 촬영하지 않았습니다.";
    String cameraResult(){return cameraResult;}
    private void cameraStatus(String text){cameraResult=text;listener.cameraStatus(text);}
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
        current.setCXRImageCbk(new IImageStreamCbk(){
            public void onImageReceived(byte[] jpeg){
                if(jpeg==null||jpeg.length<100||jpeg.length>2_000_000){main.post(()->{if(!dead&&link==current&&photo.complete()){cameraStatus("로키드 카메라: 사진 데이터 크기 오류");listener.frameFailed("로키드 사진 수신 오류 · 다시 촬영하세요.");}});return;}
                byte[] image=jpeg.clone();
                main.post(()->{
                    if(dead||link!=current||!photo.complete())return;
                    long elapsed=SystemClock.elapsedRealtime()-requested;
                    cameraStatus("로키드 카메라: 사진 수신 완료 · "+image.length+" bytes / "+elapsed+"ms");
                    listener.frame(image,elapsed);
                });
            }
            public void onImageError(int code,String message){main.post(()->{
                if(dead||link!=current||!photo.complete())return;
                String detail=message==null?"":message.substring(0,Math.min(140,message.length()));
                String error="로키드 촬영 오류 "+code+" · "+detail;
                cameraStatus(error);listener.frameFailed(error+" · Hi Rokid 카메라 권한/연결을 확인하세요.");
            });}
        });
        if(!link.configCXRSession(new CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP,PACKAGE))||!link.connect(token))listener.status("안경 연결 요청 실패");
    }
    private void lost(){opened=false;if(photo.pending())photo.invalidate();starting=installing=false;listener.connected(false);listener.status("안경 연결 끊김 · 다시 연결하세요.");}
    private void drain(){if(dead||link==null||!service||!bt||installing||starting||opened)return;if(wantInstall)install();else open();}
    private IGlassAppCbk callback(CXRLink current){return new IGlassAppCbk(){
        private void post(Runnable r){main.post(()->{if(!dead&&link==current)r.run();});}
        public void onInstallAppResult(boolean ok){post(()->{installing=false;wantInstall=false;if(ok)open();else listener.status("안경 설치 실패 · 다시 설치하세요.");});}
        public void onUnInstallAppResult(boolean ok){}
        public void onOpenAppResult(boolean ok){post(()->{starting=false;opened=ok;listener.connected(ok);listener.status(ok?"안경 앱 연결됨 · 로키드 카메라 촬영 준비":"안경 앱을 먼저 설치하세요.");if(ok){stream(false);show(hud,hudTtl);}});}
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
        // Camera ownership stays with Rokid's system photo service, including during live play.
        // Release Camera2 in an older glasses app before using that service.
        if(!ready())return;
        try{Caps caps=new Caps();caps.writeInt32(0);link.sendCustomCmd("chess_stream",caps);}catch(Exception e){listener.status("로키드 카메라 준비 신호 전송 오류");}
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
        if(!ready())return false;
        if(photo.broken()){listener.frameFailed("로키드 촬영 응답이 끊겼습니다. 안경 연결을 다시 누르세요.");return false;}
        if(photo.pending())return false;
        if(!AuthorizationHelper.INSTANCE.hasGlassPermission(GlassPermission.CAMERA)){
            cameraStatus("로키드 카메라: Hi Rokid 카메라 권한 없음");
            listener.frameFailed("안경 연결을 다시 눌러 Hi Rokid의 카메라 권한을 허용하세요.");return false;
        }
        CXRLink current=link;long id=photo.begin();if(id==0)return false;
        requested=SystemClock.elapsedRealtime();cameraStatus("로키드 카메라: 촬영 요청 중…");
        try{
            if(!current.takePhoto(1024,768,70)){
                photo.complete();cameraStatus("로키드 카메라: 촬영 요청 거부");
                listener.frameFailed("로키드 촬영 요청 실패 · Hi Rokid 연결/카메라 권한과 다른 촬영 앱을 확인하세요.");return false;
            }
        }catch(Exception e){photo.complete();cameraStatus("로키드 카메라: SDK 촬영 요청 오류");listener.frameFailed("로키드 촬영 요청 오류 · 안경을 다시 연결하세요.");return false;}
        main.postDelayed(()->{
            if(!dead&&link==current&&photo.expire(id)){
                cameraStatus("로키드 카메라: 15초 동안 사진 응답 없음");
                listener.frameFailed("로키드 사진 수신 시간 초과 · 안경 연결을 다시 누르세요.");
            }
        },PhotoRequest.TIMEOUT_MS);return true;
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
        }catch(Exception e){android.util.Log.w("RokidChess","안경 제어 응답 형식 오류");}
    }
    void cancelFrame(){photo.cancel();}
    private void release(){CXRLink old=link;link=null;if(old!=null)try{old.disconnect();}catch(Exception ignored){}service=bt=opened=starting=installing=false;photo.reset();}
    @Override public void close(){dead=true;release();main.removeCallbacksAndMessages(null);files.shutdownNow();}
}
