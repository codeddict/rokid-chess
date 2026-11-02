package com.sjtec.rokidchess;

/** A saved position or engine result alone is never evidence of the physical board. */
final class GuidePolicy {
    static boolean confirmed(boolean live,boolean calibrated,String observation){
        return live&&calibrated&&(observation.equals("변화 없음")||observation.startsWith("자동 반영:"));
    }
}
