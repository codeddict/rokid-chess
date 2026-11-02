package com.sjtec.rokidchess;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class ObservationTest {
    private double[] changes(String... squares){double[] d=new double[64];Arrays.fill(d,.002);for(String s:squares)d[(8-(s.charAt(1)-'0'))*8+s.charAt(0)-'a']=.25;return d;}
    @Test public void ordinaryMove(){assertEquals("e2e4",MoveObservation.match(new Game().board,changes("e2","e4")).uci);}
    @Test public void liftedPieceDoesNotCommit(){assertEquals("",MoveObservation.match(new Game().board,changes("e2")).uci);}
    @Test public void handDoesNotCommit(){assertEquals("",MoveObservation.match(new Game().board,changes("e2","e4","f2","f3","f4","g2","g3","g4")).uci);}
    @Test public void unexplainedMotionDoesNotCommit(){assertEquals("",MoveObservation.match(new Game().board,changes("e2","e4","a8")).uci);}
    @Test public void castlingFourSquares(){Game g=new Game();g.load("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",List.of());assertEquals("e1g1",MoveObservation.match(g.board,changes("e1","g1","h1","f1")).uci);}
    @Test public void enPassantThreeSquares(){Game g=new Game();g.load(Game.START,List.of("e2e4","a7a6","e4e5","d7d5"));assertEquals("e5d6",MoveObservation.match(g.board,changes("e5","d6","d5")).uci);}
    @Test public void promotionRequiresChoice(){Game g=new Game();g.load("7k/P7/8/8/8/8/8/7K w - - 0 1",List.of());MoveObservation.Match r=MoveObservation.match(g.board,changes("a7","a8"));assertEquals("",r.uci);assertEquals("a7a8",r.promotion);}
    @Test public void temporalConsensusAndFreshness(){MoveObservation c=new MoveObservation();assertFalse(c.stable("e2e4",1000));assertFalse(c.stable("e2e4",1100));assertTrue(c.stable("e2e4",1350));assertFalse(c.stable("d2d4",1500));assertFalse(c.stable("d2d4",5000));c.reset();assertFalse(c.stable("d2d4",5500));}
    @Test public void nonFiniteInputRejected(){double[] d=changes("e2","e4");d[0]=Double.NaN;assertEquals("",MoveObservation.match(new Game().board,d).uci);}
    @Test public void lowRegistrationResidualDoesNotAccumulateAsMotion(){double[] d=changes("e2","e4");for(int i=0;i<32;i++)if(d[i]<.1)d[i]=.025;assertEquals("e2e4",MoveObservation.match(new Game().board,d).uci);}
    @Test public void globalOcclusionIsNotMistakenForAnUnchangedBoard(){double[] d=new double[64];Arrays.fill(d,.2);MoveObservation.Match m=MoveObservation.match(new Game().board,d);assertEquals("",m.uci);assertNotEquals("변화 없음",m.reason);}
}
