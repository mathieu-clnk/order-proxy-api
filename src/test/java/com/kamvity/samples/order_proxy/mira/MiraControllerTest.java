package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.config.AsyncConfig;
import com.kamvity.samples.order_proxy.config.AsyncTraceContextConfig;
import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import com.kamvity.samples.order_proxy.config.YAMLConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@AutoConfigureObservability
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = { OrderProxyConfig.class, AsyncConfig.class, AsyncTraceContextConfig.class, YAMLConfig.class})
@ActiveProfiles("dev")
public class MiraControllerTest {
    //@Value(value="${local.server.port}")
    @SuppressWarnings("unused")
    @LocalServerPort
    private int port;
    @SuppressWarnings("unused")
    @Autowired
    private TestRestTemplate restTemplate;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startServer() {
        mockServer = startClientAndServer(8090);
    }


    @BeforeEach
    public void reset() {
        mockServer.reset();
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


    @SuppressWarnings("resource")
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

    public void mockOrderSetOK(MockServerClient mockServerClient,List<HashMap<String,Object>> orders) {
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


    @Test
    void testGetOrderId() {
        mockOrderGetByIdOK();
        String url = "http://localhost:" + port+"/api/v1/mira/orders/get-order-id?id=1";
        ResponseEntity<HashMap<String, Object>> re = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK,re.getStatusCode());
        assertEquals(1, Objects.requireNonNull(re.getBody()).get("orderId"));
    }

    @Test
    void testSetOrderId() {

        String url = "http://localhost:" + port+"/api/v1/mira/orders/set-order";
        List<HashMap<String,Object>> orders = new ArrayList<>();
        HashMap<String,Object> order = new HashMap<>();
        order.put("productId","123");
        order.put("quantity","1");
        order.put("deliveryDueDate","2023-10-23");
        orders.add(order);
        try(MockServerClient mockServerClient = new MockServerClient("127.0.0.1",8090)) {
            mockOrderSetOK(mockServerClient, orders);
            HttpEntity<List<HashMap<String, Object>>> ordersEntity = new HttpEntity<>(orders);
            ResponseEntity<HashMap<String, Object>> re = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    ordersEntity,
                    new ParameterizedTypeReference<>() {
                    });
            assertEquals(HttpStatus.OK, re.getStatusCode());
            assertEquals(1, Objects.requireNonNull(re.getBody()).get("orderId"));
        }
    }
}
