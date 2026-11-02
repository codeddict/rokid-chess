package com.sjtec.rokidchess;

import android.app.*;
import android.content.*;
import android.os.*;
import com.github.bhlangonijr.chesslib.Side;
import org.json.JSONArray;
import org.json.JSONObject;
import com.sjtec.rokidchess.shared.*;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Owns the game, camera pipeline and engine while the phone screen is off. */
public final class ChessService extends Service implements Glasses.Listener {
    interface Listener {void changed(Snapshot state);void calibration(byte[] jpeg);void camera(byte[] jpeg,float[] corners,String move);void diagnostics(String report);void stopped();}
    static final class Snapshot {
        final String fen,player,mode,advice,hint,status,promotion;
        final List<String> legal;final int plies;final boolean busy,live,calibrated,connected;
        final long captureMs,visionMs,engineMs;
        Snapshot(String fen,String player,String mode,String advice,String hint,String status,String promotion,List<String> legal,
                 int plies,boolean busy,boolean live,boolean calibrated,boolean connected,long captureMs,long visionMs,long engineMs){
            this.fen=fen;this.player=player;this.mode=mode;this.advice=advice;this.hint=hint;this.status=status;this.promotion=promotion;
            this.legal=Collections.unmodifiableList(new ArrayList<>(legal));this.plies=plies;this.busy=busy;this.live=live;this.calibrated=calibrated;
            this.connected=connected;this.captureMs=captureMs;this.visionMs=visionMs;this.engineMs=engineMs;
        }
    }
    public final class LocalBinder extends Binder {ChessService service(){return ChessService.this;}}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object commitLock=new Object();
    private final Game game=new Game();
    private LocalCoach coach;private LiveTracker tracker;private Glasses glasses;private PowerManager.WakeLock wake;
    private Listener listener;private volatile boolean dead,live;private boolean busy,connected,calibrationRequested;
    private volatile int generation;
    private String player="white",mode="close",advice="준비 중",hint="",message="휴대폰 엔진 준비 중",promotion="";
    private final String controlSession=UUID.randomUUID().toString();private long stateSequence;
    private ControlRequest queuedControl,runningControl;
    private final LinkedHashMap<String,ControlReply> completedControls=new LinkedHashMap<>();
    private static final class ControlReply {
        final ControlRequest request;final boolean ok;final String error;
        ControlReply(ControlRequest request,boolean ok,String error){this.request=request;this.ok=ok;this.error=error;}
    }
    private volatile byte[] calibrationImage;private String calibrationFen="";
    private long captureMs,visionMs,engineMs,frameStarted;
    private int frameFailures;
    private long calibrationStarted,calibrationRound;
    private final Runnable calibrationPoll=new Runnable(){public void run(){
        if(dead||!calibrationRequested)return;
        if(SystemClock.elapsedRealtime()-calibrationStarted>PhotoRequest.TIMEOUT_MS){frameFailed("이전 촬영 응답을 기다리는 중입니다. 안경 연결을 다시 누르세요.");return;}
        if(!glasses.requestFrame()&&calibrationRequested)main.postDelayed(this,300);
    }};
    private String adviceMode="",diagnosticId="",probeReport="안경 응답: 아직 점검하지 않았습니다.",engineReport="";
    private boolean diagnosticQueued,diagnosticRunning;
    private volatile Snapshot snapshot=new Snapshot(Game.START,"white","balanced","준비 중","","휴대폰 엔진 준비 중","",List.of(),0,true,false,false,false,0,0,0);
    private final Runnable poll=()->{if(!dead&&live&&!busy&&connected){frameStarted=SystemClock.elapsedRealtime();if(!glasses.requestFrame())schedule(400);}};

