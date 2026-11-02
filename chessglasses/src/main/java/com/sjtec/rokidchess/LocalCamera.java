package com.sjtec.rokidchess;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

/** Standalone Camera2: consume live YUV frames first; JPEG is a bounded compatibility fallback. */
final class LocalCamera implements AutoCloseable {
    interface Listener {void photo(byte[] jpeg,String source);void error(String message);void status(String message);}
    private static final long ATTEMPT_MS=6000,TOTAL_MS=35000;
    private static final int MAX_PROFILES=8;
    private static final class Profile {
        final String id;final int format;final Size size,privateSize;
        Profile(String id,int format,Size size,Size extra){this.id=id;this.format=format;this.size=size;privateSize=extra;}
        String key(){return id+":"+format+":"+size.getWidth()+"x"+size.getHeight();}
        String label(){return "카메라 "+id+" · "+(format==ImageFormat.YUV_420_888?"YUV":"JPEG")+" "+size+(privateSize==null?"":" +미리보기");}
    }
    private final Activity activity;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("standalone-camera");private final Handler handler;
    private final FrameRequest flow=new FrameRequest();
    private volatile boolean pending,closed;private volatile int generation;
    private volatile String diagnostics="카메라 촬영 전";
    private CameraDevice device;private CameraCaptureSession session;private ImageReader frames,privatePreview;
    private final List<Profile> profiles=new ArrayList<>();private int index,captureResults,bufferEvents;
    private long requestStarted,lastReport;private String lastError="",lastProfile="카메라 준비";
    LocalCamera(Activity activity,Listener listener){this.activity=activity;this.listener=listener;thread.start();handler=new Handler(thread.getLooper());}
    boolean pending(){return pending;}
    String diagnostics(){return diagnostics;}
    void capture(){if(closed||pending)return;pending=true;handler.post(()->{
        if(closed)return;pending=true;long id=flow.begin();if(id==0)return;requestStarted=SystemClock.elapsedRealtime();
        if(session!=null){int token=flow.epoch();report("새 프레임 대기");arm(id,token);if(profile().format==ImageFormat.JPEG)shoot(token);}
        else {try{discover();index=-1;next("촬영 시작");}catch(Exception e){terminal("카메라 준비 실패: "+detail(e));}}
    });}
    private Profile profile(){return profiles.get(index);}
    private static Size choose(Size[] sizes,long target){if(sizes==null)return null;return Arrays.stream(sizes).filter(s->s.getWidth()%2==0&&s.getHeight()%2==0&&(long)s.getWidth()*s.getHeight()<=CameraImage.MAX_PIXELS).min(Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-target))).orElse(null);}
    private static Size[] sizes(StreamConfigurationMap map,int format){try{return map.getOutputSizes(format);}catch(IllegalArgumentException unsupported){return null;}}
    private void discover() throws Exception {
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new IllegalStateException("안경 카메라 권한을 허용하세요.");
        profiles.clear();CameraManager manager=activity.getSystemService(CameraManager.class);List<String> ids=new ArrayList<>(Arrays.asList(manager.getCameraIdList()));
        ids.sort(Comparator.comparingInt(id->{try{return Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))?0:1;}catch(Exception ignored){return 2;}}));
        for(String id:ids){CameraCharacteristics info=manager.getCameraCharacteristics(id);StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)continue;
            Size high=choose(sizes(map,ImageFormat.YUV_420_888),960L*720),low=choose(sizes(map,ImageFormat.YUV_420_888),640L*480);
            Size jpg=choose(sizes(map,ImageFormat.JPEG),1024L*768),extra=null;try{extra=choose(sizes(map,ImageFormat.PRIVATE),640L*480);}catch(Exception ignored){}
            if(high!=null)profiles.add(new Profile(id,ImageFormat.YUV_420_888,high,null));
            if(low!=null&&!low.equals(high))profiles.add(new Profile(id,ImageFormat.YUV_420_888,low,null));
            if(high!=null&&extra!=null)profiles.add(new Profile(id,ImageFormat.YUV_420_888,high,extra));
            if(jpg!=null&&extra!=null)profiles.add(new Profile(id,ImageFormat.JPEG,jpg,extra));
            if(jpg!=null)profiles.add(new Profile(id,ImageFormat.JPEG,jpg,null));
        }
        if(profiles.isEmpty())throw new IllegalStateException("지원하는 Full HD 이하 YUV/JPEG 카메라가 없습니다.");
        if(profiles.size()>MAX_PROFILES)profiles.subList(MAX_PROFILES,profiles.size()).clear();
    }
    private void next(String reason){
        if(closed||!pending)return;lastError=reason;releaseResources();
        if(++index>=profiles.size()||SystemClock.elapsedRealtime()-requestStarted>TOTAL_MS){terminal("호환 촬영 경로에서 사진을 받지 못했습니다.\n"+reason);return;}
        int token=flow.session(SystemClock.elapsedRealtime());generation=token;captureResults=bufferEvents=0;
        report("호환 경로 "+(index+1)+"/"+profiles.size()+" · 준비");
        handler.postDelayed(()->{if(!closed&&pending&&flow.epoch()==token)open(token);},250);
    }
    private void open(int token){if(closed||token!=flow.epoch())return;long id=flow.request();arm(id,token);
        try{Profile p=profile();frames=ImageReader.newInstance(p.size.getWidth(),p.size.getHeight(),p.format,3);frames.setOnImageAvailableListener(source->image(source,token),handler);
            if(p.privateSize!=null){privatePreview=ImageReader.newInstance(p.privateSize.getWidth(),p.privateSize.getHeight(),ImageFormat.PRIVATE,3);privatePreview.setOnImageAvailableListener(source->{try(Image ignored=source.acquireLatestImage()){}catch(Exception ignored){}},handler);}
            CameraManager manager=activity.getSystemService(CameraManager.class);
            if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){terminal("안경 카메라 권한을 허용하세요.");return;}
            manager.openCamera(p.id,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice opened){if(closed||token!=flow.epoch()){opened.close();return;}device=opened;report("장치 열림 · 세션 구성");configure(token);}
                public void onDisconnected(CameraDevice disconnected){disconnected.close();retry(token,"장치 연결 끊김");}
                public void onError(CameraDevice failed,int error){failed.close();retry(token,"장치 오류 "+error);}
            },handler);
        }catch(Exception e){retry(token,"장치/출력 오류: "+detail(e));}
    }
    private void configure(int token){try{List<Surface> outputs=new ArrayList<>();outputs.add(frames.getSurface());if(privatePreview!=null)outputs.add(privatePreview.getSurface());
        device.createCaptureSession(outputs,new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession configured){if(closed||token!=flow.epoch()){configured.close();return;}session=configured;flow.warm(token,SystemClock.elapsedRealtime());report("세션 준비 · 프레임 대기");
                try{if(profile().format==ImageFormat.YUV_420_888||privatePreview!=null){CaptureRequest.Builder b=builder(CameraDevice.TEMPLATE_PREVIEW);if(profile().format==ImageFormat.YUV_420_888)b.addTarget(frames.getSurface());if(privatePreview!=null)b.addTarget(privatePreview.getSurface());session.setRepeatingRequest(b.build(),callback(token),handler);}
                    if(profile().format==ImageFormat.JPEG)handler.postDelayed(()->{if(pending&&token==flow.epoch())shoot(token);},privatePreview==null?0:600);
                }catch(Exception e){retry(token,"반복 프레임 시작 오류: "+detail(e));}}
            public void onConfigureFailed(CameraCaptureSession failed){failed.close();retry(token,"세션 구성 거부");}
        },handler);
    }catch(Exception e){retry(token,"세션 오류: "+detail(e));}}
    private CaptureRequest.Builder builder(int template) throws Exception {
        CaptureRequest.Builder b=device.createCaptureRequest(template);CameraCharacteristics info=activity.getSystemService(CameraManager.class).getCameraCharacteristics(profile().id);
        int[] modes=info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);if(modes!=null&&modes.length>0){int selected=modes[0];for(int preferred:new int[]{CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,CaptureRequest.CONTROL_AF_MODE_OFF}){boolean found=false;for(int m:modes)if(m==preferred){selected=m;found=true;break;}if(found)break;}b.set(CaptureRequest.CONTROL_AF_MODE,selected);}
        return b;
    }
    private CameraCaptureSession.CaptureCallback callback(int token){return new CameraCaptureSession.CaptureCallback(){
        public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest request,TotalCaptureResult result){if(token==flow.epoch()){captureResults++;if(pending&&SystemClock.elapsedRealtime()-lastReport>1200)report("촬영 결과 수신 · 사진 버퍼 대기");}}
        public void onCaptureFailed(CameraCaptureSession s,CaptureRequest request,CaptureFailure failure){retry(token,"촬영 결과 실패 "+failure.getReason());}
        public void onCaptureBufferLost(CameraCaptureSession s,CaptureRequest request,Surface surface,long frame){if(token==flow.epoch()&&pending)report("사진 버퍼 손실 · 호환 경로 점검 중");}
    };}
    private void shoot(int token){if(closed||token!=flow.epoch()||session==null||!pending)return;try{CaptureRequest.Builder b=builder(CameraDevice.TEMPLATE_STILL_CAPTURE);b.addTarget(frames.getSurface());b.set(CaptureRequest.JPEG_QUALITY,(byte)75);b.set(CaptureRequest.JPEG_ORIENTATION,0);session.capture(b.build(),callback(token),handler);}catch(Exception e){retry(token,"JPEG 요청 오류: "+detail(e));}}
    private void image(ImageReader source,int token){if(source!=frames||token!=flow.epoch())return;try(Image image=source.acquireLatestImage()){
        if(image==null)return;bufferEvents++;boolean yuv=image.getFormat()==ImageFormat.YUV_420_888;
        if(!flow.frame(token,SystemClock.elapsedRealtime(),yuv))return;
        long id=flow.request();String key=profile().key();byte[] bytes;
        if(yuv){Image.Plane[] planes=image.getPlanes();byte[] nv21=YuvFrame.nv21(image.getWidth(),image.getHeight(),new ByteBuffer[]{planes[0].getBuffer(),planes[1].getBuffer(),planes[2].getBuffer()},new int[]{planes[0].getRowStride(),planes[1].getRowStride(),planes[2].getRowStride()},new int[]{planes[0].getPixelStride(),planes[1].getPixelStride(),planes[2].getPixelStride()});ByteArrayOutputStream output=new ByteArrayOutputStream();
            if(!new YuvImage(nv21,ImageFormat.NV21,image.getWidth(),image.getHeight(),null).compressToJpeg(new Rect(0,0,image.getWidth(),image.getHeight()),75,output))throw new IllegalStateException("YUV 사진 변환 실패");bytes=output.toByteArray();
        }else{ByteBuffer data=image.getPlanes()[0].getBuffer();if(data.remaining()>4_000_000)throw new IllegalStateException("사진 크기 초과");bytes=new byte[data.remaining()];data.get(bytes);}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>CameraImage.MAX_PIXELS)throw new IllegalStateException("읽을 수 없는 사진/해상도");
        if(!flow.finish(id,token))return;report("사진 수신 완료 · "+bytes.length+" bytes / "+(SystemClock.elapsedRealtime()-requestStarted)+"ms");
        main.post(()->{if(!closed&&token==generation){pending=false;handler.post(()->flow.delivered(token));listener.photo(bytes,key);}});
    }catch(Exception e){retry(token,"프레임 변환 오류: "+detail(e));}}
    private void arm(long id,int token){handler.postDelayed(()->{if(!closed&&flow.matches(id,token))retry(token,"프레임 시간 초과 · 결과 "+captureResults+" / 버퍼 "+bufferEvents);},ATTEMPT_MS);}
    private void retry(int token,String reason){if(closed||token!=flow.epoch()||flow.delivering())return;if(pending)next(reason);else {lastError=reason;flow.cancel();generation=flow.epoch();releaseResources();report("카메라 중지: "+reason);}}
    private void report(String stage){lastReport=SystemClock.elapsedRealtime();if(index>=0&&index<profiles.size())lastProfile=profile().label();diagnostics=lastProfile+"\n"+stage+"\n결과 "+captureResults+" / 이미지 "+bufferEvents;
        android.util.Log.i("RokidChessCamera",diagnostics);String text=diagnostics;main.post(()->{if(!closed)listener.status(text);});}
    private void terminal(String reason){lastError=reason;pending=false;flow.cancel();generation=flow.epoch();releaseResources();report("촬영 실패");String message=reason+"\n"+diagnostics;main.post(()->{if(!closed)listener.error(message);});}
    void pause(){pending=false;handler.post(()->{flow.cancel();generation=flow.epoch();releaseResources();});}
    private void releaseResources(){if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}if(frames!=null){frames.close();frames=null;}if(privatePreview!=null){privatePreview.close();privatePreview=null;}}
    private static String detail(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.substring(0,Math.min(100,s.length()));}
    public void close(){closed=true;handler.post(()->{flow.cancel();generation=flow.epoch();releaseResources();thread.quitSafely();});}
}
