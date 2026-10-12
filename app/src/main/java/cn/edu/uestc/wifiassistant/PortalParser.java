package cn.edu.uestc.wifiassistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

public final class PortalParser {
    private PortalParser() {}
    public static final class Page {
        public final String ip, acid;
        public Page(String ip, String acid) { this.ip = ip; this.acid = acid; }
    }

    public static boolean ipv4(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        for (String part : value.split("\\.")) if (Integer.parseInt(part) > 255) return false;
        return !value.equals("0.0.0.0") && !value.startsWith("127.");
    }

    public static Page page(String html) throws JSONException {
        String ip = "";
        Matcher inputs = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (inputs.find()) {
            String input = inputs.group();
            if (Pattern.compile("(?:id|name)\\s*=\\s*['\"](?:user_ip|online_ip|v46ip)['\"]", Pattern.CASE_INSENSITIVE).matcher(input).find()) {
                Matcher v = Pattern.compile("value\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE).matcher(input);
                if (v.find() && ipv4(v.group(1))) { ip = v.group(1); break; }
            }
        }
        if (ip.isEmpty()) {
            Matcher m = Pattern.compile("(?:['\"]?(?:ip|user_ip|online_ip)['\"]?)\\s*[:=]\\s*['\"]([0-9.]+)['\"]").matcher(html);
            if (m.find() && ipv4(m.group(1))) ip = m.group(1);
        }
        Matcher ac = Pattern.compile("(?:['\"]?(?:acid|ac_id)['\"]?)\\s*[:=]\\s*['\"]?([0-9]{1,4})").matcher(html);
        String acid = ac.find() ? ac.group(1) : "";
        if (ip.isEmpty() || acid.isEmpty() || Integer.parseInt(acid) < 1 || Integer.parseInt(acid) > 4095)
            throw new JSONException("学校登录页未提供有效的 IP 或接入编号");
        return new Page(ip, acid);
    }

    public static JSONObject jsonp(String body) throws JSONException {
        String v = body.trim();
        if (v.startsWith("{")) return new JSONObject(v);
        Matcher m = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(\\s*(\\{.*\\})\\s*\\)\\s*;?\\s*$", Pattern.DOTALL).matcher(v);
        if (!m.matches()) throw new JSONException("学校接口响应格式已变化");
        return new JSONObject(m.group(1));
    }

    public static boolean online(JSONObject status) {
        return "ok".equalsIgnoreCase(status.optString("error"))
                && (ipv4(status.optString("online_ip")) || status.optInt("online_device_total", 0) > 0);
    }
    public static boolean offline(JSONObject status) {
        return "not_online_error".equals(status.optString("error")) || "not_online".equals(status.optString("error"));
    }
    public static boolean loginOk(JSONObject value) {
        return "ok".equalsIgnoreCase(value.optString("error"))
                || "login_ok".equals(value.optString("suc_msg"));
    }
    public static boolean alreadyOnline(JSONObject value) {
        return "ip_already_online_error".equals(value.optString("error"))
                || "ip_already_online_error".equals(value.optString("suc_msg"))
                || "ip_already_online_error".equals(value.optString("error_msg"));
    }

    public static String failure(JSONObject json) {
        String code = json.optString("ecode");
        String raw = json.optString("error") + " " + json.optString("error_msg");
        String advice;
        if (raw.contains("password") || raw.contains("E2531") || code.equals("E2531") || raw.contains("E2553") || code.equals("E2553"))
            advice = "账号或密码不正确，请由账号持有人检查凭据及运营商。";
        else if (raw.contains("E2620") || code.equals("E2620"))
            advice = "超过允许的在线设备数，请在官方页面检查在线设备；本 App 不会踢掉其他设备。";
        else if (raw.contains("captcha") || raw.contains("vcode") || raw.contains("otp"))
            advice = "学校要求验证码或人工验证，请打开官方认证页完成。";
        else if (raw.contains("balance") || raw.contains("E3001") || raw.contains("expire"))
            advice = "账号余额不足或套餐失效，请由账号持有人在官方页面核查。";
        else if (raw.contains("limit") || raw.contains("too"))
            advice = "学校限制了请求频率，请稍后再试。";
        else advice = "请检查运营商、账号状态，或打开官方认证页确认学校提示。";
        String error = json.optString("error", "unknown");
        // Display codes only. Raw responses can contain account details and must not reach UI/logs.
        if (!error.matches("[A-Za-z0-9_:-]{1,80}")) error = "unknown";
        if (!code.matches("[A-Za-z0-9_-]{0,20}")) code = "";
        return advice + "\n接口代码：" + error + (code.isEmpty() ? "" : " / " + code);
    }
}
