package com.kamvity.samples.order_proxy.controller;

import com.kamvity.samples.order_proxy.config.OrderProxyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = OrderProxyConfig.class)
//@EnabledIfEnvironmentVariable(named = "TEST_STAGE", matches = "integration")
@ActiveProfiles("dev")
public class OrderControllerTest {

    @Value(value="${local.server.port}")
    private int port = 0;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testGetOrderId() {
        String url = "http://localhost:" + port+"/api/orders/get_order_id?id=1";
        ResponseEntity<HashMap> re = restTemplate.getForEntity(url, HashMap.class);
        assertEquals(HttpStatus.OK,re.getStatusCode());
    }
}
