package com.kamvity.samples.order_proxy.service;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Optional;

public class OrderService {

    /**
     * Get order with the sub-order(s) by its identifier.
     * @param id: the identifier of the order.
     * @return order and sub-order(s) information.
     */
    public Mono<HashMap> getOrderById(Optional<String> id) {
        return null;
    }
}
