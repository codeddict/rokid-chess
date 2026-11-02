package com.sjtec.rokidchess;

import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;

/** 설정 화면/Activity 재생성을 넘어 보존하는 설치 대기 정보. 외부 경로는 저장하거나 신뢰하지 않는다. */
final class PendingUpdateStore {
    private final File record,directory;
    PendingUpdateStore(File files,File cache){record=new File(files,"chess-pending-update.json");directory=new File(cache,"updates");}
    boolean exists(){return record.isFile();}
    File apk(UpdateInfo info) throws Exception {
        File file=new File(directory,"chess-"+info.versionCode+"-"+info.sha256.substring(0,12)+".apk");
        // Android의 /data/user/0 같은 정상적인 상위 경로 별칭은 canonical 경로끼리 비교한다.
        if(Files.isSymbolicLink(directory.toPath())||!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())
            ||Files.isSymbolicLink(file.toPath()))throw new Exception("invalid_update_path");
        return file;
    }
    void save(UpdateInfo info) throws Exception {
        File temporary=File.createTempFile("chess-pending-",".json",record.getParentFile());
        try{
            Files.write(temporary.toPath(),info.json().toString().getBytes(StandardCharsets.UTF_8));
            try{Files.move(temporary.toPath(),record.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),record.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary.toPath());}
    }
    UpdateInfo load() throws Exception {
        if(!exists())return null;
        if(Files.isSymbolicLink(record.toPath())||record.length()>4096)throw new Exception("invalid_pending_update");
        return new UpdateInfo(new JSONObject(new String(Files.readAllBytes(record.toPath()),StandardCharsets.UTF_8)));
    }
    // 복원 시 캐시도 변경됐을 수 있다. 크기·내용 해시를 다시 검사하며 서명 검사는 Android 계층이 이어서 한다.
    File verifiedFile(UpdateInfo info) throws Exception {
        File file=apk(info);
        if(!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)||file.length()!=info.bytes)throw new Exception("pending_update_size");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(InputStream in=Files.newInputStream(file.toPath())){
            byte[] bytes=new byte[32768];int n;
            while((n=in.read(bytes))!=-1){total+=n;if(total>info.bytes)throw new Exception("pending_update_size");digest.update(bytes,0,n);}
        }
        StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(java.util.Locale.US,"%02x",value&255));
        if(total!=info.bytes||!hex.toString().equals(info.sha256))throw new Exception("pending_update_hash");
        return file;
    }
    void clear() throws Exception {Files.deleteIfExists(record.toPath());}
}
