package com.sjtec.rokidchess;

import org.junit.Test;
import java.net.URL;
import static org.junit.Assert.*;

public class UpdateChannelTest {
    @Test public void followsGithubLatestReleaseAndSignedAsset() throws Exception {
        URL latest=UpdateChannel.allowed(UpdateChannel.MANIFEST+"?t=123");
        URL version=UpdateChannel.redirect(latest,"/codeddict/rokid-chess/releases/download/v0.5.1/update.txt");
        assertEquals("github.com",version.getHost());
        URL asset=UpdateChannel.redirect(version,"https://release-assets.githubusercontent.com/github-production-release-asset/123/abc-def?sig=example");
        assertEquals("release-assets.githubusercontent.com",asset.getHost());
        assertEquals("github.com",UpdateChannel.allowed(UpdateChannel.apk("0.5.1")).getHost());
    }
    @Test public void rejectsDowngradeOtherRepoCredentialsAndLookalikeHosts() throws Exception {
        String[] bad={"http://github.com/codeddict/rokid-chess/releases/latest/download/update.txt",
            "https://github.com/another/project/releases/download/v1/update.txt",
            "https://github.com.evil.example/codeddict/rokid-chess/releases/latest/download/update.txt",
            "https://user:secret@github.com/codeddict/rokid-chess/releases/latest/download/update.txt",
            "https://release-assets.githubusercontent.com.evil.example/github-production-release-asset/1/x",
            "https://github.com:8443/codeddict/rokid-chess/releases/latest/download/update.txt",
            "https://release-assets.githubusercontent.com/unrelated/file",
            "https://sjtec.kr/rokid-chess/rokid-chess-v0.5.1.apk"};
        for(String value:bad){try{UpdateChannel.redirect(UpdateChannel.allowed(UpdateChannel.MANIFEST),value);fail(value);}catch(Exception expected){}}
        try{UpdateChannel.redirect(UpdateChannel.allowed(UpdateChannel.MANIFEST),null);fail();}catch(Exception expected){}
    }
    @Test public void manifestCannotSelectAnotherReleaseOrAsset() throws Exception {
        org.json.JSONObject json=new org.json.JSONObject().put("package","com.sjtec.rokidchess").put("version_code",6)
            .put("version_name","0.5.1").put("sha256","a".repeat(64)).put("bytes",104000000).put("url",UpdateChannel.apk("0.5.1"));
        assertEquals(UpdateChannel.apk("0.5.1"),new UpdateInfo(json).url);
        json.put("url",UpdateChannel.apk("0.5.0"));
        try{new UpdateInfo(json);fail();}catch(Exception expected){}
    }
}
