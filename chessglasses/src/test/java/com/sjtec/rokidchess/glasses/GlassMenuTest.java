package com.sjtec.rokidchess.glasses;
import org.junit.Test;
import static org.junit.Assert.*;
import com.sjtec.rokidchess.shared.CoachMode;

public class GlassMenuTest {
    private GlassMenu menu(){return new GlassMenu("0.4.0");}
    @Test public void menuNavigationAndReturn(){GlassMenu m=menu();m.open();assertEquals(0,m.selection);m.next(-1);assertEquals(4,m.selection);m.next(1);assertEquals(0,m.selection);m.next(1);assertEquals(1,m.selection);assertTrue(m.body().contains("중간 수준"));m.back();assertEquals(GlassMenu.Screen.GUIDE,m.screen);}
    @Test public void choiceWaitsForPhoneAcknowledgement(){GlassMenu m=menu();m.open();m.selection=1;m.waiting("request");assertSame(CoachMode.CLOSE,m.mode);assertEquals(GlassMenu.Screen.MENU,m.screen);m.update("phone",1,"medium",false,true,"0.4.0","request",true,"");assertSame(CoachMode.MEDIUM,m.mode);assertEquals(GlassMenu.Screen.GUIDE,m.screen);assertEquals("",m.pendingId);}
    @Test public void failedStartStaysInMenuAndExplainsCalibration(){GlassMenu m=menu();m.open();m.waiting("request");m.update("phone",1,"close",false,false,"0.4.0","request",false,"판 위치를 먼저 맞추세요");assertEquals(GlassMenu.Screen.MENU,m.screen);assertFalse(m.live);assertTrue(m.notice.contains("판 위치"));}
    @Test public void oldAndRetiredSessionsCannotUndoSelection(){GlassMenu m=menu();assertTrue(m.update("one",2,"medium",false,true,"0.4.0","",true,""));assertFalse(m.update("one",1,"close",false,true,"0.4.0","",true,""));assertTrue(m.update("two",1,"strong",false,true,"0.4.0","",true,""));assertFalse(m.update("one",3,"close",false,true,"0.4.0","",true,""));assertSame(CoachMode.STRONG,m.mode);}
    @Test public void lateAckDoesNotClearNewRequest(){GlassMenu m=menu();m.waiting("new");m.update("phone",1,"medium",false,true,"0.4.0","old",true,"");assertEquals("new",m.pendingId);m.timeout("old");assertEquals("new",m.pendingId);}
    @Test public void actualVersionsInVersionView(){GlassMenu m=menu();m.update("phone",1,"medium",true,true,"0.4.1","",true,"");m.screen=GlassMenu.Screen.VERSION;assertTrue(m.body().contains("안경: 0.4.0"));assertTrue(m.body().contains("휴대폰: 0.4.1"));assertTrue(m.body().contains("중간"));}
}
