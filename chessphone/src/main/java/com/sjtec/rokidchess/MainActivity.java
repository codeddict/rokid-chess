package com.sjtec.rokidchess;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.util.Pair;
import android.widget.*;
import com.rokid.sprite.aiapp.externalapp.auth.*;
import java.util.*;
import com.sjtec.rokidchess.shared.CoachMode;

/** Thin controller. Game, vision, engine and CXR connection live in the foreground service. */
public final class MainActivity extends Activity implements ChessService.Listener {
    private ChessService service;private ChessService.Snapshot state;
    private BoardView board;private CameraView camera;private TextView status,advice,turn,metrics;private EditText input;
    private LinearLayout correction;
    private SelfUpdater updater;private TextView updateStatus,diagnosticStatus;
    private Button live;private String selected="";private boolean bound,installRequested,visible;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=((ChessService.LocalBinder)binder).service();if(visible)service.attach(MainActivity.this);}
        public void onServiceDisconnected(ComponentName name){service=null;status.setText("체스 연결이 종료되었습니다. 다시 시작하세요.");}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density);content.setPadding(pad,pad,pad,pad);
        content.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(pad+bars.left,pad+bars.top,pad+bars.right,pad+bars.bottom);return insets;
        });
        label(content,"실물 체스 도우미",25);
        label(content,"휴대폰 v"+BuildConfig.VERSION_NAME+" · 안경 버전은 안경 메뉴에서 확인",12);
        LinearLayout updates=row(content);
        button(updates,"앱 업데이트 확인 / 설치",()->updater.check());
        button(updates,"안경 업데이트",()->authenticate(true));
        updateStatus=label(content,"다음 업데이트부터 이 버튼으로 다운로드·설치를 진행하세요.",12);
        updater=new SelfUpdater(this,text->updateStatus.setText(text));
        LinearLayout checks=row(content);
        button(checks,"안경 버전·난이도 점검",()->use(()->{diagnosticStatus.setVisibility(View.VISIBLE);service.runDiagnostics();}));
        button(checks,"점검 결과 복사",()->use(()->{
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("체스 점검",service.diagnostics()));
            Toast.makeText(this,"점검 결과를 복사했습니다.",Toast.LENGTH_SHORT).show();
        }));
        diagnosticStatus=label(content,"안경을 연결한 뒤 점검 버튼을 누르세요.",13);diagnosticStatus.setVisibility(View.GONE);
        label(content,"아드님과 실제 체스판에서 두세요.\n안경이 판을 보고, 아버지의 다음 이동을 안내합니다.",15);
        status=label(content,"실제 체스판을 보여 주세요.",15);
        advice=label(content,"판을 확인한 뒤 움직일 말과 도착 칸을 안내합니다.",21);
        camera=new CameraView(this);content.addView(camera);
        turn=label(content,"",15);
        LinearLayout modes=row(content);
        button(modes,"아슬아슬",()->use(()->service.setMode("close")));
        button(modes,"중간 수준",()->use(()->service.setMode("medium")));
        button(modes,"압도적",()->use(()->service.setMode("strong")));
        button(modes,"내 말 색 변경",()->use(()->service.changePlayer()));
        LinearLayout setup=row(content);
        button(setup,"안경 연결",()->authenticate(false));button(setup,"안경 앱 설치",()->authenticate(true));
        button(content,"실제 체스판 촬영 · 위치 맞추기",()->use(()->service.requestCalibration()));
        live=button(content,"실물 판 안내 시작",()->use(()->service.setLive(state==null||!state.live)));
        label(content,"처음에는 실물 말을 기본 시작 위치에 놓고 판을 맞춥니다. 안내에 따라 실제 말을 옮기세요. 아드님이 둔 수도 안경이 관측합니다.",14);
        button(content,"인식 보정 열기 / 닫기",()->correction.setVisibility(correction.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        correction=new LinearLayout(this);correction.setOrientation(LinearLayout.VERTICAL);correction.setVisibility(View.GONE);content.addView(correction);
        label(correction,"인식된 배치 확인 / 잘못 읽은 수 보정",18);
        board=new BoardView(this,this::tap);correction.addView(board);
        input=new EditText(this);input.setSingleLine(true);input.setHint("실제 둔 수 보정: e2e4 / e7e8q");correction.addView(input);
        button(correction,"보정한 수 반영 · 이후 실제 판 다시 맞추기",()->use(()->{service.move(input.getText().toString().trim());input.setText("");}));
        LinearLayout controls=row(content);
        button(controls,"다음 이동 재분석",()->use(()->service.recommend()));
        button(controls,"실물 새 대국",()->new AlertDialog.Builder(this).setMessage("실제 말을 기본 시작 위치에 놓고 새 대국을 준비합니다. 기록을 초기화할까요?")
            .setNegativeButton("취소",null).setPositiveButton("새 대국",(d,w)->use(()->service.resetGame())).show());
        button(correction,"잘못 읽은 한 수 되돌리기",()->use(()->service.undo()));
        button(correction,"중간 실물 배치 설정 (FEN)",this::importFen);
        metrics=label(correction,"",12);
        label(content,"안경 화면에는 이동할 말·출발 칸·도착 칸을 표시합니다. 실제 공간 위에 고정되는 AR 표시는 아닙니다.\n추적이 불안정하면 안내를 기다립니다. 휴대폰 화면을 꺼도 연결을 유지합니다.",13);
        button(content,"엔진 / 연결 다시 시작",this::boot);
        button(content,"체스 연결 종료",()->{if(bound){if(service!=null)service.detach(this);unbindService(connection);bound=false;}stopService(new Intent(this,ChessService.class));service=null;status.setText("체스 연결 종료");});
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
        board.setPosition(Game.START,false,"","");permissions();
    }
    private void permissions(){
        List<String> required=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)required.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)required.add(Manifest.permission.POST_NOTIFICATIONS);
        if(required.isEmpty())boot();else requestPermissions(required.toArray(new String[0]),12);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==12){if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)boot();else status.setText("근처 기기 권한을 허용해야 안경 연결을 유지할 수 있습니다.");}
    }
    private void boot(){
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){permissions();return;}
        startForegroundService(new Intent(this,ChessService.class));
        if(!bound)bound=bindService(new Intent(this,ChessService.class),connection,BIND_AUTO_CREATE);
    }
    private void use(Runnable action){if(service==null){status.setText("체스 연결을 먼저 시작하세요.");return;}action.run();}
    private void authenticate(boolean install){
        if(service==null){boot();status.setText("준비 완료 후 다시 연결하세요.");return;}
        installRequested=install;
        try{
            if(!AuthorizationHelper.INSTANCE.isRequiredHiRokidInstalled(this)){status.setText("Hi Rokid를 설치하고 안경을 연결하세요.");return;}
            Pair<Integer,Intent> cached=AuthorizationHelper.INSTANCE.requestAuthorization(this,new GlassPermission[]{GlassPermission.DEVICE_MANAGE},7001);
            if(cached!=null)auth(cached.first,cached.second);
        }catch(Exception e){status.setText("Hi Rokid 인증 요청 실패");}
    }
    private void auth(int code,Intent data){
        try{
            AuthResult result=AuthorizationHelper.INSTANCE.parseAuthorizationResult(code,data);
            if(result instanceof AuthResult.AuthSuccess)startForegroundService(new Intent(this,ChessService.class)
                .putExtra("token",((AuthResult.AuthSuccess)result).getToken()).putExtra("install",installRequested));
            else status.setText("Hi Rokid 인증이 취소되거나 거부되었습니다.");
        }catch(Exception e){status.setText("Hi Rokid 인증 응답 오류");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7001)auth(result,data);}
    @Override public void changed(ChessService.Snapshot next){
        if(isFinishing()||isDestroyed())return;state=next;status.setText(next.status);
        boolean confirmed=GuidePolicy.confirmed(next.live,next.calibrated,next.status);
        advice.setText(confirmed?next.advice:!next.calibrated?"실제 체스판을 먼저 보여 주세요.\n말을 초기 배치로 놓고 판을 맞춥니다.":!next.live?"판 위치를 맞췄습니다.\n실물 판 안내 시작을 누르세요.":"실물 판 확인 중 · "+next.status);
        board.setPosition(next.fen,next.player.equals("black"),selected,confirmed?next.hint:"");
        if(!confirmed)camera.clearGuide();
        live.setText(next.live?"실물 판 안내 일시 정지":"실물 판 안내 시작");
        turn.setText(String.format(Locale.KOREA,"아버지 %s · %s 안내 · 실제 판 %s 차례 · 관측 %d수",next.player.equals("white")?"백":"흑",CoachMode.stored(next.mode).label,next.fen.contains(" w ")?"백":"흑",next.plies));
        metrics.setText(String.format(Locale.KOREA,"안경 %s · 판 %s | 수신 %dms / 영상 %dms / 엔진 %dms",next.connected?"연결":"미연결",next.calibrated?"맞춤 완료":"맞춤 필요",next.captureMs,next.visionMs,next.engineMs));
    }
    @Override public void stopped(){
        if(bound){unbindService(connection);bound=false;}
        service=null;state=null;camera.clearGuide();status.setText("체스 연결 종료 · 다시 시작할 수 있습니다.");live.setText("실물 판 안내 시작");
    }
    private void tap(String square){
        if(state==null||state.busy||service==null)return;
        if(state.live){service.setLive(false);status.setText("라이브 정지 · 수동 입력은 판을 다시 맞춰야 합니다.");return;}
        if(selected.isEmpty()){selected=square;changed(state);return;}
        String prefix=selected+square;List<String> candidates=new ArrayList<>();
        for(String m:state.legal)if(m.startsWith(prefix))candidates.add(m);
        if(candidates.isEmpty()){selected=square;changed(state);return;}
        selected="";
        if(candidates.size()==1)service.move(candidates.get(0));
        else new AlertDialog.Builder(this).setTitle("승격 기물").setItems(new String[]{"퀸","룩","비숍","나이트"},(d,w)->use(()->service.move(prefix+new String[]{"q","r","b","n"}[w]))).show();
    }
    private void importFen(){
        if(service==null)return;EditText edit=new EditText(this);edit.setText(state==null?Game.START:state.fen);
        new AlertDialog.Builder(this).setTitle("FEN 설정 · 기보 새로 시작").setView(edit).setNegativeButton("취소",null)
            .setPositiveButton("검증 후 적용",(d,w)->use(()->service.setFen(edit.getText().toString().trim()))).show();
    }
    @Override public void calibration(byte[] jpeg){
        if(isFinishing()||isDestroyed()||service==null)return;
        final AlertDialog[] dialog={null};final CalibrationView[] view={null};
        String color=state==null?"white":state.player;
        view[0]=new CalibrationView(this,jpeg,()->{if(dialog[0]!=null){dialog[0].setTitle(CalibrationSteps.title(color,view[0].selectedCount()));dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(view[0].complete());}});
        dialog[0]=new AlertDialog.Builder(this).setTitle(CalibrationSteps.title(color,0))
            .setMessage("왼쪽·오른쪽은 아버지가 앉은 자리 기준입니다. a8 → h8 → h1 → a1 순서로 바깥 모서리를 누릅니다. 새 대국은 말을 초기 배치로 놓고, 중간 대국은 인식 보정에서 배치를 먼저 설정하세요.")
            .setView(view[0]).setNegativeButton("취소",(d,w)->use(()->service.discardCalibration()))
            .setNeutralButton("선택 초기화",null).setPositiveButton("이 실제 판으로 맞추기",(d,w)->use(()->service.calibrate(view[0].corners()))).create();
        dialog[0].setOnShowListener(d->{dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog[0].getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->view[0].reset());});
        dialog[0].setOnCancelListener(d->use(()->service.discardCalibration()));dialog[0].show();
    }
    @Override public void camera(byte[] jpeg,float[] corners,String move){if(visible&&!isDestroyed())camera.frame(jpeg,corners,move);}
    @Override public void diagnostics(String report){diagnosticStatus.setText(report);}
    @Override protected void onResume(){super.onResume();if(updater!=null)updater.resume();}
    @Override protected void onStart(){super.onStart();visible=true;if(service!=null)service.attach(this);}
    @Override protected void onStop(){visible=false;if(service!=null)service.detach(this);super.onStop();}
    private TextView label(LinearLayout parent,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,6,0,6);parent.addView(v);return v;}
    private LinearLayout row(LinearLayout p){LinearLayout r=new LinearLayout(this);p.addView(r);return r;}
    private Button button(LinearLayout p,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setOnClickListener(v->action.run());if(p.getOrientation()==LinearLayout.HORIZONTAL)p.addView(b,new LinearLayout.LayoutParams(0,-2,1));else p.addView(b);return b;}
    @Override protected void onDestroy(){if(updater!=null)updater.close();if(service!=null)service.detach(this);if(bound)unbindService(connection);camera.release();super.onDestroy();}
}
