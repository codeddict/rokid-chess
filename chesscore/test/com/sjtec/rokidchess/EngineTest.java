package com.sjtec.rokidchess;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;
public class EngineTest {
    @Test public void threeModesReachTheRealStockfishCoach()throws Exception{
        File executable=new File(System.getProperty("chess.root"),".tools/stockfish/stockfish/stockfish-windows-x86-64-universal.exe");Assume.assumeTrue(executable.exists());
        try(LocalCoach coach=new LocalCoach(executable.getPath())){
            String report=ModeDiagnostics.run(coach);assertTrue(report.contains("아슬아슬:"));assertTrue(report.contains("중간:"));assertTrue(report.contains("압도적:"));assertTrue(report.contains("샘플 시험 통과"));
            System.out.println(report);
        }
    }
    @Test public void parsesPerspectiveScoresAndMates(){Map<Integer,UciEngine.Line> m=new TreeMap<>();UciEngine.parse("info depth 16 multipv 1 score cp -34 nodes 999 pv e7e5 g1f3",m);assertEquals(-34,m.get(1).cp);UciEngine.parse("info depth 18 multipv 2 score mate 3 pv d8h4",m);assertEquals(Integer.valueOf(3),m.get(2).mate);UciEngine.parse("info score cp 100 lowerbound pv a2a3",m);assertEquals(2,m.size());}
    @Test public void balancedModeAndMateGuard(){UciEngine.Line a=new UciEngine.Line("a2a3",200,null),b=new UciEngine.Line("b2b3",90,null);assertSame(a,LocalCoach.choose(List.of(a,b),"strong",60));assertSame(b,LocalCoach.choose(List.of(a,b),"balanced",60));UciEngine.Line mate=new UciEngine.Line("a2a3",99999,1);assertSame(mate,LocalCoach.choose(List.of(mate,b),"balanced",200));}
    @Test public void actualBundledVersionUciIntegration() throws Exception{
        File executable=new File(System.getProperty("chess.root"),".tools/stockfish/stockfish/stockfish-windows-x86-64-universal.exe");
        Assume.assumeTrue(executable.exists());
        try(UciEngine engine=new UciEngine(executable.getPath())){
            long start=System.nanoTime();List<UciEngine.Line> lines=engine.analyse(Game.START,List.of("e2e4"),300,3,null);
            Game g=new Game();g.load(Game.START,List.of("e2e4"));assertNotNull(Game.find(g.board,lines.get(0).move));
            lines=engine.analyse("7k/5K2/6Q1/8/8/8/8/8 w - - 0 1",List.of(),200,1,null);assertEquals(Integer.valueOf(1),lines.get(0).mate);
            System.out.println("UCI two positions including cold start ms="+(System.nanoTime()-start)/1_000_000);
        }
    }
}
