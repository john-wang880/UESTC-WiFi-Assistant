package cn.edu.uestc.wifiassistant;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class AuthenticationFlowTest {
    static final String OFF="{\"error\":\"not_online_error\"}";
    static final String ON="{\"error\":\"ok\",\"online_ip\":\"10.20.10.99\",\"online_device_total\":\"1\"}";
    static class Mock implements SrunClient.Transport {
        Deque<String> statuses=new ArrayDeque<>();
        String login="{\"error\":\"ok\",\"suc_msg\":\"login_ok\"}", token="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        boolean initialInternet, internetAfterLogin=true, internetAfterChallenge, statusIo, cancelled;
        int loginCalls, challengeCalls, statusCalls, pauses;
        Map<String,String> lastLogin=new LinkedHashMap<>();
        @Override public String get(String path,Map<String,String> p) throws IOException {
            if(path.endsWith("rad_user_info")) { statusCalls++; if(statusIo) throw new IOException("offline"); return statuses.isEmpty() ? OFF : statuses.removeFirst(); }
            if(path.equals("/srun_portal_pc")) return "var CONFIG={acid:'3',ip:'10.20.10.99'}";
            if(path.endsWith("get_challenge")) { challengeCalls++; if(cancelOnChallenge) cancelled=true; return "cb({\"error\":\"ok\",\"challenge\":\""+token+"\"})"; }
            if(path.endsWith("srun_portal")) { loginCalls++; lastLogin=new LinkedHashMap<>(p); return login; }
            throw new IOException("unexpected path");
        }
        boolean cancelOnChallenge;
        @Override public boolean internet() { return initialInternet || (internetAfterLogin && loginCalls > 0) || (internetAfterChallenge && challengeCalls > 0); }
        @Override public void pause(long millis) { pauses++; }
        SrunClient client() { return new SrunClient(this,() -> !cancelled,() -> true); }
    }
    Credentials credentials() { return new Credentials("202600000000","@dx","dummy!234".toCharArray()); }
    @Test public void onlineSkipsCredentialsAndLogin() {
        Mock m=new Mock(); m.statuses.add(ON); m.initialInternet=true;
        AtomicInteger loads=new AtomicInteger();
        SrunClient.Result r=m.client().run(true,"",() -> { loads.incrementAndGet(); return credentials(); });
        assertEquals(SrunClient.Kind.ONLINE,r.kind); assertEquals(0,m.loginCalls); assertEquals(0,m.challengeCalls); assertEquals(0,loads.get());
    }
    @Test public void existingAuthenticationWithOutageNeverRelogs() {
        Mock m=new Mock(); m.statuses.add(ON);
        SrunClient.Result r=m.client().run(true,"",this::credentials);
        assertEquals(SrunClient.Kind.AUTHENTICATED_NO_INTERNET,r.kind); assertEquals(0,m.loginCalls);
    }
    @Test public void statusServiceFailureIsNotPasswordFailure() {
        Mock m=new Mock(); m.statusIo=true;
        assertEquals(SrunClient.Kind.NETWORK_FAILED,m.client().run(true,"",this::credentials).kind);
        assertEquals(0,m.challengeCalls); assertEquals(0,m.loginCalls);
        m.initialInternet=true;
        assertEquals(SrunClient.Kind.ONLINE,m.client().run(true,"",this::credentials).kind);
    }
    @Test public void unknownStatusDoesNotBlindlySubmitCredentials() {
        Mock m=new Mock(); m.statuses.add("{\"error\":\"new_protocol\"}");
        assertEquals(SrunClient.Kind.PROTOCOL_CHANGED,m.client().run(true,"",this::credentials).kind); assertEquals(0,m.loginCalls);
    }
    @Test public void missingConsentOrReadOnlyStopsBeforeToken() {
        Mock m=new Mock();
        assertEquals(SrunClient.Kind.NEED_CREDENTIALS,m.client().run(true,"",() -> null).kind);
        assertEquals(SrunClient.Kind.NEED_AUTH,m.client().run(false,"",this::credentials).kind);
        assertEquals(0,m.challengeCalls); assertEquals(0,m.loginCalls);
    }
    @Test public void exactlyOneLoginUsesCampusUpstreamIp() {
        Mock m=new Mock(); m.statuses.add(OFF); m.statuses.add(OFF); m.statuses.add(ON);
        SrunClient.Result r=m.client().run(true,"",this::credentials);
        assertEquals(SrunClient.Kind.AUTHENTICATED,r.kind); assertEquals(1,m.loginCalls);
        assertEquals("10.20.10.99",m.lastLogin.get("ip")); assertEquals("3",m.lastLogin.get("ac_id"));
        assertEquals("202600000000@dx",m.lastLogin.get("username")); assertTrue(m.lastLogin.get("password").startsWith("{MD5}"));
    }
    @Test public void roommateOnlineDuringChallengeSkipsFinalLogin() {
        Mock m=new Mock(); m.statuses.add(OFF); m.statuses.add(ON); m.internetAfterChallenge=true;
        assertEquals(SrunClient.Kind.ONLINE,m.client().run(true,"",this::credentials).kind);
        assertEquals(1,m.challengeCalls); assertEquals(0,m.loginCalls);
    }
    @Test public void passwordFailureNeverAutomaticallyRetries() {
        Mock m=new Mock(); m.login="{\"error\":\"password_error\",\"ecode\":\"E2531\"}";
        assertEquals(SrunClient.Kind.AUTH_FAILED,m.client().run(true,"",this::credentials).kind);
        assertEquals(1,m.loginCalls); assertEquals(0,m.pauses);
    }
    @Test public void invalidChallengeAndCancellationPreventLogin() {
        Mock m=new Mock(); m.token="";
        assertEquals(SrunClient.Kind.PROTOCOL_CHANGED,m.client().run(true,"",this::credentials).kind); assertEquals(0,m.loginCalls);
        m=new Mock(); m.cancelOnChallenge=true;
        assertEquals(SrunClient.Kind.CANCELLED,m.client().run(true,"",this::credentials).kind); assertEquals(0,m.loginCalls);
    }
    @Test public void cooldownStopsBeforeAnyLoginRequest() {
        Mock m=new Mock(); SrunClient c=new SrunClient(m,() -> true,() -> false);
        assertEquals(SrunClient.Kind.COOLDOWN,c.run(true,"",this::credentials).kind); assertEquals(0,m.challengeCalls); assertEquals(0,m.loginCalls);
    }
    @Test public void successResponseAloneDoesNotClaimInternet() {
        Mock m=new Mock(); m.internetAfterLogin=false;
        assertEquals(SrunClient.Kind.AUTH_PENDING,m.client().run(true,"",this::credentials).kind);
        assertEquals(1,m.loginCalls); assertEquals(2,m.pauses);
    }
    @Test public void keyFailureHasActionableRecovery() {
        Mock m=new Mock();
        assertEquals(SrunClient.Kind.KEY_FAILED,m.client().run(true,"",() -> { throw new IOException("do not expose secret"); }).kind);
        assertEquals(0,m.loginCalls);
    }
}
