package com.sjtec.rokidchess;

/** The coordinates never change; the seat-relative explanation changes with the father's colour. */
final class CalibrationSteps {
    static String title(String player,int selected){
        if(selected>=4)return "네 모서리 선택 완료";
        String[] squares={"a8","h8","h1","a1"};
        String[] seats=player.equals("black")?new String[]{"내 쪽 오른쪽","내 쪽 왼쪽","아들 쪽 왼쪽","아들 쪽 오른쪽"}:
            new String[]{"아들 쪽 왼쪽","아들 쪽 오른쪽","내 쪽 오른쪽","내 쪽 왼쪽"};
        return (selected+1)+"/4 · "+squares[selected]+" · "+seats[selected];
    }
}
