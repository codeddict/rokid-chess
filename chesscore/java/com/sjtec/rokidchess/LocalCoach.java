package com.sjtec.rokidchess;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.*;
import com.sjtec.rokidchess.shared.CoachMode;

final class LocalCoach implements AutoCloseable {
    private final UciEngine engine;
    private final LinkedHashMap<String,Integer> losses=new LinkedHashMap<>();
    LocalCoach(String enginePath) {engine=new UciEngine(enginePath);}
    static final class Advice {
        final String uci,text,mode;
        Advice(String uci,String text,String mode){this.uci=uci;this.text=text;this.mode=mode;}
    }
    Advice analyse(Game game,String player,String mode) throws Exception {
        CoachMode selected=CoachMode.stored(mode);
        Board board=game.board;String ended=Game.terminal(board);
        if(ended!=null)return new Advice("","대국 종료\n"+ended,selected.key);
        Side color=player.equals("white")?Side.WHITE:Side.BLACK;
        if(board.getSideToMove()!=color)return new Advice("","아드님 차례\n실제 판에서 수를 두면\n다음 안내를 준비합니다.",selected.key);
        ArrayList<Integer> samples=new ArrayList<>();
        // Cached history avoids rescoring every old opponent move on every frame.
        Board previous=new Board();previous.loadFromFen(game.root);
        ArrayList<String> prefix=new ArrayList<>();
        for(String uci:game.moves) {
            if(previous.getSideToMove()!=color && prefix.size()>=game.moves.size()-16) {
                String key=game.root+" "+String.join(" ",prefix)+" "+uci;
                Integer loss=losses.get(key);
                if(loss==null) {
                    int best=engine.analyse(game.root,prefix,70,1,null).get(0).cp;
                    int actual=engine.analyse(game.root,prefix,70,1,uci).get(0).cp;
                    loss=Math.max(0,Math.min(500,best-actual));losses.put(key,loss);
                    if(losses.size()>256)losses.remove(losses.keySet().iterator().next());
                }
                samples.add(loss);
            }
            previous.doMove(Game.find(previous,uci));prefix.add(uci);
        }
        int start=Math.max(0,samples.size()-8),count=samples.size()-start;
        double average=count==0?35:samples.subList(start,samples.size()).stream().mapToInt(Integer::intValue).average().orElse(35);
        List<UciEngine.Line> candidates=engine.analyse(game.root,game.moves,300,selected==CoachMode.STRONG?1:Math.min(12,game.legal().size()),null);
        UciEngine.Line choice=choose(candidates,mode,average);
        Move m=Game.find(board,choice.move);
        if(m==null)throw new IllegalStateException("엔진 수를 합법 수와 대조하지 못했습니다.");
        String move=MoveGuide.describe(board,m,player);
        String eval=choice.mate==null?String.format(Locale.KOREA,"내 기준 %+.2f",choice.cp/100.0):"메이트 "+choice.mate;
        return new Advice(choice.move,"["+selected.label+"] "+move+"\n"+eval+"\n"+
            (count==0?"상대 수준 추정 중":"상대 최근 "+count+"수 · 손실 "+Math.round(average)+"cp"),selected.key);
    }
    static UciEngine.Line choose(List<UciEngine.Line> choices,String mode,double loss) {
        UciEngine.Line best=choices.get(0),choice=best;CoachMode selected=CoachMode.stored(mode);
        if(selected==CoachMode.STRONG||best.mate!=null||best.cp<=selected.targetCp)return best;
        double allowance=Math.min(selected.maxLoss,Math.max(selected==CoachMode.CLOSE?50:30,
            selected==CoachMode.CLOSE?loss+50:loss*.4+25));
        double target=Math.min(allowance,best.cp-selected.targetCp);
        double distance=Double.MAX_VALUE;
        for(UciEngine.Line c:choices)if(c.mate==null&&best.cp-c.cp<=allowance&&c.cp>=selected.floorCp) {
            double d=Math.abs(best.cp-c.cp-target);if(d<distance){distance=d;choice=c;}
        }
        return choice;
    }
    @Override public void close(){engine.close();}
}
