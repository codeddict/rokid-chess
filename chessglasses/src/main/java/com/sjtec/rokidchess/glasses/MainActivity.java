package com.sjtec.rokidchess.glasses;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.*;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.sjtec.rokidchess.shared.CoachMode;
import org.json.JSONObject;
import com.rokid.cxr.CXRServiceBridge;
import com.rokid.cxr.Caps;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Continuous YUV preview; only the next requested frame is JPEG-encoded and transmitted. */
public final class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread cameraThread;private Handler cameraHandler;
    private CXRServiceBridge bridge;private TextView display,header;
    private final GlassMenu menu=new GlassMenu(BuildConfig.VERSION_NAME);
    private GestureDetector gestures;private long lastInputAt,lastQuery,noticeUntil;private String lastInput="",rendered="",renderedHeader="";
    private static final String PAD="com.android.action.ACTION_TWO_FINGER_";
    private final Runnable pendingTap=this::single;
    private final BroadcastReceiver keys=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(!active)return;String action=intent.getAction();
        if("com.android.action.ACTION_SPRITE_BUTTON_CLICK".equals(action)||(PAD+"SINGLE_TAP").equals(action))scheduleTap();
        else if((PAD+"DOUBLE_TAP").equals(action))back();
        else if((PAD+"SWIPE_FORWARD").equals(action))navigate(1);
        else if((PAD+"SWIPE_BACK").equals(action))navigate(-1);
    }};
    private CameraDevice camera;private CameraCaptureSession session;private ImageReader reader;
    private volatile boolean active;private boolean opening,streaming;
    private String pending="";private long lastImageTimestamp,minimumTimestamp,lastHud,expires,lastRequestElapsed;private int cameraEpoch;
    private String hud="실물 체스 도우미\n아드님과 두는 실제 판을 바라보고\n휴대폰에서 판을 맞춰 주세요.";
    private int autofocus=CaptureRequest.CONTROL_AF_MODE_OFF;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);layout.setBackgroundColor(Color.BLACK);
        header=new TextView(this);header.setTextColor(Color.GREEN);header.setTextSize(12);header.setGravity(Gravity.CENTER);layout.addView(header);
        display=new TextView(this);display.setTextColor(Color.GREEN);display.setTextSize(18);display.setGravity(Gravity.CENTER);display.setPadding(16,4,16,4);layout.addView(display);setContentView(layout);
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent e){return true;}
            public boolean onSingleTapConfirmed(MotionEvent e){single();return true;}
            public boolean onDoubleTap(MotionEvent e){back();return true;}
            public boolean onFling(MotionEvent a,MotionEvent b,float x,float y){if(a==null||b==null)return false;float dx=b.getX()-a.getX(),dy=b.getY()-a.getY();if(Math.abs(dx)>60*getResources().getDisplayMetrics().density&&Math.abs(dx)>Math.abs(dy)*1.5){navigate(dx>0?1:-1);return true;}return false;}
        });
        IntentFilter filter=new IntentFilter("com.android.action.ACTION_SPRITE_BUTTON_CLICK");for(String s:new String[]{"SINGLE_TAP","DOUBLE_TAP","SWIPE_FORWARD","SWIPE_BACK"})filter.addAction(PAD+s);
        ContextCompat.registerReceiver(this,keys,filter,ContextCompat.RECEIVER_EXPORTED);render();
        cameraThread=new HandlerThread("chess-camera");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());
        bridge=new CXRServiceBridge();
        bridge.subscribe("chess_frame_request",(CXRServiceBridge.MsgCallback)(name,args,bytes)-> {
            if(args!=null&&args.size()==1&&args.at(0).type()==Caps.Value.TYPE_STRING) {
                String id=args.at(0).getString();if(id.matches("[0-9a-f-]{36}"))cameraHandler.post(()->capture(id));
            }
        });
        bridge.subscribe("chess_stream",(CXRServiceBridge.MsgCallback)(name,args,bytes)->{
            if(args==null||args.size()!=1||args.at(0).type()!=Caps.Value.TYPE_INT32)return;
            boolean enabled=args.at(0).getInt()!=0;
            cameraHandler.post(()->{streaming=enabled;if(enabled){lastRequestElapsed=SystemClock.elapsedRealtime();openCamera();}else closeCamera();});
        });
        bridge.subscribe("chess_hud",(CXRServiceBridge.MsgCallback)(name,args,bytes)-> {
            if(args==null||args.size()!=2||args.at(0).type()!=Caps.Value.TYPE_STRING||args.at(1).type()!=Caps.Value.TYPE_INT32)return;
            String text=args.at(0).getString();int ttl=args.at(1).getInt();if(text.length()>400)return;
            main.post(()->{if(active){hud=text;lastHud=SystemClock.elapsedRealtime();expires=ttl;render();}});
        });
        bridge.subscribe("chess_state",(CXRServiceBridge.MsgCallback)(name,args,bytes)->{
            if(args==null||args.size()!=1||args.at(0).type()!=Caps.Value.TYPE_STRING)return;
            String raw=args.at(0).getString();if(raw.length()>2048)return;
            main.post(()->{if(!active)return;try{
                JSONObject s=new JSONObject(raw);if(s.getInt("v")!=1)return;
                String session=s.getString("session"),version=s.getString("phone_version");
                if(!session.matches("[0-9a-f-]{36}")||!version.matches("[0-9A-Za-z._-]{1,40}"))return;
                String pendingBefore=menu.pendingId;
                if(menu.update(session,s.getLong("seq"),s.getString("mode"),s.getBoolean("live"),s.getBoolean("calibrated"),version,s.optString("ack"),s.optBoolean("ok"),s.optString("error"))){
                    if(!pendingBefore.isEmpty()&&pendingBefore.equals(s.optString("ack"))&&menu.pendingId.isEmpty())noticeUntil=SystemClock.elapsedRealtime()+1800;
                    render();
                }
            }catch(Exception ignored){}});
        });
        bridge.subscribe("chess_probe",(CXRServiceBridge.MsgCallback)(name,args,bytes)->{
            if(args==null||args.size()!=1||args.at(0).type()!=Caps.Value.TYPE_STRING)return;String raw=args.at(0).getString();
            if(raw.length()>256)return;
            main.post(()->{if(!active)return;try{
                JSONObject q=new JSONObject(raw);String id=q.getString("id");if(q.getInt("v")!=1||!id.matches("[0-9a-f-]{36}"))return;
                JSONObject response=new JSONObject().put("v",1).put("id",id).put("glasses_version",BuildConfig.VERSION_NAME)
                    .put("glasses_code",BuildConfig.VERSION_CODE).put("phone_version",menu.phoneVersion).put("mode",menu.mode.key)
                    .put("state_synced",menu.synced).put("header",renderedHeader);
                Caps caps=new Caps();caps.write(response.toString());bridge.sendMessage("chess_probe_result",caps);
            }catch(Exception ignored){}});
        });
    }
    @Override protected void onResume(){
        super.onResume();active=true;menu.synced=false;menu.pendingId="";menu.back();main.post(tick);cameraHandler.post(watchdog);main.postDelayed(this::queryState,700);
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)cameraHandler.post(()->{if(streaming)openCamera();});
        else requestPermissions(new String[]{Manifest.permission.CAMERA},19);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==19&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)cameraHandler.post(()->{if(streaming)openCamera();});
        else {hud="카메라 권한이 필요합니다.\n안경 앱 설정에서 허용하세요.";expires=0;render();}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!active)return;
        render();if(SystemClock.elapsedRealtime()-lastQuery>10000)queryState();
        main.postDelayed(this,500);
    }};
    private void render(){
        String top="안경 v"+BuildConfig.VERSION_NAME+" · 폰 "+(menu.synced?"v"+menu.phoneVersion:"확인 중")+"\n모드: "+(menu.synced?menu.mode.label:"휴대폰 연결 대기");
        String body=menu.screen==GlassMenu.Screen.GUIDE?
            (SystemClock.elapsedRealtime()<noticeUntil&&!menu.notice.isEmpty()?menu.notice+"\n":"")+
            (expires>0&&SystemClock.elapsedRealtime()-lastHud>expires?"판 관측이 끊겼습니다.\n추천 갱신 대기":hud)+"\n[한 번 탭: 난이도·버전 메뉴]":menu.body();
        if(!top.equals(renderedHeader)){renderedHeader=top;header.setText(top);}
        if(!body.equals(rendered)){rendered=body;display.setText(body);}
    }
    private boolean input(String name){long now=SystemClock.elapsedRealtime();if(name.equals(lastInput)&&now-lastInputAt<200)return false;lastInput=name;lastInputAt=now;return true;}
    private void scheduleTap(){main.removeCallbacks(pendingTap);main.postDelayed(pendingTap,ViewConfiguration.getDoubleTapTimeout());}
    private void single(){
        if(!active||!input("tap"))return;
        if(menu.screen==GlassMenu.Screen.GUIDE){menu.open();queryState();}
        else if(menu.screen==GlassMenu.Screen.VERSION)menu.back();
        else if(menu.selection==4)menu.screen=GlassMenu.Screen.VERSION;
        else try{
            JSONObject request=new JSONObject().put("v",1).put("id",UUID.randomUUID().toString());
            if(menu.selection<3)request.put("op","mode.set").put("mode",CoachMode.values()[menu.selection].key);
            else request.put("op","guide.set").put("enabled",!menu.live);
            sendRequest(request);
        }catch(Exception e){menu.notice="설정 요청을 만들지 못했습니다.";}
        render();
    }
    private void navigate(int direction){if(!active||!input(direction>0?"next":"previous"))return;if(menu.screen!=GlassMenu.Screen.MENU)menu.open();else menu.next(direction);render();}
    private void back(){main.removeCallbacks(pendingTap);if(!active||!input("back"))return;menu.back();render();}
    private void queryState(){
        if(!active||bridge==null)return;lastQuery=SystemClock.elapsedRealtime();
        try{transmit(new JSONObject().put("v",1).put("id",UUID.randomUUID().toString()).put("op","state.get").toString());}catch(Exception ignored){}
    }
    private void sendRequest(JSONObject request) throws Exception{
        String id=request.getString("id"),raw=request.toString();menu.waiting(id);transmit(raw);
        main.postDelayed(()->{if(active&&menu.pendingId.equals(id))transmit(raw);},1800);
        main.postDelayed(()->{if(active&&menu.pendingId.equals(id)){menu.timeout(id);menu.notice="응답 없음 · 휴대폰/안경을 모두 v"+BuildConfig.VERSION_NAME+"으로 업데이트하세요.";render();}},6500);
    }
    private void transmit(String raw){try{Caps caps=new Caps();caps.write(raw);bridge.sendMessage("chess_control",caps);}catch(Exception ignored){}}
    @Override public boolean onTouchEvent(MotionEvent event){return gestures.onTouchEvent(event)||super.onTouchEvent(event);}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(event.getRepeatCount()!=0&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_SPACE||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN))return true;
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_SPACE){scheduleTap();return true;}
        if(code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_DOWN){navigate(1);return true;}
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_UP){navigate(-1);return true;}
        if(code==KeyEvent.KEYCODE_BACK&&menu.screen!=GlassMenu.Screen.GUIDE){back();return true;}
        return super.onKeyDown(code,event);
    }
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!active)return;
        if(streaming&&SystemClock.elapsedRealtime()-lastRequestElapsed>6000){streaming=false;closeCamera();}
        cameraHandler.postDelayed(this,1000);
    }};
    private void openCamera(){
        if(!active||!streaming||camera!=null||opening)return;opening=true;int epoch=++cameraEpoch;
        try{
            CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);String[] ids=manager.getCameraIdList();
            if(ids.length==0)throw new IllegalStateException("카메라 없음");String chosen=ids[0];
            for(String id:ids)if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))){chosen=id;break;}
            CameraCharacteristics info=manager.getCameraCharacteristics(chosen);int[] modes=info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)autofocus=mode;
            StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888);
            if(sizes==null||sizes.length==0)throw new IllegalStateException("YUV 영상 미지원");
            Size size=Arrays.stream(sizes).filter(s->s.getWidth()<=1280&&s.getHeight()<=960)
                .min(Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-960L*720)))
                .orElseGet(()->Arrays.stream(sizes).min(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight())).orElse(sizes[0]));
            if((long)size.getWidth()*size.getHeight()>2_000_000)throw new IllegalStateException("호환 영상 크기 없음");
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(this::image,cameraHandler);
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){opening=false;return;}
            manager.openCamera(chosen,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice device){
                    if(!active||epoch!=cameraEpoch){device.close();return;}camera=device;opening=false;
                    try{camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession ready){
                            if(!active||epoch!=cameraEpoch){ready.close();return;}session=ready;
                            try{
                                CaptureRequest.Builder preview=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                preview.addTarget(reader.getSurface());preview.set(CaptureRequest.CONTROL_AF_MODE,autofocus);
                                ready.setRepeatingRequest(preview.build(),null,cameraHandler);sendStatus("라이브 카메라 준비됨");
                            }catch(Exception e){sendStatus("영상 스트림 시작 실패");closeCamera();}
                        }
                        public void onConfigureFailed(CameraCaptureSession failed){if(epoch==cameraEpoch){sendStatus("카메라 세션 실패");closeCamera();}else failed.close();}
                    },cameraHandler);}catch(Exception e){sendStatus("카메라 세션 오류");closeCamera();}
                }
                public void onDisconnected(CameraDevice device){device.close();if(epoch==cameraEpoch){closeCamera();sendStatus("카메라 연결 끊김");}}
                public void onError(CameraDevice device,int error){device.close();if(epoch==cameraEpoch){closeCamera();sendStatus("카메라 오류 "+error);}}
            },cameraHandler);
        }catch(Exception e){opening=false;sendStatus("카메라를 열지 못했습니다. 권한/다른 앱을 확인하세요.");}
    }
    private void capture(String id){
        if(!active)return;
        if(!pending.isEmpty()){replyError(id,"이전 프레임 처리 중");return;}
        pending=id;minimumTimestamp=lastImageTimestamp;streaming=true;lastRequestElapsed=SystemClock.elapsedRealtime();openCamera();
        cameraHandler.postDelayed(()->{if(pending.equals(id)){pending="";replyError(id,"카메라 응답 시간 초과");closeCamera();if(streaming)openCamera();}},3000);
    }
    private void image(ImageReader source){
        if(source!=reader)return;
        try(Image image=source.acquireLatestImage()){
            if(image==null)return;lastImageTimestamp=image.getTimestamp();
            if(pending.isEmpty()||lastImageTimestamp<=minimumTimestamp)return;
            String id=pending;pending="";byte[] jpeg=jpeg(image);
            if(jpeg.length>300000){replyError(id,"프레임이 너무 큽니다.");return;}
            int count=(jpeg.length+11999)/12000;
            for(int index=0;index<count;index++){
                int offset=index*12000,length=Math.min(12000,jpeg.length-offset);
                Caps caps=new Caps();caps.write(id);caps.writeInt32(index);caps.writeInt32(count);caps.writeInt32(jpeg.length);caps.write(jpeg,offset,length);
                if(bridge.sendMessage("chess_frame_chunk",caps)<0){replyError(id,"영상 전송 혼잡 · 다음 프레임 대기");break;}
            }
        }catch(Exception e){String id=pending;pending="";if(!id.isEmpty())replyError(id,"프레임 전송 오류");}
    }
    private byte[] jpeg(Image image){
        int width=image.getWidth(),height=image.getHeight();Image.Plane[] planes=image.getPlanes();
        byte[] nv21=YuvFrame.nv21(width,height,new ByteBuffer[]{planes[0].getBuffer(),planes[1].getBuffer(),planes[2].getBuffer()},
            new int[]{planes[0].getRowStride(),planes[1].getRowStride(),planes[2].getRowStride()},
            new int[]{planes[0].getPixelStride(),planes[1].getPixelStride(),planes[2].getPixelStride()});
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        if(!new YuvImage(nv21,ImageFormat.NV21,width,height,null).compressToJpeg(new Rect(0,0,width,height),60,output))throw new IllegalStateException("JPEG 인코딩 실패");
        return output.toByteArray();
    }
    private void replyError(String id,String message){Caps caps=new Caps();caps.write(id);caps.write(message);bridge.sendMessage("chess_frame_error",caps);}
    private void sendStatus(String message){Caps caps=new Caps();caps.write(message);bridge.sendMessage("chess_camera_status",caps);}
    private void closeCamera(){
        cameraEpoch++;opening=false;pending="";lastImageTimestamp=minimumTimestamp=0;
        if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}
    }
    @Override protected void onPause(){active=false;main.removeCallbacksAndMessages(null);cameraHandler.removeCallbacks(watchdog);cameraHandler.post(this::closeCamera);super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(keys);cameraHandler.post(()->{closeCamera();cameraThread.quitSafely();});super.onDestroy();}
}