    @Override public void onCreate(){
        super.onCreate();foreground();glasses=new Glasses(this,this);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RokidChess:live");wake.setReferenceCounted(false);
        busy=true;
        worker.execute(()->{
            try{
                if(!OpenCVLoader.initLocal())throw new IllegalStateException("영상 처리 라이브러리를 열지 못했습니다.");
                tracker=new LiveTracker();coach=new LocalCoach(new File(getApplicationInfo().nativeLibraryDir,"libstockfish.so").getAbsolutePath());
                SharedPreferences p=getSharedPreferences("chess_live",MODE_PRIVATE);
                player=p.getString("player","white");mode=CoachMode.stored(p.getString("mode","close")).key;
                List<String> moves=new ArrayList<>();JSONArray array=new JSONArray(p.getString("moves","[]"));
                for(int i=0;i<array.length();i++)moves.add(array.getString(i));
                try{game.load(p.getString("root",Game.START),moves);}catch(Exception e){game.load(Game.START,List.of());message="저장된 기보 오류 · 새 대국으로 복구";}
                analyse();message="휴대폰 준비 완료 · 안경을 연결하세요.";
            }catch(Exception e){message="초기화 실패: "+safe(e);}
            finish(null);
        });
    }
    private void foreground(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("chess_live","로키드 체스 라이브",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ChessService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"chess_live").setSmallIcon(com.sjtec.rokidchess.R.drawable.chess_icon)
            .setContentTitle("로키드 체스 · 휴대폰에서 처리").setContentText("라이브 추적과 안경 연결 유지 중")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"종료",stop).build()).build();
        startForeground(72,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){shutdown();stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&intent.hasExtra("token")){
            setLive(false);glasses.connect(intent.getStringExtra("token"),intent.getBooleanExtra("install",false));
        }
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return new LocalBinder();}
    Snapshot snapshot(){return snapshot;}
    void attach(Listener l){if(dead){if(l!=null)l.stopped();return;}listener=l;if(l!=null){l.changed(snapshot);l.diagnostics(diagnostics());byte[] photo=calibrationImage;if(photo!=null)l.calibration(photo);}}
    void detach(Listener l){if(listener==l)listener=null;}
    private Snapshot build(){return new Snapshot(game.board.getFen(),player,mode,advice,hint,message,promotion,game.legal(),game.moves.size(),false,live,
        tracker!=null&&tracker.calibrated(),connected,captureMs,visionMs,engineMs);}
    private void finish(byte[] calibration){
        finish(calibration,null);
    }
    private void finish(byte[] calibration,ControlReply reply){
        Snapshot next=build();
        main.post(()->{
            if(dead)return;busy=false;
            snapshot=new Snapshot(next.fen,next.player,next.mode,next.advice,next.hint,message,next.promotion,next.legal,next.plies,false,live,next.calibrated,connected,next.captureMs,next.visionMs,next.engineMs);
            if(listener!=null){listener.changed(snapshot);if(calibration!=null)listener.calibration(calibration);}
            reportDiagnostics();
            if(reply!=null){runningControl=null;complete(reply);}else sendState("",true,"");
            if(queuedControl!=null){ControlRequest waiting=queuedControl;queuedControl=null;control(waiting);}
            else if(diagnosticQueued)runDiagnostics();
            else if(live)schedule(250);
        });
    }
    private void save(){getSharedPreferences("chess_live",MODE_PRIVATE).edit().putString("root",game.root).putString("moves",new JSONArray(game.moves).toString())
        .putString("player",player).putString("mode",mode).apply();}
    private interface Task {void run() throws Exception;}
    private void command(Task task,boolean resetVision){
        command(task,resetVision,null);
    }
    private void command(Task task,boolean resetVision,ControlRequest request){
        if(dead||busy||coach==null||tracker==null){status("처리 완료 후 다시 선택하세요.");return;}
        busy=true;generation++;main.removeCallbacks(poll);glasses.cancelFrame();
        worker.execute(()->{
            boolean ok=true;String error="";
            try{task.run();}
            catch(Exception e){message=safe(e);ok=false;error=message;}
            finally{if(resetVision){tracker.reset();calibrationImage=null;live=false;}save();}
            finish(null,request==null?null:new ControlReply(request,ok,error));main.post(()->{updateWake();show(advice);});
        });
    }
    void move(String uci){command(()->{game.move(uci);hint="";promotion="";analyse();message="수 반영: "+uci+" · 판을 다시 맞춰 주세요.";},true);}
    void undo(){command(()->{game.undo();promotion="";analyse();message="한 수 되돌림 · 판을 다시 맞춰 주세요.";},true);}
    void resetGame(){command(()->{game.load(Game.START,List.of());promotion="";analyse();message="새 대국 · 판을 맞춰 주세요.";},true);}
    void setFen(String fen){command(()->{game.load(fen,List.of());promotion="";analyse();message="FEN 적용 · 판을 맞춰 주세요.";},true);}
    void setMode(String value){command(()->applyMode(CoachMode.require(value)),false);}
    private void applyMode(CoachMode selected) throws Exception{
        long started=SystemClock.elapsedRealtime();LocalCoach.Advice next=coach.analyse(game,player,selected.key);
        mode=selected.key;advice=next.text;hint=next.uci;adviceMode=next.mode;engineMs=SystemClock.elapsedRealtime()-started;message=selected.label+" 모드 적용";
    }
    @Override public void control(ControlRequest request){
        if(dead)return;
        if(request.operation.equals("state.get")){sendState(request.id,true,"");return;}
        ControlReply old=completedControls.get(request.id);
        if(old!=null){sendState(request.id,old.request.fingerprint().equals(request.fingerprint())&&old.ok,old.request.fingerprint().equals(request.fingerprint())?old.error:"같은 요청 ID의 설정 충돌");return;}
        if(runningControl!=null&&runningControl.id.equals(request.id)){
            if(!runningControl.fingerprint().equals(request.fingerprint()))sendState(request.id,false,"같은 요청 ID의 설정 충돌");return;
        }
        if(request.operation.equals("guide.set")&&!request.enabled){setLive(false);complete(new ControlReply(request,true,""));return;}
        if(busy){
            if(queuedControl!=null&&queuedControl.id.equals(request.id)){
                if(!queuedControl.fingerprint().equals(request.fingerprint()))sendState(request.id,false,"같은 요청 ID의 설정 충돌");return;
            }
            if(queuedControl!=null)complete(new ControlReply(queuedControl,false,"새 설정 선택으로 대체되었습니다."));
            queuedControl=request;return;
        }
        if(coach==null||tracker==null){complete(new ControlReply(request,false,"휴대폰 엔진 준비 상태를 확인하세요."));return;}
        if(request.operation.equals("mode.set")){
            runningControl=request;command(()->applyMode(CoachMode.require(request.mode)),false,request);
        }else{
            if(!snapshot.calibrated||!connected){complete(new ControlReply(request,false,"휴대폰에서 실제 판 위치를 먼저 맞춰 주세요."));return;}
            setLive(true);complete(new ControlReply(request,live,live?"":"안내를 시작하지 못했습니다."));
        }
    }
    private void complete(ControlReply reply){
        completedControls.put(reply.request.id,reply);if(completedControls.size()>64)completedControls.remove(completedControls.keySet().iterator().next());
        sendState(reply.request.id,reply.ok,reply.error);
        if(reply.ok&&reply.request.operation.equals("mode.set"))main.postDelayed(this::requestProbe,350);
    }
    private void sendState(String ack,boolean ok,String error){
        if(dead||glasses==null)return;
        try{Snapshot s=snapshot;
            JSONObject response=new JSONObject().put("v",1).put("session",controlSession).put("seq",++stateSequence)
                .put("phone_version",BuildConfig.VERSION_NAME).put("phone_code",BuildConfig.VERSION_CODE)
                .put("mode",CoachMode.stored(s.mode).key).put("live",live).put("calibrated",s.calibrated)
                .put("ack",ack).put("ok",ok).put("error",error).put("advice_mode",adviceMode).put("move",s.hint);
            glasses.state(response.toString());
        }catch(Exception ignored){}
    }
    void changePlayer(){command(()->{player=player.equals("white")?"black":"white";analyse();message="내 색 변경";},false);}
    void recommend(){command(()->{analyse();message="휴대폰 분석 완료";},false);}
    private void analyse() throws Exception{
        long started=SystemClock.elapsedRealtime();LocalCoach.Advice next=coach.analyse(game,player,mode);
        advice=next.text;hint=next.uci;adviceMode=next.mode;engineMs=SystemClock.elapsedRealtime()-started;
    }
    String diagnostics(){
        Snapshot s=snapshot;
        return "휴대폰 v"+BuildConfig.VERSION_NAME+"\n"+probeReport+"\n현재 설정: "+CoachMode.stored(s.mode).label+
            "\n최근 엔진 분석 모드: "+(adviceMode.isEmpty()?"아직 없음":CoachMode.stored(adviceMode).label)+"\n"+glasses.cameraResult()+"\n"+engineReport;
    }
    private void reportDiagnostics(){if(listener!=null)listener.diagnostics(diagnostics());}
    private void requestProbe(){
        if(dead)return;diagnosticId=UUID.randomUUID().toString();String id=diagnosticId;
        probeReport=glasses.probe(id)?"안경의 실행 버전·표시 모드 응답 대기 중…":"안경 미연결 · 안경 연결 후 다시 점검하세요.";reportDiagnostics();
        main.postDelayed(()->{if(!dead&&diagnosticId.equals(id)&&probeReport.contains("응답 대기")){probeReport="안경 응답 없음 · 안경 앱도 v"+BuildConfig.VERSION_NAME+"으로 업데이트하고 연결하세요.";reportDiagnostics();}},6000);
    }
    void runDiagnostics(){
        if(dead||diagnosticRunning)return;
        if(calibrationRequested){diagnosticQueued=true;engineReport="로키드 사진 수신 후 점검합니다.";reportDiagnostics();return;}
        if(busy){diagnosticQueued=true;engineReport="현재 처리 후 점검합니다.";reportDiagnostics();return;}
        if(coach==null){engineReport="엔진 초기화 상태를 확인하세요.";reportDiagnostics();return;}
        diagnosticQueued=false;diagnosticRunning=true;busy=true;generation++;main.removeCallbacks(poll);glasses.cancelFrame();
        requestProbe();
        engineReport="실제 Stockfish로 세 모드 샘플 분석 중…";status("모드 점검 중 · 실제 판 안내는 잠시 대기합니다.");show("세 모드 샘플 점검 중\n실물 판 안내는 잠시 대기합니다.");reportDiagnostics();
        worker.execute(()->{
            String result;try{result=ModeDiagnostics.run(coach);}catch(Exception e){result="엔진 점검 실패: "+safe(e);}
            String verified=result;
            main.post(()->{if(dead)return;diagnosticRunning=false;engineReport=verified;reportDiagnostics();});
            finish(null);
        });
    }
    @Override public void probe(JSONObject response){
        if(dead)return;
        try{
            if(response.getInt("v")!=1||!diagnosticId.equals(response.getString("id")))return;
            String version=response.getString("glasses_version"),phone=response.getString("phone_version"),key=response.getString("mode");
            if(!version.matches("[0-9A-Za-z._-]{1,40}")||phone.length()>40)return;
            CoachMode actual=CoachMode.require(key);
            boolean synced=response.getBoolean("state_synced"),same=actual.key.equals(CoachMode.stored(snapshot.mode).key);
            String header=response.getString("header");
            boolean versionMatches=version.equals(BuildConfig.VERSION_NAME)&&phone.equals(BuildConfig.VERSION_NAME)&&header.contains("안경 v"+version)&&header.contains("폰 v"+phone);
            probeReport="안경 실제 응답 v"+version+" / 안경이 확인한 폰 v"+phone+"\n안경 표시 모드: "+actual.label+
                (synced&&same?" · 휴대폰 설정 일치":" · 동기화 확인 필요")+"\n버전 표시 문구: "+(versionMatches?"응답 일치":"버전/문구 확인 필요");reportDiagnostics();
        }catch(Exception ignored){}
    }
    void setLive(boolean enabled){
        if(enabled&&(busy||!snapshot.calibrated||!connected)){status("안경 연결과 판 맞추기를 먼저 완료하세요.");return;}
        synchronized(commitLock){live=enabled;generation++;}
        calibrationRequested=false;main.removeCallbacks(calibrationPoll);
        main.removeCallbacks(poll);glasses.cancelFrame();glasses.stream(enabled);updateWake();
        status(enabled?"라이브 시작 · 양쪽 수를 자동 추적합니다.":"라이브 일시 정지");
        if(enabled){show("라이브 시작\n체스판을 바라보세요.");schedule(0);}else show(advice);
    }
    private void updateWake(){
        if(dead)return;
        if(live){if(!wake.isHeld())wake.acquire(6*60*60*1000L);}
        else {if(wake.isHeld())wake.release();glasses.stream(false);}
    }
    void requestCalibration(){
        if(busy||!connected){status("안경 연결/처리 상태를 확인하세요.");return;}
        if(calibrationRequested){status("로키드 카메라 사진을 기다리는 중입니다.");return;}
        setLive(false);glasses.stream(true);calibrationRequested=true;calibrationImage=null;
        calibrationStarted=SystemClock.elapsedRealtime();status("로키드 카메라로 촬영 중 · 안경으로 판 전체를 바라보세요.");
        glasses.show("로키드 카메라 촬영 중\n실제 체스판 전체를 바라보세요.",0);
        long round=++calibrationRound;
        main.postDelayed(()->{if(!dead&&calibrationRequested&&round==calibrationRound){glasses.cancelFrame();frameFailed("로키드 촬영 대기 시간 초과 · 안경 연결을 다시 누르세요.");}},PhotoRequest.TIMEOUT_MS*2);
        // Give an older glasses APK a chance to release its direct Camera2 session.
        main.postDelayed(calibrationPoll,300);
    }
    void discardCalibration(){calibrationImage=null;calibrationRequested=false;main.removeCallbacks(calibrationPoll);glasses.cancelFrame();}
    void calibrate(Point[] corners){
        byte[] jpeg=calibrationImage;if(jpeg==null){status("판 사진을 다시 받으세요.");return;}
        command(()->{
            if(!calibrationFen.equals(game.board.getFen()))throw new IllegalArgumentException("기보가 바뀌었습니다. 판을 다시 촬영하세요.");
            Mat raw=decode(jpeg);try{tracker.calibrate(raw,corners);}finally{raw.release();}
            calibrationImage=null;message="판 맞추기 완료 · 실물 판 안내 시작을 누르세요.";
        },false);
    }
    private static Mat decode(byte[] jpeg){
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>2_000_000)throw new IllegalArgumentException("영상 해상도 오류");
        MatOfByte bytes=new MatOfByte(jpeg);try{
            Mat image=Imgcodecs.imdecode(bytes,Imgcodecs.IMREAD_COLOR);
            if(image.empty())throw new IllegalArgumentException("카메라 프레임을 읽지 못했습니다.");return image;
        }finally{bytes.release();}
    }
    @Override public void frame(byte[] jpeg,long elapsed){
        if(dead||busy)return;
        boolean calibration=calibrationRequested;calibrationRequested=false;
        if(!calibration&&!live)return;
        if(elapsed>(calibration?PhotoRequest.TIMEOUT_MS:6000)){frameFailed("오래된 사진 폐기 · 안경 연결 속도를 확인하세요.");return;}
        busy=true;int epoch=generation;captureMs=elapsed;frameFailures=0;
        worker.execute(()->{
            Mat raw=null;byte[] preview=null;float[] physicalCorners=null;
            try{
                raw=decode(jpeg);
                if(calibration){calibrationFen=game.board.getFen();calibrationImage=jpeg;preview=jpeg;message="로키드 사진 수신 완료 · a8 → h8 → h1 → a1 모서리를 누르세요.";main.post(()->glasses.stream(false));}
                else if(live&&epoch==generation){
                    long started=SystemClock.elapsedRealtime();
                    try(LiveTracker.Frame observation=tracker.observe(raw,game.board,started)){
                        physicalCorners=observation.cameraCorners();
                        visionMs=SystemClock.elapsedRealtime()-started;
                        if(!live||epoch!=generation)return;
                        if(observation.stable){
                            synchronized(commitLock){
                                if(dead||!live||epoch!=generation)return;
                                game.move(observation.move);hint="";tracker.accept(observation);save();
                            }
                            main.post(()->show("수 확인: "+observation.move+"\n다음 수 분석 중"));
                            analyse();message="자동 반영: "+observation.move;frameFailures=0;
                            if(Game.terminal(game.board)!=null)live=false;
                            main.post(()->show(advice));
                        }else{
                            message=observation.reason;promotion=observation.promotion;
                            String hud=observation.reason.equals("변화 없음")?advice:"자동 추적\n"+observation.reason;
                            main.post(()->show(hud));
                            if(!promotion.isEmpty()){live=false;message="승격을 수동 입력한 뒤 판을 다시 맞추세요: "+promotion;}
                        }
                    }
                }
            }catch(Exception e){tracker.reset();live=false;save();message="자동 반영 보류 · 판을 다시 맞추세요: "+safe(e);main.post(()->show(message));}
            finally{
                boolean validImage=raw!=null&&!raw.empty();
                if(raw!=null)raw.release();finish(preview);main.post(this::updateWake);
                if(validImage){
                    float[] corners=physicalCorners;String physicalMove=GuidePolicy.confirmed(live,tracker.calibrated(),message)?hint:"";
                    main.post(()->{if(!dead&&epoch==generation&&listener!=null)listener.camera(jpeg,corners,physicalMove);});
                }
            }
        });
    }
    @Override public void frameFailed(String error){
        if(dead)return;calibrationRequested=false;main.removeCallbacks(calibrationPoll);if(!live)glasses.stream(false);frameFailures++;status(error);show("관측 대기\n"+error);
        if(frameFailures>=5){setLive(false);status("프레임 수신 실패 5회 · 안경 앱/카메라 권한을 확인하고 다시 시작하세요.");}
        else if(live)schedule(700);
    }
    private void schedule(long delay){main.removeCallbacks(poll);if(live&&!dead)main.postDelayed(poll,delay);}
    private void show(String text){
        if(dead)return;
        if(!live)text="실물 체스 도우미\n안경으로 실제 판을 바라보고\n휴대폰에서 판을 맞춰 주세요.";
        else if(text.equals(advice)&&!GuidePolicy.confirmed(live,snapshot.calibrated,message))text="실물 판 확인 중\n안정된 관측 후 안내합니다.";
        glasses.show(text,live?4500:0);
    }
    @Override public void connected(boolean ready){
        connected=ready;if(!ready)setLive(false);status(ready?"안경 연결 완료":"안경 연결 대기");
        if(ready){sendState("",true,"");main.postDelayed(this::requestProbe,500);}
        else{probeReport="안경 연결이 끊겼습니다. 다시 연결하고 점검하세요.";reportDiagnostics();}
    }
    @Override public void status(String text){
        if(dead)return;message=text;Snapshot s=snapshot;
        snapshot=new Snapshot(s.fen,s.player,s.mode,s.advice,s.hint,text,s.promotion,s.legal,s.plies,busy,live,s.calibrated,connected,captureMs,visionMs,engineMs);
        if(listener!=null)listener.changed(snapshot);
        sendState("",true,"");
    }
    @Override public void cameraStatus(String text){reportDiagnostics();status(text);}
    private static String safe(Exception e){String text=e.getMessage();return text==null?e.getClass().getSimpleName():text.substring(0,Math.min(180,text.length()));}
    private void shutdown(){
        if(dead)return;
        synchronized(commitLock){dead=true;live=false;generation++;}
        main.removeCallbacksAndMessages(null);glasses.stream(false);glasses.close();
        if(wake.isHeld())wake.release();if(coach!=null)coach.close();
        worker.execute(()->{if(tracker!=null)tracker.close();if(coach!=null)coach.close();});worker.shutdown();
        stopForeground(STOP_FOREGROUND_REMOVE);
        Listener current=listener;listener=null;if(current!=null)current.stopped();
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
}
