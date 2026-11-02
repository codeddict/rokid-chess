package com.sjtec.rokidchess;

import java.net.URL;

// Public release downloads only. No GitHub credential is needed or sent by the app.
final class UpdateChannel {
    static final String REPOSITORY="codeddict/rokid-chess";
    static final String BASE="https://github.com/"+REPOSITORY;
    static final String MANIFEST=BASE+"/releases/latest/download/update.txt";
    static final int MAX_REDIRECTS=5;
    static String apk(String version){return BASE+"/releases/download/v"+version+"/rokid-chess-v"+version+".apk";}
    static URL allowed(String value) throws Exception {
        URL url=new URL(value);
        if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||url.getRef()!=null
            ||(url.getPort()!=-1&&url.getPort()!=443))throw new Exception("untrusted_update_url");
        String host=url.getHost(),path=url.getPath();
        if(host.equals("github.com")){
            String prefix="/"+REPOSITORY+"/releases/";
            if(!path.equals(prefix+"latest/download/update.txt")
                &&!path.matches(java.util.regex.Pattern.quote(prefix)+"download/v[0-9A-Za-z._-]{1,40}/(update\\.txt|rokid-chess-v[0-9A-Za-z._-]{1,40}\\.apk)"))throw new Exception("untrusted_release_path");
        }else if(!host.equals("release-assets.githubusercontent.com")&&!host.equals("objects.githubusercontent.com")){
            throw new Exception("untrusted_update_host");
        }else if(!path.startsWith("/github-production-release-asset/"))throw new Exception("untrusted_asset_path");
        return url;
    }
    static URL redirect(URL current,String location) throws Exception {
        if(location==null||location.isBlank())throw new Exception("missing_redirect_location");
        return allowed(new URL(current,location).toString());
    }
}
