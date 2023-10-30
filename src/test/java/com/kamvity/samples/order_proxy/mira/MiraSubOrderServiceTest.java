package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import com.kamvity.samples.order_proxy.health.TerminalHealth;
import com.kamvity.samples.order_proxy.mira.MiraSubOrderService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.serialization.JsonArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = OrderProxyConfig.class)
@ActiveProfiles("dev")
public class MiraSubOrderServiceTest {

    @Autowired
    MiraSubOrderService miraSubOrderService;

    private static final Logger log = LoggerFactory.getLogger(MiraSubOrderServiceTest.class);

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
            "    \"orderTimestamp\": \"2023-01-09T14:12:13.931+00:00\"\n" +
            "}";

    String order2 = "{ \"order\" : \"soma\" }";

    String savedItem = "{\n" +
            "   \"status\": \"success\",\n" +
            "   \"messageId\": \"123456\"\n" +
            "}";

    String savedItemFallback = "{\n" +
            "   \"status\": \"success\",\n" +
            "   \"messageId\": \"123456\", \n" +
            "   \"responseType\": \"fallback\" \n" +
            "}";

    public void mockOrderSetOK(List<HashMap> orders) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<JSONObject>();
        orders.stream().forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
        new MockServerClient("127.0.0.1",8090)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/mira/set-order")
                                .withBody(jsonArray.toString())
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader(header)
                                .withBody(order1)
                );
    }

    public void mockOrderSetFailed() {
        Header header = Header.header("Content-Type","application/json");
        new MockServerClient("127.0.0.1",8090)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/mira/set-order")
                )
                .respond(
                        response()
                                .withStatusCode(503)
                );
    }

    public void mockOrderSetFallback(List<HashMap> orders) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<JSONObject>();
        orders.stream().forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
        new MockServerClient("127.0.0.1",8090)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/queue/save")
                                .withBody(jsonArray.toString())
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader(header)
                                .withBody(savedItemFallback)
                );
    }

    public void mockOrderGetByIdOK() {
        Header header = Header.header("Content-Type","application/json");
        new MockServerClient("127.0.0.1",8090)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1/mira/get-by-id")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader(header)
                                .withBody(order1)
                );
    }

    public void mockOrderSetRetry(List<HashMap> orders,int retry) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<JSONObject>();
        orders.stream().forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090);
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/queue/save")
                                .withBody(jsonArray.toString()),
                        Times.exactly(retry)
                )
                .respond(
                        response()
                                .withStatusCode(503)
                );
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/queue/save")
                                .withBody(jsonArray.toString()),
                        Times.exactly(1)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader(header)
                                .withBody(savedItemFallback)

                );
    }
    public void mockOrderSetRetryFailed(List<HashMap> orders) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<JSONObject>();
        orders.stream().forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090);
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/queue/save")
                )
                .respond(
                        response()
                                .withStatusCode(503)
                );
    }

    @Test
    public void testGetOrderById() {
        MiraHealth.status = MiraHealth.RUNNING;
        mockOrderGetByIdOK();
        Mono<HashMap> result = miraSubOrderService.getOrderById(Optional.of("1") );
        HashMap<String,Object> response = result.block();
        assertEquals(1,response.get("orderId"));
    }

    @Test
    public void testFindOrderByIdAlreadyTimeout() {
        mockOrderGetByIdOK();
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now());
        Mono<HashMap> result = miraSubOrderService.getOrderById(Optional.of("1") );
        HashMap<String,Object> response = result.block();
        assertEquals("failed", result.block().get("status"));
        assertEquals("Context:<Url: http://127.0.0.1:8090/v1/mira/get-by-id?orderId=1>, error:The backend is not available, please try again later.", result.block().get("errorMessage"));
        assertEquals("BackendTimeOut",result.block().get("errorReason"));
    }

    @Test
    public void testFindOrderByIdOldTimeout() {
        mockOrderGetByIdOK();
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(5)));
        Mono<HashMap> result = miraSubOrderService.getOrderById(Optional.of("1") );
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
                                .withPath("/v1/mira/get-by-id")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody(order1)
                                .withDelay(TimeUnit.MINUTES, 2)
                );
        MiraHealth.status = MiraHealth.RUNNING;
        Mono<HashMap> mreor = miraSubOrderService.getOrderById(Optional.of("1") );
        assert mreor.block() != null;
        assertEquals("failed", mreor.block().get("status"));
        String errorMessage = "Context:<Url: http://127.0.0.1:8090/v1/mira/get-by-id?orderId=1>, error:The operation timed out.";
        assertEquals(errorMessage, mreor.block().get("errorMessage"));
    }

    @Test
    public void testSetOrder() {
        MiraHealth.status = MiraHealth.RUNNING;
        List<HashMap> orders = new ArrayList<>();
        HashMap<String,String> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        mockOrderSetOK(orders);
        Mono<HashMap> result = miraSubOrderService.setOrder(Optional.of(orders) );
        HashMap<String,Object> response = result.block();
        assertEquals(1,response.get("orderId"));
    }
    @Test
    public void testSetOrderFallback() {
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now());
        List<HashMap> orders = new ArrayList<>();
        HashMap<String,String> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        mockOrderSetOK(orders);
        mockOrderSetFallback(orders);
        Mono<HashMap> result = miraSubOrderService.setOrder(Optional.of(orders) );
        HashMap<String,Object> response = result.block();
        assertEquals("123456",response.get("messageId"));
        assertEquals("fallback", response.get("responseType"));
    }

    @Test
    public void testSetOrderFallbackRetry() {
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now());
        List<HashMap> orders = new ArrayList<>();
        HashMap<String,String> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        mockOrderSetFailed();
        mockOrderSetRetry(orders,2);
        Mono<HashMap> result = miraSubOrderService.setOrder(Optional.of(orders) );
        HashMap<String,Object> response = result.block();
        assertEquals("123456",response.get("messageId"));
        assertEquals("fallback", response.get("responseType"));
    }

    @Test
    public void testSetOrderFallbackRetryWhenOK() {
        MiraHealth.status = MiraHealth.RUNNING;
        List<HashMap> orders = new ArrayList<>();
        HashMap<String,String> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        mockOrderSetFailed();
        mockOrderSetRetry(orders,2);
        Mono<HashMap> result = miraSubOrderService.setOrder(Optional.of(orders) );
        HashMap<String,Object> response = result.block();
        assertEquals("123456",response.get("messageId"));
        assertEquals("fallback", response.get("responseType"));
    }
    @Test
    public void testSetOrderPanic() {
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now());
        List<HashMap> orders = new ArrayList<>();
        HashMap<String,String> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        mockOrderSetFailed();
        mockOrderSetRetry(orders,10);
        Mono<HashMap> result = miraSubOrderService.setOrder(Optional.of(orders) );
        try {
            HashMap<String, Object> response = result.block();
        }catch (Exception ex) {
            assertEquals("Neither the order service nor its fallback have responded.",ex.getMessage());
        }
    }
}
