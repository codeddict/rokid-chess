package com.sjtec.rokidchess.glasses;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
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
import java.util.*;

/** HUD/control only. Rokid system photo service owns the glasses camera. */
public final class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
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
    private volatile boolean active;
    private long lastHud,expires;
    private String hud="실물 체스 도우미\n아드님과 두는 실제 판을 바라보고\n휴대폰에서 판을 맞춰 주세요.";
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
        bridge=new CXRServiceBridge();
        bridge.setStatusListener(new CXRServiceBridge.StatusListener(){
            public void onConnected(String mac,String name,int type){main.post(()->{if(active)queryState();});}
            public void onDisconnected(){main.post(()->{menu.synced=false;render();});}
            public void onConnecting(String mac,String name,int type){}
            public void onARTCStatus(float quality,boolean healthy){}
            public void onRokidAccountChanged(String account){}
            public void onAudioNoise(float noise){}
        });
        bridge.subscribe("chess_stream",(CXRServiceBridge.MsgCallback)(name,args,bytes)->{});
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
                    .put("state_synced",menu.synced).put("header",renderedHeader).put("camera_source","rokid_cxr_takePhoto");
                Caps caps=new Caps();caps.write(response.toString());bridge.sendMessage("chess_probe_result",caps);
            }catch(Exception ignored){}});
        });
    }
    @Override protected void onResume(){
        super.onResume();active=true;menu.synced=false;menu.pendingId="";menu.back();main.post(tick);main.postDelayed(this::queryState,700);
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
    @Override protected void onPause(){active=false;main.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(keys);super.onDestroy();}
}
