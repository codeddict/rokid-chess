package com.sjtec.rokidchess;

import android.content.*;
import android.os.*;
import org.json.*;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import com.sjtec.rokidchess.shared.CoachMode;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** All game/vision/UCI work is local to the glasses process. */
final class StandaloneCoach implements AutoCloseable {
    static final class State {
        final boolean ready,busy,live,calibrated;final String player,mode,advice,status;final int plies;
        State(boolean r,boolean b,boolean l,boolean c,String p,String m,String a,String s,int n){ready=r;busy=b;live=l;calibrated=c;player=p;mode=m;advice=a;status=s;plies=n;}
    }
    interface Listener {void state(State state);}
    private final Context context;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Object commit=new Object();
    private final Game game=new Game();private LocalCoach engine;private LiveTracker tracker;
    private volatile boolean closed,live;private volatile int generation;private String player="white",mode="close",advice="",message="안경 엔진 준비 중…";private boolean ready,busy=true;
    private volatile State state=new State(false,true,false,false,"white","close","","안경 엔진 준비 중…",0);
    StandaloneCoach(Context context,Listener listener){this.context=context;this.listener=listener;worker.execute(()->{
        try{if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV 초기화 실패");tracker=new LiveTracker();engine=new LocalCoach(new File(context.getApplicationInfo().nativeLibraryDir,"libstockfish.so").getAbsolutePath());
            SharedPreferences prefs=context.getSharedPreferences("standalone_chess",0);player=prefs.getString("player","white");mode=CoachMode.stored(prefs.getString("mode","close")).key;
            ArrayList<String> moves=new ArrayList<>();JSONArray history=new JSONArray(prefs.getString("moves","[]"));for(int i=0;i<history.length();i++)moves.add(history.getString(i));
            try{game.load(prefs.getString("root",Game.START),moves);}catch(Exception e){game.load(Game.START,List.of());}
            analyse();ready=true;message="단독 실행 준비 완료 · 탭해서 새 대국/판 맞추기";
        }catch(Exception e){message="안경 초기화 실패: "+detail(e);}finally{busy=false;publish();}
    });}
    State state(){return state;}
    private void publish(){State next=new State(ready,busy,live,tracker!=null&&tracker.calibrated(),player,mode,advice,message,game.moves.size());state=next;main.post(()->{if(!closed)listener.state(next);});}
    private void analyse() throws Exception{advice=engine.analyse(game,player,mode).text;}
    private void save(){context.getSharedPreferences("standalone_chess",0).edit().putString("player",player).putString("mode",mode).putString("root",game.root).putString("moves",new JSONArray(game.moves).toString()).apply();}
    void pause(){synchronized(commit){live=false;generation++;}worker.execute(()->{message="안내 일시 정지";publish();});}
    void start(){if(!state.ready||state.busy||!state.calibrated){messageOnMain("먼저 판 촬영·맞추기를 완료하세요.");return;}synchronized(commit){live=true;generation++;}worker.execute(()->{message="판 확인 중 · 실제 판을 바라보세요.";publish();});}
    void mode(String key){if(!state.ready||state.busy)return;worker.execute(()->{busy=true;publish();try{mode=CoachMode.require(key).key;analyse();save();message=CoachMode.stored(mode).label+" 적용 완료";}catch(Exception e){message=detail(e);}finally{busy=false;publish();}});}
    void newGame(boolean black,Runnable after){pause();worker.execute(()->{if(!ready)return;busy=true;publish();try{game.load(Game.START,List.of());player=black?"black":"white";tracker.reset();analyse();save();message="말을 초기 배치에 놓고 판을 촬영하세요.";}catch(Exception e){message=detail(e);}finally{busy=false;publish();main.post(()->{if(!closed)after.run();});}});}
    void undo(){pause();worker.execute(()->{if(!ready)return;try{game.undo();tracker.reset();analyse();save();message="한 수 되돌림 · 실제 말도 되돌리고 판을 다시 맞추세요.";}catch(Exception e){message=detail(e);}publish();});}
    void calibrate(byte[] jpeg,double[][] coordinates,Runnable done){if(!ready)return;pause();worker.execute(()->{busy=true;publish();Mat raw=null;boolean ok=false;
        try{raw=decode(jpeg);Point[] corners=new Point[4];for(int i=0;i<4;i++)corners[i]=new Point(coordinates[i][0],coordinates[i][1]);tracker.calibrate(raw,corners);message="판 맞추기 완료 · 메뉴에서 안내 시작";ok=true;
        }catch(Exception e){tracker.reset();message="판 맞추기 실패: "+detail(e);}finally{if(raw!=null)raw.release();busy=false;publish();if(ok)main.post(()->{if(!closed)done.run();});}
    });}
    void observe(byte[] jpeg){if(!live||state.busy||closed)return;int epoch=generation;worker.execute(()->{if(!live||epoch!=generation)return;busy=true;publish();Mat raw=null;
        try{raw=decode(jpeg);try(LiveTracker.Frame frame=tracker.observe(raw,game.board,SystemClock.elapsedRealtime())){
            if(!live||epoch!=generation)return;
            if(frame.stable){synchronized(commit){if(!live||epoch!=generation)return;game.move(frame.move);tracker.accept(frame);save();}analyse();message="자동 반영: "+frame.move;if(Game.terminal(game.board)!=null)live=false;}
            else {message=frame.reason;if(!frame.promotion.isEmpty()){live=false;message="승격 확인 필요 · 판과 기보를 확인하세요.";}}
        }}catch(Exception e){live=false;tracker.reset();message="관측 중지 · 판을 다시 맞추세요: "+detail(e);}finally{if(raw!=null)raw.release();busy=false;publish();}
    });}
    private void messageOnMain(String text){worker.execute(()->{message=text;publish();});}
    private static Mat decode(byte[] bytes){android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>2_000_000)throw new IllegalArgumentException("사진 해상도를 확인하세요.");return CameraImage.decode(bytes);}

    private static String detail(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.substring(0,Math.min(130,s.length()));}
    public void close(){closed=true;live=false;generation++;worker.execute(()->{if(tracker!=null)tracker.close();if(engine!=null)engine.close();});worker.shutdown();main.removeCallbacksAndMessages(null);}
}
