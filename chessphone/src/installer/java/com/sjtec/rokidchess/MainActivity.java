package com.sjtec.rokidchess;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Pair;
import android.widget.*;
import com.rokid.sprite.aiapp.externalapp.auth.*;

/** Phone is only an installer/updater; no camera, engine, board or runtime link. */
public final class MainActivity extends Activity {
    private TextView status,updates;private SelfUpdater updater;private GlassInstaller installer;private boolean updateGlass;
    public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);content.setPadding(pad,pad,pad,pad);
        label(content,"안경 단독 체스 · v"+BuildConfig.VERSION_NAME,24);
        label(content,"대국 중에는 휴대폰 연결이 필요 없습니다.\n촬영·모서리 맞추기·추천 계산은 안경에서 실행합니다. 이 휴대폰 앱은 설치와 업데이트에만 사용합니다.",17);
        updates=label(content,"GitHub에서 업데이트합니다.",14);updater=new SelfUpdater(this,updates::setText);
        button(content,"앱 업데이트 확인 / 설치",updater::check);
        status=label(content,"안경 앱 설치 후 안경의 앱 목록에서 ‘실물 체스 도우미’를 여세요.",16);installer=new GlassInstaller(this,status::setText);
        button(content,"안경 앱 설치 / 업데이트",()->authorize(true));button(content,"안경 앱 열기",()->authorize(false));
        button(content,"설치 연결 종료",()->{installer.disconnect();status.setText("설치 연결 종료. 안경 앱 목록에서 단독 실행하세요.");});
        label(content,"안경에서: 한 번 탭 → 메뉴. 스와이프 → 선택.\n새 대국(백/흑) → 판 촬영·맞추기 → 네 모서리 확인 → 안내 시작.\n안경 카메라 권한은 안경 화면에서 허용해야 합니다.",16);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
    }
    private void authorize(boolean install){updateGlass=install;if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},12);return;}
        try{if(!AuthorizationHelper.INSTANCE.isRequiredHiRokidInstalled(this)){status.setText("설치할 때는 Hi Rokid가 필요합니다.");return;}
            Pair<Integer,Intent> cached=AuthorizationHelper.INSTANCE.requestAuthorization(this,new GlassPermission[]{GlassPermission.DEVICE_MANAGE},7001);if(cached!=null)auth(cached.first,cached.second);
        }catch(Exception e){status.setText("Hi Rokid 설치 인증 요청 실패");}}
    private void auth(int code,Intent data){try{AuthResult r=AuthorizationHelper.INSTANCE.parseAuthorizationResult(code,data);if(r instanceof AuthResult.AuthSuccess)installer.connect(((AuthResult.AuthSuccess)r).getToken(),updateGlass);else status.setText("설치 인증이 취소되었습니다.");}catch(Exception e){status.setText("설치 인증 응답 오류");}}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7001)auth(result,data);}
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==12&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)authorize(updateGlass);}
    protected void onResume(){super.onResume();if(updater!=null)updater.resume();}
    protected void onDestroy(){if(updater!=null)updater.close();if(installer!=null)installer.close();super.onDestroy();}
    private TextView label(LinearLayout parent,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,12,0,12);parent.addView(v);return v;}
    private void button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());parent.addView(b);}
}
