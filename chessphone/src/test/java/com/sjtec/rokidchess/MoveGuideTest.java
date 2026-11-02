package com.sjtec.rokidchess;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class MoveGuideTest {
    private String describe(Game g,String move,String color){return MoveGuide.describe(g.board,Objects.requireNonNull(Game.find(g.board,move)),color);}
    @Test public void whitePhysicalDirection(){String s=describe(new Game(),"g1f3","white");assertTrue(s.contains("내 나이트 g1 → f3"));assertTrue(s.contains("앞으로 2칸 · 왼쪽 1칸"));}
    @Test public void blackDirectionUsesFathersSide(){Game g=new Game();g.move("e2e4");String s=describe(g,"g8f6","black");assertTrue(s.contains("앞으로 2칸 · 오른쪽 1칸"));}
    @Test public void captureExplainsPhysicalRemoval(){Game g=new Game();g.load(Game.START,List.of("e2e4","d7d5"));assertTrue(describe(g,"e4d5","white").contains("상대 폰을 잡으세요"));}
    @Test public void castleExplainsBothPieces(){Game g=new Game();g.load("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",List.of());String s=describe(g,"e1g1","white");assertTrue(s.contains("내 킹 e1 → g1"));assertTrue(s.contains("룩 h1 → f1도"));}
    @Test public void enPassantExplainsThirdSquare(){Game g=new Game();g.load(Game.START,List.of("e2e4","a7a6","e4e5","d7d5"));assertTrue(describe(g,"e5d6","white").contains("d5의 상대 폰을 제거"));}
    @Test public void promotionExplainsReplacement(){Game g=new Game();g.load("7k/P7/8/8/8/8/8/7K w - - 0 1",List.of());assertTrue(describe(g,"a7a8n","white").contains("폰을 나이트로 바꾸세요"));}
    @Test public void requiresCurrentPhysicalObservation(){assertFalse(GuidePolicy.confirmed(false,false,"휴대폰 분석 완료"));assertFalse(GuidePolicy.confirmed(false,true,"변화 없음"));assertFalse(GuidePolicy.confirmed(true,false,"변화 없음"));assertFalse(GuidePolicy.confirmed(true,true,"손 가림 / 머리 움직임 · 대기"));assertTrue(GuidePolicy.confirmed(true,true,"변화 없음"));assertTrue(GuidePolicy.confirmed(true,true,"자동 반영: e2e4"));}
}
