package com.sjtec.rokidchess.shared;

/** Stable wire keys, UI labels and bounded advantage targets shared by both apps. */
public enum CoachMode {
    CLOSE("close","아슬아슬","아슬아슬하게 이기기",80,30,200),
    MEDIUM("medium","중간","중간 수준으로 이기기",250,100,100),
    STRONG("strong","압도적","압도적으로 이기기",0,0,0);
    public final String key,label,title;
    public final int targetCp,floorCp,maxLoss;
    CoachMode(String key,String label,String title,int target,int floor,int loss){this.key=key;this.label=label;this.title=title;targetCp=target;floorCp=floor;maxLoss=loss;}
    public static CoachMode require(String key){for(CoachMode m:values())if(m.key.equals(key))return m;throw new IllegalArgumentException("알 수 없는 안내 모드");}
    public static CoachMode stored(String key){if("balanced".equals(key))return CLOSE;try{return require(key);}catch(IllegalArgumentException e){return CLOSE;}}
}
