package com.sjtec.rokidchess;

/** Frozen-photo corner editing entirely with swipe/tap. Coordinates are normalized. */
final class CornerPicker {
    enum Axis {ACTION,X,Y}enum Result {NONE,DONE,RETAKE,CANCEL}
    private static final float[][] DEFAULT={{.18f,.20f},{.82f,.20f},{.82f,.80f},{.18f,.80f}};
    final float[][] points=new float[4][2];final boolean black;int corner,selection;Axis axis=Axis.ACTION;
    CornerPicker(boolean black){this.black=black;for(int i=0;i<4;i++)points[i]=DEFAULT[black?(i+2)%4:i].clone();}
    void swipe(int direction){if(axis==Axis.ACTION)selection=Math.floorMod(selection+direction,6);else{int coordinate=axis==Axis.X?0:1;points[corner][coordinate]=Math.max(.01f,Math.min(.99f,points[corner][coordinate]+direction*.01f));}}
    Result tap(){if(axis!=Axis.ACTION){axis=Axis.ACTION;return Result.NONE;}switch(selection){
        case 0:axis=Axis.X;break;case 1:axis=Axis.Y;break;
        case 2:if(++corner==4){corner=3;return Result.DONE;}selection=0;break;
        case 3:if(corner>0)corner--;break;case 4:return Result.RETAKE;case 5:return Result.CANCEL;
    }return Result.NONE;}
    void back(){if(axis!=Axis.ACTION)axis=Axis.ACTION;else if(corner>0)corner--;}
    String title(){return CalibrationSteps.title(black?"black":"white",corner);}
    String help(){String[] choices={"좌우 위치 조정","위아래 위치 조정","이 모서리 확정","이전 모서리","다시 촬영","맞추기 취소"};return axis==Axis.ACTION?"▶ "+choices[selection]+"\n스와이프: 항목 이동 · 탭: 실행":(axis==Axis.X?"좌우":"위아래")+" 조정 · 스와이프: 1% 이동\n탭: 항목 선택으로 돌아가기";}
    double[][] pixels(int width,int height){double[][] result=new double[4][2];for(int i=0;i<4;i++){result[i][0]=points[i][0]*width;result[i][1]=points[i][1]*height;}return result;}
}
