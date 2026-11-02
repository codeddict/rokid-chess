package com.sjtec.rokidchess;
import org.junit.*;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class ChessUpdateTest {
    private JSONObject manifest(long size,String hash)throws Exception{return new JSONObject().put("package","com.sjtec.rokidchess").put("version_code",6).put("version_name","0.6.0").put("url","https://github.com/codeddict/rokid-chess/releases/download/v0.6.0/rokid-chess-v0.6.0.apk").put("bytes",size).put("sha256",hash);}
    private String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    @Test public void acceptsRealChessApkSizeAboveOldHqLimit()throws Exception{UpdateInfo i=new UpdateInfo(manifest(105_000_000,"a".repeat(64)));assertEquals(105_000_000,i.bytes);assertEquals(6,i.versionCode);}
    @Test public void untrustedUrlPackageAndOversizeRejected()throws Exception{
        for(JSONObject bad:List.of(manifest(100,"a".repeat(64)).put("url","http://sjtec.kr/rokid-chess/rokid-chess-v0.6.0.apk"),manifest(100,"a".repeat(64)).put("url","https://evil.example/update.apk"),manifest(100,"a".repeat(64)).put("package","com.other.app"),manifest(160_000_000,"a".repeat(64)),manifest(0,"a".repeat(64)),manifest(100,"not-a-hash"))){try{new UpdateInfo(bad);fail();}catch(Exception expected){}}
    }
    @Test public void pathTraversalVersionRejected()throws Exception{try{new UpdateInfo(manifest(100,"a".repeat(64)).put("version_name","../../outside"));fail();}catch(Exception expected){}}
    @Test public void pendingInstallRestoresAcrossInstancesAndDetectsTampering()throws Exception{
        Path root=Files.createTempDirectory("chess-update-");Path files=Files.createDirectory(root.resolve("files")),cache=Files.createDirectory(root.resolve("cache"));Files.createDirectory(cache.resolve("updates"));
        byte[] bytes="verified download fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);UpdateInfo info=new UpdateInfo(manifest(bytes.length,sha(bytes)));
        PendingUpdateStore one=new PendingUpdateStore(files.toFile(),cache.toFile());Path apk=one.apk(info).toPath();
        try{
            Files.write(apk,bytes);one.save(info);PendingUpdateStore restored=new PendingUpdateStore(files.toFile(),cache.toFile());
            assertEquals("0.6.0",restored.load().versionName);assertEquals(apk.toFile(),restored.verifiedFile(restored.load()));
            byte[] changed=bytes.clone();changed[0]^=1;Files.write(apk,changed);
            try{restored.verifiedFile(info);fail("tampering accepted");}catch(Exception expected){}
            restored.clear();assertFalse(restored.exists());
        }finally{Files.deleteIfExists(apk);Files.deleteIfExists(files.resolve("chess-pending-update.json"));Files.deleteIfExists(cache.resolve("updates"));Files.deleteIfExists(cache);Files.deleteIfExists(files);Files.deleteIfExists(root);}
    }
    @Test public void oversizedPendingMetadataRejected()throws Exception{
        Path root=Files.createTempDirectory("chess-record-");Path record=root.resolve("chess-pending-update.json");
        try{Files.write(record,"x".repeat(5000).getBytes(java.nio.charset.StandardCharsets.UTF_8));try{new PendingUpdateStore(root.toFile(),root.toFile()).load();fail();}catch(Exception expected){}}
        finally{Files.deleteIfExists(record);Files.deleteIfExists(root);}
    }
}
