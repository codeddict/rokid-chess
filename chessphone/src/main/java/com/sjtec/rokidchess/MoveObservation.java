package com.sjtec.rokidchess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.*;

/** Conservative move matching and temporal consensus, independent of camera/native code. */
final class MoveObservation {
    static final class Match {
        final String uci,promotion; final String reason;
        Match(String uci,String promotion,String reason){this.uci=uci;this.promotion=promotion;this.reason=reason;}
    }
    private String pending="";private long since,last;private int repeats;
    void reset(){pending="";repeats=0;since=last=0;}
    boolean stable(String move,long now) {
        if(!pending.equals(move)||now-last>2500){pending=move;since=now;repeats=1;last=now;return false;}
        repeats++;last=now;return repeats>=2 && now-since>=250;
    }
    static Match match(Board board,double[] change) {
        if(change.length!=64)throw new IllegalArgumentException("64 square scores required");
        double[] sorted=change.clone();Arrays.sort(sorted);
        double floor=(sorted[31]+sorted[32])*.5;
        double threshold=Math.max(.038,floor*3+.012);
        int changed=0;double total=0;
        for(double score:change){if(!Double.isFinite(score))return new Match("","","프레임 오류");if(score>threshold)changed++;total+=Math.max(0,score-threshold);}
        if(floor>.055)return new Match("","","손 가림 / 머리 움직임 · 대기");
        if(changed==0)return new Match("","","변화 없음");
        if(changed==1)return new Match("","","기물 이동 중 · 놓을 때까지 대기");
        if(changed>7)return new Match("","","손 가림 / 머리 움직임 · 대기");
        char[] before=Game.pieces(board);ArrayList<String> candidates=new ArrayList<>();
        for(Move move:board.legalMoves()) {
            Board after=board.clone();after.doMove(move,true);char[] pieces=Game.pieces(after);
            double explained=0;boolean valid=true;int count=0;
            for(int i=0;i<64;i++)if(before[i]!=pieces[i]) {
                count++;if(change[i]<=threshold){valid=false;break;}explained+=Math.max(0,change[i]-threshold);
            }
            // Reject unexplained motion outside every expected changed square.
            if(valid && count>=2 && explained/Math.max(total,.001)>.82)candidates.add(move.toString());
        }
        if(candidates.size()==1)return new Match(candidates.get(0),"","");
        if(candidates.size()>1) {
            String first=candidates.get(0);
            if(first.length()==5 && candidates.stream().allMatch(s->s.length()==5&&s.startsWith(first.substring(0,4))))
                return new Match("",first.substring(0,4),"승격 기물 선택 필요");
        }
        return new Match("","","합법적인 한 수로 확정하지 못했습니다.");
    }
}
