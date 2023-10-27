package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.service.ProviderSubOrderService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
@Service
public class MiraSubOrderService implements ProviderSubOrderService {
    private static final Logger log = LoggerFactory.getLogger(MiraSubOrderService.class);
    private static final String CB_ORDER_CONFIG = "orderMiraConfig";

    @Value("${endpoints.mira.url}")
    private String orderEndpoint;
    @Value("${endpoints.mira.timeout}")
    private Integer timeout;
    @Value("${endpoints.mira.fallback.url}")
    private String fallbackOrderEndpoint;

    @Value("${endpoints.mira.fallback.timeout}")
    private Integer fallbackTimeout;

    @Autowired
    protected WebClient webClient;
    private final RestTemplate restTemplate = new RestTemplate();

    private Mono<HashMap> createGetOrderFailedResponse(Optional<String> id,String errorMessage,String errorReason, String exception) {
        if(id.isEmpty()) id = Optional.of("0");
        String context = String.format("Url: %s/get-by-id?orderId=%s",orderEndpoint, id.get());
        return createFailedResponse(context, errorMessage, errorReason, exception);
    }
    private Mono<HashMap> createSetOrderFailedResponse(Optional<List<HashMap>> subOrders,String errorMessage,String errorReason, String exception) {
        if(subOrders.isEmpty()) return createFailedResponse(String.format("Url: %s/get-by-id, Payload: EMPTY",orderEndpoint),errorMessage,errorReason,null);
        String context = String.format("Url: %s, Payload; %s",orderEndpoint, subOrders.get());
        return createFailedResponse(context, errorMessage, errorReason, exception);
    }
    private Mono<HashMap> createFailedResponse(String context,String errorMessage,String errorReason, String exception) {
        HashMap<String,Object> result = new HashMap<>();
        String error = String.format("Context:<%s>, error:%s",context,errorMessage);
        result.put("status","failed");
        result.put("errorMessage",error);
        result.put("errorReason",errorReason);
        log.error(error);
        if (Optional.ofNullable(exception).isPresent()) log.error(String.format("Exception: %s",exception)) ;
        return Mono.just(result);
    }

