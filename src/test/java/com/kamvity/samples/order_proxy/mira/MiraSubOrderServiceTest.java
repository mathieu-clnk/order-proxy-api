package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import com.kamvity.samples.order_proxy.health.TerminalHealth;
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
import org.mockserver.model.Header;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = OrderProxyConfig.class)
@ActiveProfiles("dev")
class MiraSubOrderServiceTest {

    @Autowired
    MiraSubOrderService miraSubOrderService;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startServer() {
        mockServer = startClientAndServer(8090);
    }


    @BeforeEach
    public void reset() {
        mockServer.stop();
        mockServer = startClientAndServer(8090);
        await().atMost(20, TimeUnit.SECONDS).until(mockServer::isRunning);
    }

    @AfterAll
    public static void stopServer() {
        mockServer.stop();
    }


    String order1 = """
            {
                "orderId": 1,
                "orderTimestamp": "2023-01-09T14:12:13.931+00:00"
            }""";

    String savedItemFallback = """
            {
               "status": "success",
               "messageId": "123456",\s
               "responseType": "fallback"\s
            }""";


    public void mockOrderSetOK(MockServerClient mockServerClient, List<HashMap<String,Object>> orders) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<>();
        orders.forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
            mockServerClient
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

    public void mockOrderSetFailed(MockServerClient mockServerClient) {
            mockServerClient
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

    public void mockOrderSetFallback(MockServerClient mockServerClient, List<HashMap<String,Object>> orders) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<>();
        orders.forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
            mockServerClient
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

    public void mockOrderGetByIdOK(MockServerClient mockServerClient) {
        Header header = Header.header("Content-Type","application/json");
            mockServerClient
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

    public void mockOrderSetRetry(MockServerClient mockServerClient,List<HashMap<String,Object>> orders, int retry) {
        Header header = Header.header("Content-Type","application/json");
        List<JSONObject> jsonObjectList = new ArrayList<>();
        orders.forEach((o) -> jsonObjectList.add(new JSONObject(o)));
        JSONArray jsonArray = new JSONArray(jsonObjectList);
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

    @Test
    void testGetOrderById() {
        MiraHealth.setRunning();
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderGetByIdOK(mockServerClient);
            Mono<HashMap<String, Object>> result = miraSubOrderService.getOrderById("1");
            HashMap<String, Object> response = result.block();
            assert (response != null);
            assertEquals(1, response.get("orderId"));
        }
    }

    @Test
    void testFindOrderByIdAlreadyTimeout() {
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderGetByIdOK(mockServerClient);
            MiraHealth.setStopped();
            Mono<HashMap<String, Object>> result = miraSubOrderService.getOrderById("1");
            assert (result != null);
            assertEquals("failed", Objects.requireNonNull(result.block()).get("status"));
            assertEquals("Context:<Url: http://127.0.0.1:8090/v1/mira/get-by-id?orderId=1>, error:The backend is not available, please try again later.", Objects.requireNonNull(result.block()).get("errorMessage"));
            assertEquals("BackendTimeOut", Objects.requireNonNull(result.block()).get("errorReason"));
        }
    }

    @Test
    void testFindOrderByIdOldTimeout() {
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderGetByIdOK(mockServerClient);
            MiraHealth.setStopped();
            MiraHealth.setFailedTime(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(5))));
            Mono<HashMap<String, Object>> result = miraSubOrderService.getOrderById("1");
            HashMap<String, Object> response = result.block();
            assert response != null;
            assertEquals(1, response.get("orderId"));
            assertEquals(TerminalHealth.RUNNING, TerminalHealth.status);
        }
    }
    @Test
    void testFindByIdTimeOut() {
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockServerClient
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
            MiraHealth.setRunning();
            Mono<HashMap<String,Object>> mreor = miraSubOrderService.getOrderById("1");
            assert mreor.block() != null;
            assertEquals("failed", Objects.requireNonNull(mreor.block()).get("status"));
            String errorMessage = "Context:<Url: http://127.0.0.1:8090/v1/mira/get-by-id?orderId=1>, error:The operation timed out.";
            assertEquals(errorMessage, Objects.requireNonNull(mreor.block()).get("errorMessage"));
        }

    }

    @Test
    void testSetOrder() {
        MiraHealth.setRunning();
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetOK(mockServerClient,orders);
            Mono<HashMap<String, Object>> result = miraSubOrderService.setOrder(orders);
            HashMap<String, Object> response = result.block();
            assert response != null;
            assertEquals(1, response.get("orderId"));
        }
    }
    @Test
    void testSetOrderFallback() {
        MiraHealth.setStopped();
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetOK(mockServerClient,orders);
            mockOrderSetFallback(mockServerClient,orders);
            Mono<HashMap<String, Object>> result = miraSubOrderService.setOrder(orders);
            HashMap<String, Object> response = result.block();
            assert response != null;
            assertEquals("123456", response.get("messageId"));
            assertEquals("fallback", response.get("responseType"));
        }
    }

    @Test
    void testSetOrderFallbackRetry() {
        MiraHealth.setStopped();
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetFailed(mockServerClient);
            mockOrderSetRetry(mockServerClient,orders, 2);
            Mono<HashMap<String, Object>> result = miraSubOrderService.setOrder(orders);
            HashMap<String, Object> response = result.block();
            assert response != null;
            assertEquals("123456", response.get("messageId"));
            assertEquals("fallback", response.get("responseType"));
        }
    }

    @Test
    void testSetOrderFallbackRetryWhenOK() {
        MiraHealth.setRunning();
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetFailed(mockServerClient);
            mockOrderSetRetry(mockServerClient,orders, 2);
            Mono<HashMap<String, Object>> result = miraSubOrderService.setOrder(orders);
            HashMap<String, Object> response = result.block();
            assert response != null;
            assertEquals("123456", response.get("messageId"));
            assertEquals("fallback", response.get("responseType"));
        }
    }
    @Test
    void testSetOrderPanic() {
        MiraHealth.setStopped();
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetFailed(mockServerClient);
            mockOrderSetRetry(mockServerClient,orders, 10);
            Mono<HashMap<String, Object>> result = miraSubOrderService.setOrder(orders);
            try {
                HashMap<String, Object> response = result.block();
                fail(String.format("An exception shall have occurred but somehow not.Response: %s", response));
            } catch (Exception ex) {
                assertEquals("Neither the order service nor its fallback have responded.", ex.getMessage());
            }
        }
    }
}
