package com.sjtec.rokidchess;

import org.json.JSONObject;

// 공개 업데이트 안내의 입력 계약. 패키지·크기·해시·버전과 고정 호스트의 불변 APK 경로를 검증한다.
// 이 검증만으로 APK 신원을 보증하지 않으므로 SelfUpdater가 실제 패키지와 서명도 확인한다.
final class UpdateInfo {
    final int versionCode;
    final String versionName,url,sha256;
    final long bytes;
    JSONObject json() throws Exception {
        return new JSONObject().put("package","com.sjtec.rokidchess").put("version_code",versionCode)
            .put("version_name",versionName).put("url",url).put("sha256",sha256).put("bytes",bytes);
    }
    UpdateInfo(JSONObject json) throws Exception {
        versionCode=json.getInt("version_code");versionName=json.getString("version_name");
        url=json.getString("url");sha256=json.getString("sha256");bytes=json.getLong("bytes");
        if(!json.getString("package").equals("com.sjtec.rokidchess")||versionCode<1
            ||!versionName.matches("[0-9A-Za-z._-]{1,40}")||!sha256.matches("[a-f0-9]{64}")||bytes<1||bytes>150L*1024*1024
            ||!url.equals(UpdateChannel.apk(versionName)))throw new Exception("invalid_update_manifest");
    }
}
