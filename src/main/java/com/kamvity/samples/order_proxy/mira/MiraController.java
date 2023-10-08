package com.kamvity.samples.order_proxy.mira;

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
@RequestMapping("/api/mira/orders")
@RequiredArgsConstructor
public class MiraController {
    private final MiraSubOrderService miraSubOrderService;
    private static final Logger log = LoggerFactory.getLogger(MiraController.class);
    @GetMapping("/get_order_id")
    public Mono<HashMap> getOrderById(@RequestParam Optional<String> id) {
        log.debug(String.format("get_order_id,id=%s",id.get()));
        return miraSubOrderService.getOrderById(id);
    }


}
