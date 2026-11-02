package com.sjtec.rokidchess;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import androidx.core.content.ContextCompat;
import com.sjtec.rokidchess.glasses.BuildConfig;
import com.sjtec.rokidchess.shared.CoachMode;

/** Standalone entry point: local camera, on-glasses calibration, vision and engine. */
public class StandaloneActivity extends Activity {
    private enum Screen {GUIDE,MENU,CORNERS,VERSION,RESET}
    private final Handler main=new Handler(Looper.getMainLooper());private StandaloneCoach coach;private LocalCamera camera;private Hud view;
    private StandaloneCoach.State state;private Screen screen=Screen.GUIDE;private CornerPicker picker;private Bitmap photo;private byte[] photoBytes;
    private boolean active,calibrationCapture,permissionPending,resetBlack,wantCalibration;private int selection;private String notice="",cameraState="카메라 촬영 전",calibrationSource="";
    private GestureDetector gestures;private long lastInput,lastConfirmed;private String lastAction="";
    private final Runnable singleTap=this::tap;
    private final Runnable poll=()->{if(active&&screen==Screen.GUIDE&&state!=null&&state.live&&!state.busy&&!camera.pending())capture(false);};
    private final BroadcastReceiver keys=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){String a=intent.getAction();if(a==null||!active)return;
        if(a.endsWith("DOUBLE_TAP"))back();else if(a.endsWith("SWIPE_FORWARD"))swipe(1);else if(a.endsWith("SWIPE_BACK"))swipe(-1);else delayedTap();}};
    public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);view=new Hud();setContentView(view);
        camera=new LocalCamera(this,new LocalCamera.Listener(){public void photo(byte[] jpeg,String source){if(!active)return;notice="";if(wantCalibration){wantCalibration=false;capture(true);return;}
            if(calibrationCapture){calibrationSource=source;photoBytes=jpeg;photo=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(photo==null){notice="안경 사진을 읽지 못했습니다.";render();return;}picker=new CornerPicker(state!=null&&state.player.equals("black"));screen=Screen.CORNERS;render();}
            else if(!calibrationSource.equals(source)){coach.pause();notice="카메라 촬영 경로가 바뀌었습니다.\n메뉴에서 판을 다시 맞춰 주세요.";render();}else coach.observe(jpeg);
        }public void error(String message){notice=message;coach.pause();render();}
            public void status(String message){cameraState=message;if(calibrationCapture&&camera.pending()){String[] lines=message.split("\n");notice="안경에서 사진 받는 중…\n"+(lines.length>1?lines[1]:message);}render();}});
        coach=new StandaloneCoach(this,next->{state=next;if(screen==Screen.CORNERS&&!next.busy&&next.status.startsWith("판 맞추기 실패"))notice=next.status;if(!next.busy&&GuidePolicy.confirmed(next.live,next.calibrated,next.status))lastConfirmed=SystemClock.elapsedRealtime();render();main.removeCallbacks(poll);if(active&&screen==Screen.GUIDE&&next.live&&!next.busy)main.postDelayed(poll,600);});
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onSingleTapConfirmed(MotionEvent e){tap();return true;}public boolean onDoubleTap(MotionEvent e){back();return true;}public boolean onFling(MotionEvent a,MotionEvent b,float x,float y){if(a==null)return false;float dx=b.getX()-a.getX();if(Math.abs(dx)>40){swipe(dx>0?1:-1);return true;}return false;}});
        IntentFilter filter=new IntentFilter("com.android.action.ACTION_SPRITE_BUTTON_CLICK");for(String a:new String[]{"SINGLE_TAP","DOUBLE_TAP","SWIPE_FORWARD","SWIPE_BACK"})filter.addAction("com.android.action.ACTION_TWO_FINGER_"+a);ContextCompat.registerReceiver(this,keys,filter,ContextCompat.RECEIVER_EXPORTED);
    }
    private boolean input(String action){long now=SystemClock.elapsedRealtime();if(action.equals(lastAction)&&now-lastInput<180)return false;lastAction=action;lastInput=now;return true;}
    private void delayedTap(){main.removeCallbacks(singleTap);main.postDelayed(singleTap,ViewConfiguration.getDoubleTapTimeout());}
    private void tap(){if(!active||!input("tap"))return;
        if(screen==Screen.CORNERS){if(state!=null&&state.busy){notice="판 위치 적용을 기다려 주세요.";render();return;}CornerPicker.Result result=picker.tap();if(result==CornerPicker.Result.DONE){notice="판 위치를 적용하는 중…";double[][] points=picker.pixels(photo.getWidth(),photo.getHeight());coach.calibrate(photoBytes,points,()->{screen=Screen.GUIDE;clearPhoto();notice="";render();});}
            else if(result==CornerPicker.Result.RETAKE){clearPhoto();screen=Screen.GUIDE;capture(true);}else if(result==CornerPicker.Result.CANCEL){clearPhoto();screen=Screen.GUIDE;}render();return;}
        if(screen==Screen.VERSION){screen=Screen.GUIDE;render();return;}
        if(screen==Screen.RESET){if(selection==0){screen=Screen.GUIDE;coach.newGame(resetBlack,()->capture(true));}else screen=Screen.MENU;render();return;}
        if(screen==Screen.GUIDE){screen=Screen.MENU;selection=0;main.removeCallbacks(poll);render();return;}
        switch(selection){case 0:screen=Screen.GUIDE;coach.pause();capture(true);break;
            case 1:case 2:resetBlack=selection==2;screen=Screen.RESET;selection=0;break;
            case 3:case 4:case 5:coach.mode(CoachMode.values()[selection-3].key);screen=Screen.GUIDE;break;
            case 6:screen=Screen.GUIDE;if(state!=null&&state.live)coach.pause();else coach.start();break;
            case 7:coach.undo();screen=Screen.GUIDE;break;case 8:screen=Screen.VERSION;break;case 9:finish();return;}
        render();
    }
    private void swipe(int direction){if(!active||!input(direction>0?"next":"previous"))return;if(screen==Screen.CORNERS)picker.swipe(direction);else if(screen==Screen.RESET)selection=1-selection;else{if(screen!=Screen.MENU){screen=Screen.MENU;selection=0;main.removeCallbacks(poll);}else selection=Math.floorMod(selection+direction,10);}render();}
    private void back(){main.removeCallbacks(singleTap);if(!active||!input("back"))return;if(screen==Screen.CORNERS)picker.back();else{screen=Screen.GUIDE;if(state!=null&&state.live)main.postDelayed(poll,600);}render();}
    private void capture(boolean calibration){if(state==null||!state.ready||state.busy){notice="안경 엔진 준비/처리가 끝난 뒤 다시 촬영하세요.";render();return;}
        if(calibration&&camera.pending()){wantCalibration=true;notice="이전 촬영 종료 후 새 판을 촬영합니다.";render();return;}
        calibrationCapture=calibration;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){permissionPending=true;requestPermissions(new String[]{Manifest.permission.CAMERA},19);return;}
        notice=calibration?"안경 카메라 촬영 중 · 판 전체를 바라보세요.":"";camera.capture();render();
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==19){permissionPending=false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)capture(calibrationCapture);else{notice="안경 앱의 카메라 권한을 허용해야 촬영할 수 있습니다.";render();}}}
    private void clearPhoto(){if(photo!=null){photo.recycle();photo=null;}photoBytes=null;picker=null;}
    private void render(){if(view!=null)view.invalidate();}
    private final Runnable refresh=new Runnable(){public void run(){if(active){render();main.postDelayed(this,500);}}};
    protected void onResume(){super.onResume();active=true;main.post(refresh);render();}
    protected void onPause(){active=false;wantCalibration=false;main.removeCallbacksAndMessages(null);camera.pause();if(!permissionPending)coach.pause();super.onPause();}
    protected void onDestroy(){unregisterReceiver(keys);camera.close();coach.close();clearPhoto();super.onDestroy();}
    public boolean onTouchEvent(MotionEvent e){return gestures.onTouchEvent(e)||super.onTouchEvent(e);}
    public boolean onKeyDown(int code,KeyEvent event){if(event.getRepeatCount()>0)return super.onKeyDown(code,event);if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_SPACE){delayedTap();return true;}if(code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_DOWN){swipe(1);return true;}if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_UP){swipe(-1);return true;}if(code==KeyEvent.KEYCODE_BACK&&screen!=Screen.GUIDE){back();return true;}return super.onKeyDown(code,event);}
    private final class Hud extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float density=getResources().getDisplayMetrics().density;
        Hud(){super(StandaloneActivity.this);setContentDescription("안경 단독 체스 안내와 판 모서리 맞추기");}
        private float text(Canvas c,String value,float x,float y,float size,int color){paint.setColor(color);paint.setTextSize(size*density);float width=Math.max(10,getWidth()-x-12*density);for(String paragraph:value.split("\n",-1)){String line=paragraph;if(line.isEmpty()){y+=size*density*1.4f;continue;}while(!line.isEmpty()){int count=Math.max(1,paint.breakText(line,true,width,null));c.drawText(line.substring(0,count),x,y,paint);line=line.substring(count);y+=size*density*1.4f;}}return y;}
        protected void onDraw(Canvas c){c.drawColor(Color.BLACK);float pad=12*density;float y=text(c,"안경 단독 v"+BuildConfig.VERSION_NAME+" · "+(state==null?"준비 중":(state.player.equals("white")?"백":"흑")+" / "+CoachMode.stored(state.mode).label),pad,22*density,13,Color.GREEN)+6*density;
            if(screen==Screen.CORNERS&&photo!=null){float available=Math.max(50,getHeight()-y-140*density),scale=Math.min((getWidth()-2*pad)/photo.getWidth(),available/photo.getHeight());float left=(getWidth()-photo.getWidth()*scale)/2,top=y;
                c.drawBitmap(photo,null,new RectF(left,top,left+photo.getWidth()*scale,top+photo.getHeight()*scale),paint);paint.setStrokeWidth(2*density);
                for(int i=0;i<4;i++){float x=left+picker.points[i][0]*photo.getWidth()*scale,py=top+picker.points[i][1]*photo.getHeight()*scale;paint.setColor(i==picker.corner?Color.YELLOW:Color.GREEN);c.drawCircle(x,py,6*density,paint);if(i==picker.corner){c.drawLine(x-12*density,py,x+12*density,py,paint);c.drawLine(x,py-12*density,x,py+12*density,paint);}text(c,new String[]{"a8","h8","h1","a1"}[i],x+8*density,py-5*density,12,Color.YELLOW);}
                y=top+photo.getHeight()*scale+20*density;y=text(c,picker.title()+"\n"+picker.help(),pad,y,14,Color.GREEN);
            }else if(screen==Screen.MENU){String[] items={"판 촬영·맞추기","새 대국 · 내가 백","새 대국 · 내가 흑","아슬아슬하게 이기기","중간 수준으로 이기기","압도적으로 이기기",state!=null&&state.live?"안내 일시 정지":"안내 시작","한 수 되돌리기","버전·상태","앱 종료"};y=text(c,"안경에서 선택\n▶ "+items[selection]+"\n"+(selection+1)+" / "+items.length+"\n스와이프: 이동 · 탭: 선택\n두 번 탭: 안내로",pad,y+20*density,19,Color.GREEN);
            }else if(screen==Screen.RESET){y=text(c,(resetBlack?"내가 흑":"내가 백")+" · 새 대국\n현재 기보를 초기화합니다.\n▶ "+(selection==0?"초기화하고 촬영":"취소")+"\n스와이프: 선택 · 탭: 확정",pad,y+20*density,18,Color.GREEN);
            }else if(screen==Screen.VERSION){y=text(c,"안경 v"+BuildConfig.VERSION_NAME+"\n촬영·판 추적·Stockfish 19: 안경 내부\n휴대폰/인터넷 연결 필요 없음\n"+(state==null?"":state.status+"\n기보 "+state.plies+"수")+"\n"+cameraState+"\n탭: 안내로",pad,y+18*density,14,Color.GREEN);
            }else {String body=state==null?"안경 엔진 준비 중…":state.busy?"안경에서 판/엔진 처리 중…":state.live&&GuidePolicy.confirmed(true,state.calibrated,state.status)?(SystemClock.elapsedRealtime()-lastConfirmed<4500?state.advice:"판 관측 대기 · 추천 갱신 중"):state.status;y=text(c,body+"\n\n한 번 탭: 메뉴",pad,y+28*density,18,Color.GREEN);}
            if(!notice.isEmpty()){float line=19*density;int count=0;paint.setTextSize(13*density);for(String paragraph:notice.split("\n",-1)){String remaining=paragraph;if(remaining.isEmpty())count++;while(!remaining.isEmpty()){int n=Math.max(1,paint.breakText(remaining,true,Math.max(10,getWidth()-2*pad),null));remaining=remaining.substring(n);count++;}}float top=Math.max(30*density,getHeight()-(count+1)*line);paint.setColor(Color.BLACK);c.drawRect(0,top-line,getWidth(),getHeight(),paint);text(c,notice,pad,top,13,Color.YELLOW);}
        }
    }
}
