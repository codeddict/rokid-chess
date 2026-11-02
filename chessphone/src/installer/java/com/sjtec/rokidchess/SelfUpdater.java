package com.sjtec.rokidchess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 휴대폰 앱의 사용자 실행 업데이트: 안내 확인 → 다운로드 검증 → Android 설치 화면.
// 휴대폰 설치와 안경 설치는 별도 단계이며 Android 설치 확인은 사용자가 수행한다.
final class SelfUpdater {
    private final java.util.function.Consumer<String> observer;
    private volatile HttpURLConnection activeConnection;
    private final Activity activity;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy;
    private final PendingUpdateStore pending;
    private boolean installOffered;
    private volatile boolean closed;
    SelfUpdater(Activity activity,java.util.function.Consumer<String> observer){this.activity=activity;this.observer=observer;pending=new PendingUpdateStore(activity.getFilesDir(),activity.getCacheDir());}
    private void report(String text){show(()->observer.accept(text));}
    private HttpURLConnection connection(String url) throws Exception {
        URL target=UpdateChannel.allowed(url);
        for(int redirects=0;redirects<=UpdateChannel.MAX_REDIRECTS;redirects++){
            if(closed)throw new java.io.IOException("closed");
            HttpURLConnection c=(HttpURLConnection)target.openConnection();activeConnection=c;
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","RokidChess/"+BuildConfig.VERSION_NAME);
            try{
                int code=c.getResponseCode();
                if(code!=301&&code!=302&&code!=303&&code!=307&&code!=308)return c;
                if(redirects==UpdateChannel.MAX_REDIRECTS)throw new Exception("too_many_redirects");
                target=UpdateChannel.redirect(target,c.getHeaderField("Location"));
            }catch(Exception failure){c.disconnect();throw failure;}
            c.disconnect();
        }
        throw new Exception("too_many_redirects");
    }
    private void show(Runnable work){ui.post(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())work.run();});}
    // 안내는 공개 정보이며 기기 Bearer를 보내지 않는다. 다운로드가 끝날 때까지 UI 스레드를 점유하지 않는다.
    void check(){
        if(busy||closed)return;
        if(pending.exists()){installOffered=false;resumeInstall(true);return;}
        busy=true;report("새 버전을 확인하고 있습니다…");
        worker.execute(()->{
            try{
                HttpURLConnection c=connection(UpdateChannel.MANIFEST+"?t="+System.currentTimeMillis());byte[] bytes;
                try{
                    if(c.getResponseCode()!=200)throw new Exception("manifest_unavailable");
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=c.getInputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4096)throw new Exception("manifest_too_large");}}
                    bytes=out.toByteArray();
                }finally{c.disconnect();}
                UpdateInfo info=new UpdateInfo(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
                show(()->{
                    busy=false;
                    if(info.versionCode<=BuildConfig.VERSION_CODE){report("최신 버전입니다 · v"+BuildConfig.VERSION_NAME);return;}
                    report("새 버전 v"+info.versionName+"이 있습니다.");
                    new AlertDialog.Builder(activity).setTitle("실물 체스 도우미 업데이트")
                        .setMessage("v"+BuildConfig.VERSION_NAME+" → v"+info.versionName+"\n휴대폰 앱 설치 후 안경 업데이트 버튼으로 안경도 갱신해주세요.")
                        .setNegativeButton("나중에",null).setPositiveButton("다운로드 / 설치",(d,w)->download(info)).show();
                });
            }catch(Exception e){show(()->{busy=false;report("업데이트 확인 실패 · 휴대폰 인터넷을 확인해주세요.");});}
        });
    }
    private void download(UpdateInfo info){
        if(busy)return;busy=true;report("업데이트를 다운로드하고 확인합니다…");
        worker.execute(()->{
            File directory=new File(activity.getCacheDir(),"updates"),temporary=new File(directory,"update-"+java.util.UUID.randomUUID()+".apk");
            try{
                if(!directory.isDirectory()&&!directory.mkdirs())throw new Exception("cache_unavailable");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
                HttpURLConnection c=connection(info.url);
                try{
                    if(c.getResponseCode()!=200)throw new Exception("download_unavailable");
                    try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){
                        byte[] b=new byte[32768];int n;
                        long notified=0;while((n=in.read(b))!=-1){if(closed)throw new java.io.IOException("closed");total+=n;long now=android.os.SystemClock.elapsedRealtime();if(now-notified>600){notified=now;report("다운로드 "+(total*100/info.bytes)+"% · 검증 후 설치 화면을 엽니다");}if(total>info.bytes)throw new Exception("unexpected_size");digest.update(b,0,n);out.write(b,0,n);}
                    }
                }finally{c.disconnect();}
                if(total!=info.bytes||!hex(digest.digest()).equals(info.sha256))throw new Exception("update_hash_mismatch");
                // 크기와 SHA-256 통과만으로 끝내지 않고 설치 대상 패키지·버전·현재 서명자까지 확인한다.
                verifyPackage(temporary,info);
                File apk=pending.apk(info);Files.move(temporary.toPath(),apk.toPath(),StandardCopyOption.REPLACE_EXISTING);
                // UI 콜백 전에 원자 저장한다. 설정 화면 전환/프로세스 종료 후에도 새 인스턴스가 이어갈 수 있다.
                pending.save(info);
                show(()->{busy=false;installOffered=false;resumeInstall(true);});
            }catch(Exception e){try{Files.deleteIfExists(temporary.toPath());}catch(Exception ignored){}show(()->{busy=false;report("업데이트 다운로드 또는 파일 확인 실패 · 다시 시도해주세요.");});}
        });
    }
    // 현재 서명자 집합이 정확히 같아야 한다. 서명 키 회전 이력을 허용하는 정책은 아직 없다.
    private void verifyPackage(File apk,UpdateInfo info) throws Exception {
        PackageManager pm=activity.getPackageManager();
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()!=info.versionCode
            ||!info.versionName.equals(candidate.versionName)||candidate.getLongVersionCode()<=installed.getLongVersionCode()
            ||candidate.signingInfo==null||installed.signingInfo==null)throw new Exception("unexpected_update_package");
        if(!signers(installed.signingInfo.getApkContentsSigners()).equals(signers(candidate.signingInfo.getApkContentsSigners())))throw new Exception("unexpected_update_signer");
    }
    private Set<String> signers(Signature[] signatures) throws Exception {
        Set<String> result=new HashSet<>();for(Signature signature:signatures)result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        if(result.isEmpty())throw new Exception("unsigned_update");return result;
    }
    private static String hex(byte[] value){StringBuilder s=new StringBuilder();for(byte b:value)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
    // 설치 출처 허용 및 Android 설치 확인은 사용자가 수행한다. FileProvider로 해당 캐시 APK만 공유한다.
    private void requestInstall(File apk,boolean askPermission){
        try{
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                report("이 앱의 설치 허용을 켠 뒤 돌아와주세요.");
                if(askPermission)activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;
            }
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);
            report("휴대폰 설치 화면에서 확인해주세요. 설치 후 앱을 열어 안경 업데이트를 누르세요.");
            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            // 취소 후 즉시 다시 열리는 반복을 막는다. 업데이트 버튼을 누르면 명시적으로 재시도할 수 있다.
            installOffered=true;
        }catch(Exception e){report("설치 화면을 열지 못했습니다. 업데이트 버튼에서 다시 시도해주세요.");}
    }
    // 모든 복원은 worker에서 파일 해시와 Android 패키지/서명을 다시 검증한 뒤 설치 UI로 전달한다.
    private void resumeInstall(boolean askPermission){
        if(closed||busy||installOffered||!pending.exists())return;
        busy=true;
        worker.execute(()->{
            try{
                UpdateInfo info=pending.load();
                if(info==null){show(()->busy=false);return;}
                if(info.versionCode<=BuildConfig.VERSION_CODE){pending.clear();show(()->{busy=false;report("최신 버전입니다 · v"+BuildConfig.VERSION_NAME);});return;}
                File apk=pending.verifiedFile(info);verifyPackage(apk,info);
                show(()->{busy=false;requestInstall(apk,askPermission);});
            }catch(Exception e){
                if(closed)return;
                try{pending.clear();}catch(Exception ignored){}
                show(()->{busy=false;report("대기 중인 업데이트 파일 확인 실패 · 업데이트 버튼에서 다시 다운로드해주세요.");});
            }
        });
    }
    void resume(){resumeInstall(false);}
    void close(){closed=true;HttpURLConnection c=activeConnection;if(c!=null)c.disconnect();worker.shutdownNow();}
}
