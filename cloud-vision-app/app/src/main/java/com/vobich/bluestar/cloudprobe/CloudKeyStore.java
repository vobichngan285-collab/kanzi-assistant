package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android Keystore-backed local secret; never bundled into APK or repository. */
final class CloudKeyStore {
    private static final String ALIAS="bluestar_cloud_probe_api_v1";
    private static final String PREFS="bluestar_cloud_secret";
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    static void save(Context context,String secret) throws Exception {
        if(secret==null||secret.trim().isEmpty())throw new IllegalArgumentException("密钥不能为空");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] ciphertext=cipher.doFinal(secret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("ciphertext",Base64.encodeToString(ciphertext,Base64.NO_WRAP)).apply();
    }
    static String load(Context context) throws Exception {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String iv=p.getString("iv",""),encrypted=p.getString("ciphertext","");
        if(iv.isEmpty()||encrypted.isEmpty())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),
            java.nio.charset.StandardCharsets.UTF_8);
    }
    /** Multiple provider secrets share the proven Android Keystore AES-GCM key,
     * but each profile has an independent random IV and ciphertext pair.
     * The legacy single Bailian Key remains at its original unchanged keys. */
    static void saveNamed(Context context,String id,String secret)throws Exception{
        if(!id.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new IllegalArgumentException("无效云端配置 ID");
        if(secret==null||secret.trim().isEmpty())
            throw new IllegalArgumentException("API Key 不能为空");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(secret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("iv_"+id,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("ciphertext_"+id,Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    static String loadNamed(Context context,String id)throws Exception{
        if(!id.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new IllegalArgumentException("无效云端配置 ID");
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String iv=prefs.getString("iv_"+id,"");
        String content=prefs.getString("ciphertext_"+id,"");
        if(iv.isEmpty()||content.isEmpty())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(
            128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(content,Base64.NO_WRAP)),
            java.nio.charset.StandardCharsets.UTF_8);
    }
    static void clearNamed(Context context,String id){
        if(id==null||!id.matches("[a-zA-Z0-9_-]{1,64}"))return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .remove("iv_"+id).remove("ciphertext_"+id).apply();
    }
    static void clear(Context context){
        // Clearing the legacy Bailian key must never erase independently
        // encrypted keys belonging to user-created OCR/vision/ASR providers.
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .remove("iv").remove("ciphertext").apply();
    }
}
