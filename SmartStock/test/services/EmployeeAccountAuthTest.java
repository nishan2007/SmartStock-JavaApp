package services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP transport against a disposable auth stub. Never contacts hosted Auth. */
class EmployeeAccountAuthTest {
    private static final String ID="00000000-0000-0000-0000-000000000001";
    private void exercise(boolean mfa,String code,boolean wrongIdentity,boolean rejectUpdate,boolean mandatory,boolean expectSuccess)throws Exception {
        List<String> calls=new ArrayList<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/auth/v1/",x->{
            String path=x.getRequestURI().toString(),method=x.getRequestMethod();
            calls.add(method+" "+path);
            String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            String response="{}";int status=200;
            if(path.contains("/token")) {assertTrue(body.contains("current password"));response="{\"access_token\":\"test-access\"}";}
            else if(path.equals("/auth/v1/user")&&method.equals("GET"))response="{\"id\":\""+(wrongIdentity?"other":ID)+"\",\"factors\":"+(mfa?"[{\"id\":\"factor\",\"factor_type\":\"totp\",\"status\":\"verified\"}]":"[]")+"}";
            else if(path.endsWith("/challenge"))response="{\"id\":\"challenge\"}";
            else if(path.endsWith("/verify")) {
                assertTrue(body.contains("123456"));
                String jwt="e30."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"aal\":\"aal2\"}".getBytes(StandardCharsets.UTF_8))+".test";
                response="{\"access_token\":\""+jwt+"\"}";
            }else if(method.equals("PUT")) {
                assertTrue(body.contains("new password"));assertTrue(body.contains("current_password"));
                assertNotNull(x.getRequestHeaders().getFirst("Authorization"));
                if(rejectUpdate)status=422;
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();
        });
        server.start();
        try {
            var cfg=new SupabaseProjectConfig(null,"http://127.0.0.1:"+server.getAddress().getPort(),"test-public-key","test");
            var client=HttpClient.newHttpClient();
            if(expectSuccess)assertDoesNotThrow(()->EmployeeWebServer.updateAccountPassword(cfg,client,"test@example.com",ID,"current password","new password",code,mandatory));
            else assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.updateAccountPassword(cfg,client,"test@example.com",ID,"current password","new password",code,mandatory));
            boolean put=calls.contains("PUT /auth/v1/user");
            assertEquals(expectSuccess||rejectUpdate,put);
            if(expectSuccess&&mfa)assertTrue(calls.indexOf("POST /auth/v1/factors/factor/verify")<calls.indexOf("PUT /auth/v1/user"));
        }finally{server.stop(0);}
    }
    @Test void passwordChangeUsesCurrentPasswordAndAuthenticatedPut()throws Exception{exercise(false,"",false,false,false,true);}
    @Test void enrolledMfaIsVerifiedBeforeUpdate()throws Exception{exercise(true,"123456",false,false,false,true);}
    @Test void enrolledMfaCannotBeSkipped()throws Exception{exercise(true,"",false,false,false,false);}
    @Test void mandatoryMfaCannotBeSkippedWhenFactorWasRemoved()throws Exception{exercise(false,"",false,false,true,false);}
    @Test void authenticatedIdentityMustMatchSignedInEmployee()throws Exception{exercise(false,"",true,false,false,false);}
    @Test void rejectedPasswordPolicyIsNotReportedAsSuccess()throws Exception{exercise(false,"",false,true,false,false);}
}
