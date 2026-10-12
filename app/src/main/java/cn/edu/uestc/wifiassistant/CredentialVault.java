package cn.edu.uestc.wifiassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public final class CredentialVault {
    static final String ALIAS = "uestc_wifi_authorized_v1";
    static final String PREFS = "authorized_credentials";
    private final SharedPreferences prefs;
    private final String alias;
    private final byte[] aad;
    public CredentialVault(Context context) { this(context, ALIAS, PREFS); }
    CredentialVault(Context context, String alias, String preferenceName) {
        this.alias = alias;
        prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        aad = (context.getPackageName() + "/authorized/v1").getBytes(StandardCharsets.UTF_8);
    }
    public synchronized boolean hasCredentials() { return prefs.contains("ciphertext"); }
    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        if (!create) throw new GeneralSecurityException("本机密钥不可用");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    public synchronized void save(Credentials credentials, boolean ownerConfirmed) throws Exception {
        if (!ownerConfirmed) throw new IllegalArgumentException("必须由账号持有人确认授权");
        credentials.fullUsername();
        if (credentials.password.length == 0 || credentials.password.length > 256) throw new IllegalArgumentException("密码长度应为 1 到 256 个字符");
        String json = "{\"account\":" + SrunCrypto.quote(credentials.account.trim()) + ",\"suffix\":"
                + SrunCrypto.quote(credentials.suffix) + ",\"password\":" + SrunCrypto.quote(new String(credentials.password))
                + ",\"owner_authorized_at\":" + System.currentTimeMillis() + "}";
        byte[] plain = json.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(aad);
            byte[] encrypted = cipher.doFinal(plain);
            try {
                if (!prefs.edit().clear().putInt("schema", 1)
                        .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                        .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
                    throw new GeneralSecurityException("无法保存加密凭据");
            } finally { Arrays.fill(encrypted, (byte) 0); }
        } finally { Arrays.fill(plain, (byte) 0); }
    }
    public synchronized Credentials load() throws Exception {
        if (!hasCredentials()) return null;
        if (prefs.getInt("schema", 0) != 1) throw new GeneralSecurityException("凭据格式不受支持");
        byte[] iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP);
        byte[] data = Base64.decode(prefs.getString("ciphertext", ""), Base64.NO_WRAP);
        if (iv.length != 12 || data.length < 16) throw new GeneralSecurityException("凭据损坏");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] plain = cipher.doFinal(data);
        try {
            JSONObject json = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            if (json.optLong("owner_authorized_at", 0) <= 0) throw new GeneralSecurityException("缺少账号持有人授权");
            char[] secret = json.getString("password").toCharArray();
            try { return new Credentials(json.getString("account"), json.getString("suffix"), secret); }
            finally { Arrays.fill(secret, '\0'); }
        } finally { Arrays.fill(plain, (byte) 0); Arrays.fill(data, (byte) 0); }
    }
    public synchronized void clear() throws Exception {
        boolean cleared = prefs.edit().clear().commit();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) store.deleteEntry(alias);
        if (!cleared) throw new GeneralSecurityException("删除存储失败，密钥已撤销，请在系统中清除应用数据");
    }
}
