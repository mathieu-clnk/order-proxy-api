package com.kamvity.samples.order_proxy.controller;

import com.kamvity.samples.order_proxy.mira.MiraSubOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final MiraSubOrderService miraSubOrderService;
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    @GetMapping("/get_order_id")
    public Mono<HashMap> getOrderById(@RequestParam Optional<String> id) {
        log.info(String.format("Request order info %s",id.get()));
        HashMap<String,Object> result = new HashMap<>();
        //return result;
        return miraSubOrderService.getOrderById(id);
        //return Mono.just(result);
    }
}
