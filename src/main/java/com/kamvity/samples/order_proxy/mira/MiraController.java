package com.kamvity.samples.order_proxy.mira;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/mira/orders")
public class MiraController {
    private final MiraSubOrderService miraSubOrderService;

    @Autowired
    public MiraController(MiraSubOrderService miraSubOrderService) {
        this.miraSubOrderService = miraSubOrderService;
    }
    private static final Logger log = LoggerFactory.getLogger(MiraController.class);
    @GetMapping("/get-order-id")
    public Mono<HashMap<String,Object>> getOrderById(@RequestParam Optional<String> id) {
        if (id.isEmpty()) return missingParameter();
        log.debug("get_order_id,id={}",id.get());
        return miraSubOrderService.getOrderById(id.get());
    }

    @PostMapping("/set-order")
    public Mono<HashMap<String,Object>> setOrder(@RequestBody Optional<List<HashMap<String,Object>>> orders) {
        if (orders.isEmpty()) return  missingParameter();
        return miraSubOrderService.setOrder(orders.get());
    }

    private Mono<HashMap<String,Object>> missingParameter() {
        HashMap<String,Object> error = new HashMap<>();
        error.put("status","failed");
        error.put("errorMessage","Parameter is missing.");
        error.put("errorReason","ParameterError");
        return Mono.just(error);
    }
}
