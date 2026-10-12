package cn.edu.uestc.wifiassistant;

import java.util.Arrays;

public final class Credentials implements AutoCloseable {
    public final String account, suffix;
    public final char[] password;
    public Credentials(String account, String suffix, char[] password) {
        this.account = account; this.suffix = suffix; this.password = password.clone();
    }
    public String fullUsername() { return SrunCrypto.username(account, suffix); }
    @Override public void close() { Arrays.fill(password, '\0'); }
    @Override public String toString() { return "Credentials[redacted]"; }
}
