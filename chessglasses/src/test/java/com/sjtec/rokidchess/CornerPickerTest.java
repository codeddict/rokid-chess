package com.sjtec.rokidchess;
import org.junit.Test;
import static org.junit.Assert.*;

public class CornerPickerTest {
    @Test public void colourChangesTheSeatCorners(){CornerPicker w=new CornerPicker(false),b=new CornerPicker(true);assertTrue(w.points[0][0]<.5);assertTrue(w.points[0][1]<.5);assertTrue(b.points[0][0]>.5);assertTrue(b.points[0][1]>.5);assertTrue(w.title().contains("아들 쪽 왼쪽"));assertTrue(b.title().contains("내 쪽 오른쪽"));}
    @Test public void adjustingDoesNotConfirmACorner(){CornerPicker p=new CornerPicker(false);float x=p.points[0][0];p.tap();p.swipe(1);assertEquals(x+.01,p.points[0][0],.0001);p.tap();assertEquals(CornerPicker.Axis.ACTION,p.axis);assertEquals(0,p.corner);}
    @Test public void cursorCannotLeavePhoto(){CornerPicker p=new CornerPicker(false);p.tap();for(int i=0;i<300;i++)p.swipe(-1);assertEquals(.01,p.points[0][0],.0001);for(int i=0;i<300;i++)p.swipe(1);assertEquals(.99,p.points[0][0],.0001);}
    @Test public void allFourCornersMustBeConfirmedInOrder(){CornerPicker p=new CornerPicker(false);for(int i=0;i<4;i++){p.selection=2;assertEquals(i==3?CornerPicker.Result.DONE:CornerPicker.Result.NONE,p.tap());}double[][] pixels=p.pixels(1000,500);assertEquals(180,pixels[0][0],.001);assertEquals(100,pixels[0][1],.001);}
    @Test public void retakeAndCancelAreExplicitActions(){CornerPicker p=new CornerPicker(false);p.selection=4;assertEquals(CornerPicker.Result.RETAKE,p.tap());p.selection=5;assertEquals(CornerPicker.Result.CANCEL,p.tap());}
    @Test public void backPreservesEditedCoordinates(){CornerPicker p=new CornerPicker(false);p.tap();p.swipe(1);p.back();p.selection=2;p.tap();p.back();assertEquals(0,p.corner);assertEquals(.19,p.points[0][0],.0001);CornerPicker other=new CornerPicker(false);assertEquals(.18,other.points[0][0],.0001);}
}
