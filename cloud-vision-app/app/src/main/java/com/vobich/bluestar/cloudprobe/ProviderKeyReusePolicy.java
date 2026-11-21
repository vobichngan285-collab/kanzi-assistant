package com.vobich.bluestar.cloudprobe;

import java.net.URI;
import java.util.Locale;

/** Explicit same HTTPS origin, not a suffix/name/model guess. Prevents
 * sending one provider's credentials to a deceptive or unrelated domain. */
final class ProviderKeyReusePolicy {
    private ProviderKeyReusePolicy(){}
    static boolean sameProvider(String left,String right){
        if(left==null||right==null)return false;
        try{
            URI a=new URI(left.trim()),b=new URI(right.trim());
            if(!"https".equalsIgnoreCase(a.getScheme())||
               !"https".equalsIgnoreCase(b.getScheme())||
               a.getRawUserInfo()!=null||b.getRawUserInfo()!=null)return false;
            if(a.getHost()==null||b.getHost()==null)return false;
            String h1=a.getHost().toLowerCase(Locale.ROOT);
            String h2=b.getHost().toLowerCase(Locale.ROOT);
            if(h1.isEmpty()||!h1.equals(h2))return false;
            return (a.getPort()==-1?443:a.getPort())==
                   (b.getPort()==-1?443:b.getPort());
        }catch(Exception error){return false;}
    }
}
