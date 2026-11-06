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
    static void clear(Context context){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
    }
}
