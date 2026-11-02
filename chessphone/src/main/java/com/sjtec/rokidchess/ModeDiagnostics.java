package com.sjtec.rokidchess;
import com.sjtec.rokidchess.shared.CoachMode;
import java.util.*;

/** Runs the production coach on a fixed sample, never on or against the user's game object. */
final class ModeDiagnostics {
    static final String SAMPLE="r1bqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    static String run(LocalCoach coach) throws Exception {
        Game sample=new Game();sample.load(SAMPLE,List.of());String before=sample.board.getFen();
        StringBuilder result=new StringBuilder("Stockfish 19 실제 분석 · 샘플 국면\n");
        for(CoachMode mode:CoachMode.values()){
            LocalCoach.Advice advice=coach.analyse(sample,"white",mode.key);
            if(!advice.mode.equals(mode.key)||Game.find(sample.board,advice.uci)==null||!sample.board.getFen().equals(before)||!sample.moves.isEmpty())
                throw new IllegalStateException("모드 분석 대조 실패: "+mode.label);
            result.append(mode.label).append(": ").append(advice.uci).append(" · 모드 일치\n");
        }
        return result.append("샘플 시험 통과 · 실제 대국 기록 유지\n같은 수가 나와도 정상일 수 있습니다.").toString();
    }
}
