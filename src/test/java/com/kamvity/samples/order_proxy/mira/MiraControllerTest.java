package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.config.AsyncConfig;
import com.kamvity.samples.order_proxy.config.AsyncTraceContextConfig;
import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@AutoConfigureObservability
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = { OrderProxyConfig.class, AsyncConfig.class, AsyncTraceContextConfig.class})
@ActiveProfiles("dev")
public class MiraControllerTest {
    @Value(value="${local.server.port}")
    private int port = 0;
    @Autowired
    private TestRestTemplate restTemplate;

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
    public void testGetOrderId() {
        mockOrderGetByIdOK();
        String url = "http://localhost:" + port+"/api/v1/mira/orders/get_order_id?id=1";
        ResponseEntity<HashMap> re = restTemplate.getForEntity(url, HashMap.class);
        assertEquals(HttpStatus.OK,re.getStatusCode());
    }
}
