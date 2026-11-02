package com.sjtec.rokidchess;
import com.sjtec.rokidchess.shared.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ModeControlTest {
    private static final String ID="01234567-89ab-cdef-0123-456789abcdef";
    @Test public void exactlyThreeStableModesAndLegacyMigration(){assertEquals(3,CoachMode.values().length);assertSame(CoachMode.CLOSE,CoachMode.stored("balanced"));assertSame(CoachMode.MEDIUM,CoachMode.require("medium"));assertSame(CoachMode.STRONG,CoachMode.stored("strong"));}
    @Test public void distinctAdvantageTargets(){List<UciEngine.Line> c=List.of(new UciEngine.Line("a2a3",500,null),new UciEngine.Line("b2b3",400,null),new UciEngine.Line("c2c3",300,null));assertEquals("c2c3",LocalCoach.choose(c,"close",200).move);assertEquals("b2b3",LocalCoach.choose(c,"medium",200).move);assertEquals("a2a3",LocalCoach.choose(c,"strong",200).move);}
    @Test public void noForcedMateOrLosingCandidatesForGentlerModes(){for(String mode:List.of("close","medium")){UciEngine.Line mate=new UciEngine.Line("a2a3",99999,1);assertSame(mate,LocalCoach.choose(List.of(mate,new UciEngine.Line("b2b3",300,null)),mode,500));UciEngine.Line equal=new UciEngine.Line("a2a3",20,null);assertSame(equal,LocalCoach.choose(List.of(equal,new UciEngine.Line("b2b3",-100,null)),mode,500));}}
    @Test public void validatedIdempotentRequests() throws Exception{ControlRequest r=ControlRequest.parse("{\"v\":1,\"id\":\""+ID+"\",\"op\":\"mode.set\",\"mode\":\"medium\"}");assertEquals("medium",r.mode);ControlRequest guide=ControlRequest.parse("{\"v\":1,\"id\":\""+ID+"\",\"op\":\"guide.set\",\"enabled\":false}");assertFalse(guide.enabled);assertNotEquals(r.fingerprint(),guide.fingerprint());}
    @Test public void invalidActionsAndTypesRejected(){for(String extra:List.of("\"op\":\"guide.toggle\"","\"op\":\"mode.set\",\"mode\":\"impossible\"","\"op\":\"guide.set\",\"enabled\":\"true\"")){try{ControlRequest.parse("{\"v\":1,\"id\":\""+ID+"\","+extra+"}");fail(extra);}catch(Exception expected){}}}
    @Test public void cornersAreExplainedFromFathersSeat(){assertTrue(CalibrationSteps.title("white",0).contains("a8 · 아들 쪽 왼쪽"));assertTrue(CalibrationSteps.title("black",0).contains("a8 · 내 쪽 오른쪽"));assertTrue(CalibrationSteps.title("black",2).contains("h1 · 아들 쪽 왼쪽"));assertEquals("네 모서리 선택 완료",CalibrationSteps.title("white",4));}
}