    /**
     * Get order by its identifier.
     *
     * @param id            : identifier used by the provider.
     * @return the content of the order when it exists.
     */
    @TimeLimiter(name = CB_ORDER_CONFIG, fallbackMethod = "getOrderFallback")
    @CircuitBreaker(name = CB_ORDER_CONFIG, fallbackMethod = "getOrderFallback")
    @Bulkhead(name = CB_ORDER_CONFIG)
    @RateLimiter(name = CB_ORDER_CONFIG, fallbackMethod = "getOrderFallback")
    @Override
    public Mono<HashMap> getOrderById(Optional<String> id) {
        if(id.isEmpty()) return createFailedResponse(orderEndpoint,"Received an empty id while getting the order Id.","ParameterError",null);
        log.info(String.format("Request received getOrderById with the id %s",id.get()));
        String url = orderEndpoint + "/get-by-id?orderId=" + id.get();
        if(MiraHealth.status.equals(MiraHealth.RUNNING) ||
                MiraHealth.failedTime.before(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(timeout))))) {
            Mono<HashMap> response = webClient.get().uri(url).accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(HashMap.class);
            MiraHealth.status = MiraHealth.RUNNING;
            return response;
        }
        String context = String.format("Url: %s",url);
        return createFailedResponse(context,"The backend is not available, please try again later.","BackendTimeOut",null);
    }
    @TimeLimiter(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderFallback")
    @CircuitBreaker(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderFallback")
    @Bulkhead(name = CB_ORDER_CONFIG)
    @RateLimiter(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderFallback")
    @Override
    public Mono<HashMap> setOrder(Optional<List<HashMap>> subOrder) {

        if(MiraHealth.status.equals(MiraHealth.RUNNING) ||
                MiraHealth.failedTime.before(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(timeout))))) {
            String url = orderEndpoint + "/set-order";
            Mono<HashMap> response = webClient.post().uri(url).accept(MediaType.APPLICATION_JSON)
                    .bodyValue(subOrder.get())
                    .retrieve()
                    .bodyToMono(HashMap.class);
            MiraHealth.status = MiraHealth.RUNNING;
            return response;
        }
        return setOrderFallback(subOrder);
    }

    @Override
    public Mono<HashMap> cancelOrderById(Optional<String> id) {
        return null;
    }

    /**
     * Catch no such element exception may be raised when calling the backend.
     * @param id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    private Mono<HashMap> getOrderFallback(Optional<String> id, NoSuchElementException ex) {
        return createGetOrderFailedResponse(id,"Missing parameter(s) while calling the Order API.","BadParameter",null);
    }

    /**
     * Catch timeout exception that may be raised when calling the backend.
     * @param id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    private Mono<HashMap> getOrderFallback(Optional<String> id, TimeoutException ex) {
        MiraHealth.status = MiraHealth.STOPPED;
        MiraHealth.failedTime = Timestamp.from(Instant.now());
        return createGetOrderFailedResponse(id,"The operation timed out.","BackendTimeOut",ex.getMessage());
    }

    /**
     * Catch Null pointer exception that may be raised when calling the backend.
     * @param id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> getOrderFallback(Optional<String> id, NullPointerException ex) {
        return createGetOrderFailedResponse(id,
                "An error occurred while during the Order API call. Maybe the order does not exists",
                "BackendUnavailable",
                ex.getMessage());
    }
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> getOrderFallback(Optional<String> id, HttpServerErrorException ex) {
        return createGetOrderFailedResponse(id,
                "An HTTP error occurred during the Order API call while retrieving the id.",
                "NetworkError",
                ex.getMessage());
    }

    /**
     * Catch the RequestNotPermitted exception that may be raised when reaching the rate limit.
     * @param id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    private Mono<HashMap> getOrderFallback(Optional<String> id, RequestNotPermitted ex) {
        return createGetOrderFailedResponse(id,
                "You are note permitted to retrieve the id.",
                "PermittedError",
                ex.getMessage());
    }

    /**
     * Catch the WebClientRequestException that may be raised by a network connectivity issue.
     * An automatic retry is performed.
     * @param id id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    private Mono<HashMap> getOrderFallback(Optional<String> id, WebClientRequestException ex) {
        return createGetOrderFailedResponse(id,
                "An error occurred while during the Order API call. The backend may not be available.",
                "BackendCallError",
                ex.getMessage());
    }

    /**
     * Catch the WebClientResponseException that may be raised by a backend issue.
     * An automatic retry is performed if the backend has returned an 503.
     * @param id id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> getOrderFallback(Optional<String> id, WebClientResponseException ex) {
        return createGetOrderFailedResponse(id,
                "An client error while receiving the Order API response. The backend may not be available.",
                "ResponseError",
                ex.getMessage());
    }

    /**
     * Catch generic exception.
     * @param id id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> getOrderFallback(Optional<String> id, Exception ex) {
        return createGetOrderFailedResponse(id,
                "A generic error has occurred.",
                "GenericError",
                ex.getMessage());
    }

    /**
     * Fallback exception call without exception.
     * @param id: the identifier of the order.
     * @return the order response with the error.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> getOrderFallback(Optional<String> id) {
        return createGetOrderFailedResponse(id,
                "A generic error has occurred.",
                "GenericError",
                null);
    }

    private Mono<HashMap> queueOrder(Optional<List<HashMap>> subOrder) {
        String url = fallbackOrderEndpoint + "/save";
        return webClient.post().uri(url).accept(MediaType.APPLICATION_JSON)
                .bodyValue(subOrder)
                .retrieve()
                .bodyToMono(HashMap.class);
    }

    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder) {
        return queueOrder(subOrder);
    }

    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, Exception ex) {
        return queueOrder(subOrder);
    }

    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, NoSuchElementException ex) {
        return queueOrder(subOrder);
    }
    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, TimeoutException ex) {
        return queueOrder(subOrder);
    }
    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, NullPointerException ex) {
        return queueOrder(subOrder);
    }
    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, HttpServerErrorException ex) {
        return queueOrder(subOrder);
    }
    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, RequestNotPermitted ex) {
        return queueOrder(subOrder);
    }
    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, WebClientRequestException ex) {
        return queueOrder(subOrder);
    }

    @Retry(name = CB_ORDER_CONFIG, fallbackMethod = "setOrderPanic")
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap> setOrderFallback(Optional<List<HashMap>> subOrder, WebClientResponseException ex) {
        return queueOrder(subOrder);
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap> setOrderPanic(Optional<List<HashMap>> subOrder, Exception ex) {
        return createSetOrderFailedResponse(subOrder,
                "Neither the order service nor its fallback have responded.",
                "PanicError",
                null);
    }
}
