package com.sjtec.rokidchess.glasses;

import com.sjtec.rokidchess.shared.CoachMode;
import java.util.*;

/** Menu selection is provisional until the phone returns authoritative state and an ACK. */
final class GlassMenu {
    enum Screen {GUIDE,MENU,VERSION}
    Screen screen=Screen.GUIDE;int selection;
    CoachMode mode=CoachMode.CLOSE;boolean live,calibrated,synced;
    String phoneVersion="미확인",notice="",pendingId="",session="";
    private long sequence=-1;private final Set<String> retiredSessions=new HashSet<>();
    private final String glassesVersion;
    GlassMenu(String version){glassesVersion=version;}
    void open(){screen=Screen.MENU;selection=mode.ordinal();notice="";}
    void back(){screen=Screen.GUIDE;}
    void next(int direction){if(screen==Screen.MENU)selection=Math.floorMod(selection+direction,5);}
    void waiting(String id){pendingId=id;notice="휴대폰에 적용 중…";}
    void timeout(String id){if(pendingId.equals(id)){pendingId="";notice="응답 시간 초과 · 휴대폰 연결/버전을 확인하세요.";}}
    boolean update(String incomingSession,long seq,String key,boolean running,boolean ready,String version,String ack,boolean ok,String error){
        CoachMode actual=CoachMode.require(key);
        if(!session.equals(incomingSession)){
            if(retiredSessions.contains(incomingSession))return false;
            if(!session.isEmpty())retiredSessions.add(session);
            if(retiredSessions.size()>16)retiredSessions.clear();
            session=incomingSession;sequence=-1;
        }
        if(seq<=sequence)return false;
        sequence=seq;mode=actual;live=running;calibrated=ready;phoneVersion=version;synced=true;
        if(!pendingId.isEmpty()&&pendingId.equals(ack)){
            pendingId="";notice=ok?mode.label+" 적용 완료":error;
            if(ok)screen=Screen.GUIDE;
        }
        return true;
    }
    String body(){
        if(screen==Screen.VERSION)return "버전 정보\n안경: "+glassesVersion+"\n휴대폰: "+phoneVersion+"\n현재 모드: "+(synced?mode.label:"연결 확인 중")+"\n두 번 탭: 안내로 돌아가기";
        StringBuilder text=new StringBuilder("안경에서 설정\n");
        for(int i=0;i<5;i++)text.append(i==selection?"▶ ":"   ").append(i<3?CoachMode.values()[i].title:i==3?(live?"안내 일시 정지":"안내 시작"):"버전 정보").append('\n');
        return text.append(notice.isEmpty()?"스와이프: 이동 · 탭: 선택":notice).toString();
    }
}
