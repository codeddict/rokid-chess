package com.sjtec.rokidchess;

import android.content.Context;
import android.os.*;
import com.rokid.cxr.link.CXRLink;
import com.rokid.cxr.link.callbacks.*;
import com.rokid.cxr.link.utils.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Installation only. The glasses APK does not use CXR at runtime. */
final class GlassInstaller implements AutoCloseable {
    private static final String PACKAGE="com.sjtec.rokidchess.glasses";
    private final Context context;private final Consumer<String> status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CXRLink link;private boolean service,bt,working,install,closed;
    GlassInstaller(Context context,Consumer<String> status){this.context=context;this.status=status;}
    void connect(String token,boolean update){disconnect();install=update;CXRLink current=new CXRLink(context);link=current;
        current.setCXRLinkCbk(new ICXRLinkCbk(){
            private void post(Runnable r){main.post(()->{if(!closed&&link==current)r.run();});}
            public void onCXRLConnected(boolean ok){post(()->{service=ok;drain();});}
            public void onGlassBtConnected(boolean ok){post(()->{bt=ok;drain();});}
            public void onGlassDeviceInfo(GlassInfo info){}public void onGlassWearingStatus(boolean v){}
            public void onGlassAiAssistStart(){}public void onGlassAiAssistStop(){}public void onGlassAiInterrupt(boolean v){}public void onGlassLauncherResume(){}
        });
        status.accept("Hi Rokid 설치 연결 중…");
        if(!current.configCXRSession(new CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP,PACKAGE))||!current.connect(token))status.accept("설치 연결 실패 · Hi Rokid를 확인하세요.");
    }
    private void drain(){if(closed||link==null||!service||!bt||working)return;working=true;CXRLink current=link;
        IGlassAppCbk callback=new IGlassAppCbk(){
            private void post(String text){main.post(()->{if(!closed&&link==current)status.accept(text);});}
            public void onInstallAppResult(boolean ok){post(ok?"안경 v"+BuildConfig.VERSION_NAME+" 설치 완료. 안경 앱 목록에서 ‘실물 체스 도우미’를 직접 여세요. 대국에는 휴대폰 연결이 필요 없습니다.":"안경 설치 실패 · 다시 시도하세요.");}
            public void onUnInstallAppResult(boolean ok){}public void onOpenAppResult(boolean ok){post(ok?"안경 앱 열기 요청 완료. 단독 실행은 안경 앱 목록에서도 가능합니다.":"안경 앱 열기 실패 · 안경 앱 목록에서 직접 실행하세요.");}
            public void onStopAppResult(boolean ok){}public void onGlassAppResume(boolean ok){}public void onQueryAppResult(boolean ok){}
        };
        if(!install){current.appStart(PACKAGE+".MainActivity",callback);return;}
        status.accept("안경 단독 앱 전송 중 · 약 105MB로 시간이 걸릴 수 있습니다.");
        worker.execute(()->{try{
            File apk=new File(context.getFilesDir(),"chess-glasses.apk");
            try(InputStream in=context.getAssets().open("chess-glasses.apk");OutputStream out=new FileOutputStream(apk)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            main.post(()->{if(!closed&&link==current)try{current.appUploadAndInstall(apk.getAbsolutePath(),callback);}catch(Exception e){status.accept("안경 전송 실패 · Hi Rokid를 확인하세요.");}});
        }catch(Exception e){main.post(()->{if(!closed&&link==current)status.accept("안경 설치 파일 준비 실패");});}});
    }
    void disconnect(){CXRLink old=link;link=null;if(old!=null)try{old.disconnect();}catch(Exception ignored){}service=bt=working=false;}
    public void close(){closed=true;disconnect();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
