package com.kamvity.samples.order_proxy.service;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Each provider receives order from the order management system.
 * These orders are identified as sub-order as the initial order from the customer may be divided in multiple sub-orders,
 * depending on the product's provider.
 * The implementation of this interface varies of the provider.
 * The fallback, exceptions management and retries policies are different between providers.
 */
public interface ProviderSubOrderService {

    /**
     * Find a sub-order by its identifier from the provider´s system.
     *
     * @param id            : identifier used by the provider.
     * @return the sub-order when it has been found.
     */
    public Mono<HashMap> getOrderById(Optional<String> id);

    /**
     * Place a sub-order into the provider's system.
     * @param subOrder: The sub-order to send.
     * @return the result of the operation and the identifier when it has succeeded.
     */
    public Mono<HashMap> setOrder(Optional<List<HashMap>> subOrder);

    /**
     * Cancel a sub-order by its id.
     * @param id: identifier used by the provider.
     * @return the result of the operation.
     */
    public Mono<HashMap> cancelOrderById(Optional<String> id);

}
