package com.sjtec.rokidchess;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.graphics.Rect;
import java.io.ByteArrayOutputStream;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.*;

/** Camera2 on the glasses. No phone, CXR service or Bluetooth photo callback. */
final class LocalCamera implements AutoCloseable {
    interface Listener {void photo(byte[] jpeg);void error(String message);}
    private final Activity activity;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());private final HandlerThread thread=new HandlerThread("standalone-camera");
    private final Handler handler;private CameraDevice device;private CameraCaptureSession session;private ImageReader jpeg,preview;
    private volatile long shot;private volatile boolean pending,closed;private boolean opening,jpegOnly;private int format=ImageFormat.JPEG;private volatile int epoch;private int af=CaptureRequest.CONTROL_AF_MODE_OFF;
    LocalCamera(Activity activity,Listener listener){this.activity=activity;this.listener=listener;thread.start();handler=new Handler(thread.getLooper());}
    boolean pending(){return pending;}
    void capture(){if(closed||pending)return;pending=true;shot++;handler.post(()->{if(session!=null)shoot(epoch);else open();});}
    private void open(){if(closed||opening)return;opening=true;final int token=++epoch;final long request=shot;
        try{
            if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new IllegalStateException("안경 카메라 권한을 허용하세요.");
            CameraManager manager=activity.getSystemService(CameraManager.class);String[] ids=manager.getCameraIdList();if(ids.length==0)throw new IllegalStateException("안경 카메라를 찾지 못했습니다.");
            String selected=ids[0];for(String id:ids)if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))){selected=id;break;}
            CameraCharacteristics info=manager.getCameraCharacteristics(selected);int[] modes=info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)af=mode;
            StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)throw new IllegalStateException("카메라 출력 정보를 읽지 못했습니다.");
            Size size;format=ImageFormat.JPEG;
            try{size=choose(map.getOutputSizes(ImageFormat.JPEG),1024L*768);}catch(IllegalStateException unsupported){format=ImageFormat.YUV_420_888;size=choose(map.getOutputSizes(format),960L*720);}
            jpeg=ImageReader.newInstance(size.getWidth(),size.getHeight(),format,2);
            jpeg.setOnImageAvailableListener(source->{if(source!=jpeg)return;try(Image image=source.acquireLatestImage()){
                if(image==null||!pending)return;ByteBuffer data=image.getPlanes()[0].getBuffer();if(data.remaining()>4_000_000)throw new IllegalStateException("사진 데이터가 너무 큽니다.");
                byte[] bytes;if(image.getFormat()==ImageFormat.YUV_420_888){Image.Plane[] planes=image.getPlanes();byte[] nv=YuvFrame.nv21(image.getWidth(),image.getHeight(),new ByteBuffer[]{planes[0].getBuffer(),planes[1].getBuffer(),planes[2].getBuffer()},new int[]{planes[0].getRowStride(),planes[1].getRowStride(),planes[2].getRowStride()},new int[]{planes[0].getPixelStride(),planes[1].getPixelStride(),planes[2].getPixelStride()});ByteArrayOutputStream output=new ByteArrayOutputStream();if(!new YuvImage(nv,ImageFormat.NV21,image.getWidth(),image.getHeight(),null).compressToJpeg(new Rect(0,0,image.getWidth(),image.getHeight()),75,output))throw new IllegalStateException("YUV 사진 변환 실패");bytes=output.toByteArray();}else{bytes=new byte[data.remaining()];data.get(bytes);}pending=false;main.post(()->{if(!closed&&token==epoch)listener.photo(bytes);});
            }catch(Exception e){fail(token,"사진 수신 오류: "+detail(e));}},handler);
            Size[] yuv=map.getOutputSizes(ImageFormat.YUV_420_888);
            if(format==ImageFormat.JPEG&&yuv!=null&&yuv.length>0){try{Size p=choose(yuv,640L*480);preview=ImageReader.newInstance(p.getWidth(),p.getHeight(),ImageFormat.YUV_420_888,2);preview.setOnImageAvailableListener(source->{try(Image ignored=source.acquireLatestImage()){}catch(Exception ignored){}},handler);}catch(IllegalStateException unsupported){preview=null;}}
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice camera){if(closed||token!=epoch){camera.close();return;}device=camera;opening=false;configure(token,false);}
                public void onDisconnected(CameraDevice camera){camera.close();fail(token,"카메라 연결이 끊겼습니다. 다시 촬영하세요.");}
                public void onError(CameraDevice camera,int error){camera.close();fail(token,"안경 카메라 오류 "+error+" · 다른 카메라 앱을 종료하고 다시 촬영하세요.");}
            },handler);
            handler.postDelayed(()->{if(token==epoch&&request==shot&&pending)fail(token,"안경 카메라 준비 시간 초과 · 권한과 다른 촬영 앱을 확인하세요.");},10000);
        }catch(Exception e){fail(token,"안경 카메라를 열지 못했습니다: "+detail(e));}
    }
    private static Size choose(Size[] sizes,long target){if(sizes==null||sizes.length==0)throw new IllegalStateException("지원하는 사진 형식이 없습니다.");return Arrays.stream(sizes).filter(s->(long)s.getWidth()*s.getHeight()<=2_000_000).min(Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-target))).orElseThrow(()->new IllegalStateException("2MP 이하 촬영 크기가 없습니다."));}
    private void configure(int token,boolean fallback){try{
        jpegOnly=fallback||preview==null;List<Surface> outputs=new ArrayList<>();outputs.add(jpeg.getSurface());if(!jpegOnly)outputs.add(preview.getSurface());
        device.createCaptureSession(outputs,new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession configured){if(closed||token!=epoch){configured.close();return;}session=configured;
                try{if(!jpegOnly){CaptureRequest.Builder b=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(preview.getSurface());b.set(CaptureRequest.CONTROL_AF_MODE,af);configured.setRepeatingRequest(b.build(),null,handler);}
                    handler.postDelayed(()->{if(pending&&token==epoch)shoot(token);},jpegOnly?0:600);
                }catch(Exception e){fail(token,"카메라 미리보기 시작 실패: "+detail(e));}}
            public void onConfigureFailed(CameraCaptureSession failed){failed.close();if(token!=epoch)return;if(!fallback)configure(token,true);else fail(token,"안경 카메라 세션을 만들지 못했습니다.");}
        },handler);
    }catch(Exception e){if(!fallback)configure(token,true);else fail(token,"카메라 세션 오류: "+detail(e));}}
    private void shoot(int token){final long request=shot;if(closed||token!=epoch||session==null||!pending)return;try{
        CaptureRequest.Builder b=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);b.addTarget(jpeg.getSurface());b.set(CaptureRequest.JPEG_QUALITY,(byte)75);b.set(CaptureRequest.JPEG_ORIENTATION,0);b.set(CaptureRequest.CONTROL_AF_MODE,af);
        session.capture(b.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure failure){fail(token,"안경 촬영 실패 "+failure.getReason());}},handler);
        handler.postDelayed(()->{if(pending&&token==epoch&&request==shot)fail(token,"안경 사진 수신 시간 초과 · 다시 촬영하세요.");},8000);
    }catch(Exception e){fail(token,"안경 촬영 요청 오류: "+detail(e));}}
    private void fail(int token,String message){if(closed||token!=epoch)return;shutdown();main.post(()->{if(!closed)listener.error(message);});}
    void pause(){handler.post(this::shutdown);}
    private void shutdown(){epoch++;pending=false;opening=false;if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}if(jpeg!=null){jpeg.close();jpeg=null;}if(preview!=null){preview.close();preview=null;}}
    private static String detail(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.substring(0,Math.min(100,s.length()));}
    public void close(){closed=true;handler.post(()->{shutdown();thread.quitSafely();});}
}
