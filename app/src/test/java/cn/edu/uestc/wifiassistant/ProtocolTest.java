package cn.edu.uestc.wifiassistant;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void matchesOfficialCampusJavascriptVectors() throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/srun_vectors.json")) {
            assertNotNull(in);
            JSONArray cases=new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            for(int i=0;i<cases.length();i++) {
                JSONObject v=cases.getJSONObject(i);
                String user=v.getString("username"), password=v.getString("password"), token=v.getString("token");
                Map<String,String> actual=SrunCrypto.loginParameters(user,password.toCharArray(),v.getString("ip"),v.getString("acid"),token);
                assertEquals(v.getString("hmac"),actual.get("password").substring(5));
                assertEquals(v.getString("info"),actual.get("info"));
                assertEquals(v.getString("checksum"),actual.get("chksum"));
            }
        }
    }
    @Test public void suffixesAreNotDuplicatedOrSilentlyChanged() {
        assertEquals("202600000000@dx",SrunCrypto.username("202600000000@dx","@dx"));
        assertEquals("202600000000@cmcc",SrunCrypto.username("202600000000","@cmcc"));
        assertThrows(IllegalArgumentException.class,() -> SrunCrypto.username("202600000000@dx","@cmcc"));
        assertThrows(IllegalArgumentException.class,() -> SrunCrypto.username("","@dx"));
        assertThrows(IllegalArgumentException.class,() -> SrunCrypto.username("a b","@dx"));
    }
    @Test public void readsCampusConfigAndOldInputAttributeOrder() throws Exception {
        PortalParser.Page p=PortalParser.page("<script>var CONFIG={acid:'3',ip:'10.20.10.99'};</script>");
        assertEquals("10.20.10.99",p.ip); assertEquals("3",p.acid);
        p=PortalParser.page("<input value=\"10.22.1.4\" type=\"hidden\" id=\"user_ip\"><script>ac_id=\"1\";</script>");
        assertEquals("10.22.1.4",p.ip); assertEquals("1",p.acid);
        assertThrows(Exception.class,() -> PortalParser.page("CONFIG={acid:'3',ip:'999.20.1.1'}"));
        assertThrows(Exception.class,() -> PortalParser.page("CONFIG={ip:'10.1.1.1'}"));
    }
    @Test public void jsonpIsParsedAsDataAndNeverExecuted() throws Exception {
        assertEquals("ok",PortalParser.jsonp("cb_1({\"error\":\"ok\"});").getString("error"));
        assertThrows(Exception.class,() -> PortalParser.jsonp("cb({\"error\":\"ok\"});steal()"));
        assertThrows(Exception.class,() -> PortalParser.jsonp("<html>login</html>"));
    }
    @Test public void failuresOnlyExposeCodesAndAdvice() throws Exception {
        String text=PortalParser.failure(new JSONObject("{\"error\":\"fail\",\"ecode\":\"E2531\",\"error_msg\":\"secret account and password\"}"));
        assertTrue(text.contains("账号")); assertFalse(text.contains("secret"));
        assertTrue(PortalParser.failure(new JSONObject("{\"error\":\"vcode_error\"}")).contains("人工验证"));
        assertFalse(PortalParser.loginOk(new JSONObject("{\"error\":\"fail\",\"ecode\":0}")));
    }
    @Test public void credentialArrayIsCleared() {
        Credentials c=new Credentials("202600000000","@dx","test-value".toCharArray());
        c.close(); char[] expected=new char[c.password.length]; assertArrayEquals(expected,c.password);
        assertEquals("Credentials[redacted]",c.toString());
        assertEquals("",SrunCrypto.base64(new byte[0]));
        assertEquals(0,SrunCrypto.xencode("","token").length);
    }
}
