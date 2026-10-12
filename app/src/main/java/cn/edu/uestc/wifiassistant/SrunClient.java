package cn.edu.uestc.wifiassistant;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/** One guarded login per user flow. Unknown/network errors never trigger blind authentication. */
public final class SrunClient {
    public static final String BASE = "http://10.253.0.235";
    public interface Transport {
        String get(String path, Map<String, String> params) throws IOException;
        boolean internet() throws IOException;
        void pause(long millis) throws IOException;
    }
    public interface CredentialSource { Credentials load() throws Exception; }
    public enum Kind { ONLINE, AUTHENTICATED, AUTHENTICATED_NO_INTERNET, AUTH_PENDING, NEED_CREDENTIALS,
        AUTH_FAILED, NETWORK_FAILED, PROTOCOL_CHANGED, KEY_FAILED, COOLDOWN, CANCELLED, NEED_AUTH }
    public static final class Result {
        public final Kind kind;
        public final String title, detail;
        public Result(Kind kind, String title, String detail) { this.kind=kind; this.title=title; this.detail=detail; }
        public boolean success() { return kind == Kind.ONLINE || kind == Kind.AUTHENTICATED; }
    }
    private final Transport transport;
    private final BooleanSupplier active;
    private final BooleanSupplier takeLoginSlot;
    public SrunClient(Transport transport, BooleanSupplier active, BooleanSupplier takeLoginSlot) {
        this.transport=transport; this.active=active; this.takeLoginSlot=takeLoginSlot;
    }
    private void check() throws IOException { if (!active.getAsBoolean()) throw new IOException("cancelled"); }
    private JSONObject request(String path, Map<String, String> params) throws IOException, JSONException {
        check();
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("callback", "uestc" + System.currentTimeMillis());
        p.put("_", String.valueOf(System.currentTimeMillis()));
        return PortalParser.jsonp(transport.get(path, p));
    }
    private JSONObject status() throws IOException, JSONException { return request("/cgi-bin/rad_user_info", new LinkedHashMap<>()); }
    private boolean internet() throws IOException {
        check();
        try { return transport.internet(); } catch (IOException e) { check(); return false; }
    }
    public Result run(boolean allowLogin, String overrideAcid, CredentialSource source) {
        try {
            JSONObject current = null;
            boolean statusIoError = false, statusFormatError = false;
            try { current = status(); }
            catch (IOException e) { check(); statusIoError = true; }
            catch (JSONException e) { statusFormatError = true; }
            if (internet()) return new Result(Kind.ONLINE, "网络已连接", "目标 Wi-Fi 已能访问互联网，未提交登录请求。");
            if (current != null && PortalParser.online(current))
                return new Result(Kind.AUTHENTICATED_NO_INTERNET, "校园网已认证", "学校确认当前路由器在线，但外网探测失败。请检查路由器、DNS 或运营商网络，稍后点击按钮重新检测；未重复登录。");
            if (current == null || !PortalParser.offline(current)) {
                return new Result(statusFormatError || (!statusIoError && current != null) ? Kind.PROTOCOL_CHANGED : Kind.NETWORK_FAILED,
                        statusIoError ? "无法访问认证服务" : "学校接口需要检查",
                        "尚不能确认是否需要认证，未提交账号凭据。请确认路由器上游接入校园网，并打开官方认证页核查。");
            }
            if (!allowLogin) return new Result(Kind.NEED_AUTH, "校园网需要认证", "点击「连接并认证」继续。");
            Credentials credentials;
            try { credentials = source.load(); }
            catch (Exception e) { return new Result(Kind.KEY_FAILED, "授权信息无法读取", "本机密钥或密文已失效。请清除授权信息，再由账号持有人重新输入。"); }
            if (credentials == null) return new Result(Kind.NEED_CREDENTIALS, "请先配置授权账号", "请由账号持有人在设置页亲自输入凭据并确认授权。");
            try (Credentials c = credentials) {
                if (!takeLoginSlot.getAsBoolean())
                    return new Result(Kind.COOLDOWN, "请稍后再试", "刚刚已执行过认证。为避免账号被限流，请等待 30 秒后重试，或打开官方页面。");
                check();
                String hint = overrideAcid.isEmpty() ? "3" : overrideAcid;
                Map<String, String> pageQuery = new LinkedHashMap<>();
                pageQuery.put("ac_id", hint); pageQuery.put("theme", "pro");
                PortalParser.Page page = PortalParser.page(transport.get("/srun_portal_pc", pageQuery));
                String acid = overrideAcid.isEmpty() ? page.acid : overrideAcid;
                String username = c.fullUsername();
                Map<String, String> tokenQuery = new LinkedHashMap<>();
                tokenQuery.put("username", username); tokenQuery.put("ip", page.ip);
                JSONObject tokenResponse = request("/cgi-bin/get_challenge", tokenQuery);
                String token = tokenResponse.optString("challenge");
                if (!token.matches("[A-Za-z0-9]{16,128}"))
                    return new Result(Kind.PROTOCOL_CHANGED, "无法获取认证令牌", "学校没有返回有效 challenge，未提交登录。请打开官方认证页检查验证码或接口变化。");
                // Check again in case a roommate brought the shared router online while obtaining the token.
                JSONObject latest = status();
                boolean restoredInternet = internet();
                if (restoredInternet) return new Result(Kind.ONLINE, "网络已连接", "检测到宿舍网络已经恢复，未提交登录请求。");
                if (PortalParser.online(latest))
                    return new Result(Kind.AUTHENTICATED_NO_INTERNET, "校园网已认证", "宿舍路由器已在线，但外网暂不可用；未重复登录。");
                if (!PortalParser.offline(latest))
                    return new Result(Kind.PROTOCOL_CHANGED, "无法确认认证状态", "学校在线状态发生变化，未提交登录。请重新检测或打开官方认证页。");
                check();
                JSONObject response = request("/cgi-bin/srun_portal", SrunCrypto.loginParameters(username, c.password, page.ip, acid, token));
                if (!PortalParser.loginOk(response) && !PortalParser.alreadyOnline(response))
                    return new Result(Kind.AUTH_FAILED, "认证失败", PortalParser.failure(response));
                boolean schoolOnline = PortalParser.alreadyOnline(response);
                for (int attempt = 0; attempt < 3; attempt++) {
                    check();
                    if (attempt > 0) transport.pause(1000);
                    try { schoolOnline = schoolOnline || PortalParser.online(status()); } catch (IOException | JSONException e) { check(); }
                    if (internet()) return new Result(Kind.AUTHENTICATED, "网络已连接", "学校认证与外网连通检查完成。");
                    if (schoolOnline) break;
                }
                if (schoolOnline) return new Result(Kind.AUTHENTICATED_NO_INTERNET, "校园网已认证", "学校已确认认证成功，但外网暂不可用。请检查 DNS、路由器或运营商网络；不会重复提交账号。");
                return new Result(Kind.AUTH_PENDING, "认证结果待确认", "学校已接受认证请求，但尚未确认联网。请稍后重新检测，或打开官方页面查看状态；本次不会自动重复登录。");
            }
        } catch (JSONException | IllegalArgumentException e) {
            return new Result(Kind.PROTOCOL_CHANGED, "认证协议需要检查", "当前登录页或接口参数与已知协议不符。请打开官方认证页；没有执行自动重试。");
        } catch (Exception e) {
            if (!active.getAsBoolean()) return new Result(Kind.CANCELLED, "操作已停止", "网络切换或授权信息已清除，请重新连接后再试。");
            return new Result(Kind.NETWORK_FAILED, "网络请求未完成", "请保持连接 Xiaomi_181A，检查路由器及校园网线路。请求超时或网络中断时无法确认认证结果，请稍后重新检测。");
        }
    }
}
