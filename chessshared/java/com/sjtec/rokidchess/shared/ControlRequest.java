package com.sjtec.rokidchess.shared;
import org.json.JSONObject;

/** Whitelisted, idempotent controls; no toggle command that could reverse on retry. */
public final class ControlRequest {
    public final String id,operation,mode;public final boolean enabled;
    private ControlRequest(String id,String operation,String mode,boolean enabled){this.id=id;this.operation=operation;this.mode=mode;this.enabled=enabled;}
    public String fingerprint(){return operation+":"+mode+":"+enabled;}
    public static ControlRequest parse(String raw) throws Exception {
        if(raw==null||raw.length()>1024)throw new IllegalArgumentException("설정 요청 크기 오류");
        JSONObject q=new JSONObject(raw);String id=q.getString("id"),op=q.getString("op");
        if(q.getInt("v")!=1||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("설정 요청 형식 오류");
        String mode="";boolean enabled=false;
        if(op.equals("mode.set"))mode=CoachMode.require(q.getString("mode")).key;
        else if(op.equals("guide.set")){Object value=q.get("enabled");if(!(value instanceof Boolean))throw new IllegalArgumentException("안내 상태 형식 오류");enabled=(Boolean)value;}
        else if(!op.equals("state.get"))throw new IllegalArgumentException("지원하지 않는 설정 요청");
        return new ControlRequest(id,op,mode,enabled);
    }
}
