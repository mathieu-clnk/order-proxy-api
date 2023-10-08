package com.kamvity.samples.order_proxy.service;

import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import com.kamvity.samples.order_proxy.health.TerminalHealth;
import com.kamvity.samples.order_proxy.health.TerminalHealthTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
/*
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = OrderProxyConfig.class)
@ActiveProfiles("dev")
public class OrderTerminalServiceTest {
    @Autowired
    OrderTerminalService orderTerminalService;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startServer() {
        mockServer = startClientAndServer(8090);
    }


    @BeforeEach
    public void reset() throws InterruptedException {
        mockServer.reset();
        //Make sure the Time limiter is passed
        Thread.sleep(2000);
        int i = 0;
        while ( ! mockServer.isRunning() && i < 30) {
            Thread.sleep(1000);
            i++;
        }
    }

    @AfterAll
    public static void stopServer() {
        mockServer.stop();
    }


    String order1 = "{\n" +
            "    \"orderId\": 1,\n" +
            "    \"price\": 200.0,\n" +
            "    \"orderTimestamp\": \"2023-01-09T14:12:13.931+00:00\",\n" +
            "    \"customer\": {\n" +
            "        \"customerId\": 1,\n" +
            "        \"title\": \"Majesty\",\n" +
            "        \"firstname\": \"Soma\",\n" +
            "        \"lastname\": \"Leavyi\",\n" +
            "        \"email\": \"soma.leavyi@email.org\",\n" +
            "        \"address\": \"1 street of Majesty, Cambodia\",\n" +
            "        \"zipCode\": \"2222\"\n" +
            "    }\n" +
            "}";

    String order2 = "{ \"order\" : \"soma\" }";

    public void mockOrderGetByIdOK() {
        Header header = Header.header("Content-Type","application/json");
        new MockServerClient("127.0.0.1",8090)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1/order-terminal/get-by-id")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader(header)
                                .withBody(order1)
                );
    }


    @Test
    public void testFindOrderById() {
        mockOrderGetByIdOK();
        Mono<HashMap> result = orderTerminalService.findOrderById(Optional.of("1"));
        HashMap<String,Object> response = result.block();
        assertEquals(1,response.get("orderId"));
    }

    @Test
    public void testFindOrderByIdAlreadyTimeout() {
        mockOrderGetByIdOK();
        TerminalHealth.status = TerminalHealth.STOPPED;
        TerminalHealth.failedTime = Timestamp.from(Instant.now());
        Mono<HashMap> result = orderTerminalService.findOrderById(Optional.of("1"));
        HashMap<String,Object> response = result.block();
        assertEquals("failed", result.block().get("status"));
        assertEquals("The backend is not available, please try again later.", result.block().get("errorMessage"));
        assertEquals("BackendTimeOut",result.block().get("errorReason"));
    }

    @Test
    public void testFindOrderByIdOldTimeout() {
        mockOrderGetByIdOK();
        TerminalHealth.status = TerminalHealth.STOPPED;
        TerminalHealth.failedTime = Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(5)));
        Mono<HashMap> result = orderTerminalService.findOrderById(Optional.of("1"));
        HashMap<String,Object> response = result.block();
        assertEquals(1,response.get("orderId"));
        assertEquals(TerminalHealth.RUNNING,TerminalHealth.status);
    }

    @Test
    public void testFindByIdTimeOut() {
        new MockServerClient("127.0.0.1", 8090)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1/order-terminal/get-by-id")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody(order1)
                                .withDelay(TimeUnit.MINUTES, 2)
                );
        Mono<HashMap> mreor = orderTerminalService.findOrderById(Optional.of("1"));
        assert mreor.block() != null;
        assertEquals("failed", mreor.block().get("status"));
        String errorMessage = "The operation timed out. Exception: Did not observe any item or terminal signal within 15000ms in 'source(MonoDefer)' (and no fallback has been configured). Url http://127.0.0.1:8090/v1/order-terminal/get-by-id?orderId=1.";
        assertEquals(errorMessage, mreor.block().get("errorMessage"));
    }
}
*/