package com.kamvity.samples.order_proxy.mira;

import com.kamvity.samples.order_proxy.config.YAMLConfig;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpServerErrorException;
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
    private final String orderEndpoint;
    private final Integer timeout;

    private final String fallbackOrderEndpoint;
    private final WebClient webClient;

    @Autowired
    public MiraSubOrderService(WebClient webClient, YAMLConfig config) {
        this.webClient = webClient;
        this.orderEndpoint = config.getEndpoints().getMira().getUrl();
        this.timeout = config.getEndpoints().getMira().getTimeout();
        this.fallbackOrderEndpoint = config.getEndpoints().getMira().getFallback().getUrl();
    }
    private static final Map<String,String> errorMap;

    static
    {

        errorMap = new HashMap<>();
        errorMap.put(NoSuchElementException.class.getName(), "Missing parameter(s) while calling the Order API.");
        errorMap.put(TimeoutException.class.getName(),"The operation timed out.");
        errorMap.put(NullPointerException.class.getName(), "An error occurred while during the Order API call. Maybe the order does not exists.");
        errorMap.put(HttpServerErrorException.class.getName(),"An HTTP error occurred during the Order API call while retrieving the id.");
        errorMap.put(RequestNotPermitted.class.getName(),"You are note permitted to retrieve the id.");
        errorMap.put(WebClientRequestException.class.getName(),"An error occurred while during the Order API call. The backend may not be available.");
        errorMap.put(WebClientResponseException.class.getName(),"An error occurred while during the Order API call. The backend may not be available.");
        errorMap.put(Exception.class.getName(),"A generic error has occurred.");
    }

    private Mono<HashMap<String,Object>> createGetOrderFailedResponse(String id, Exception exception) {
        String context = String.format("Url: %s/get-by-id?orderId=%s",orderEndpoint, id);
        String exceptionName = exception.getClass().getName();
        return createFailedResponse(context, errorMap.get(exceptionName), exceptionName, exception.getMessage());
    }
    private Mono<HashMap<String,Object>> createFailedResponse(String context,String errorMessage,String errorReason, String exception) {
        HashMap<String,Object> result = new HashMap<>();
        String error = String.format("Context:<%s>, error:%s",context,errorMessage);
        result.put("status","failed");
        result.put("errorMessage",error);
        result.put("errorReason",errorReason);
        log.error(error);
        log.error("Exception: {}",exception) ;
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
    public Mono<HashMap<String,Object>> getOrderById(String id) {
        if(id.isEmpty()) return createFailedResponse(orderEndpoint,"Received an empty id while getting the order Id.","ParameterError",null);
        log.info("Request received getOrderById with the id {}",id);
        String url = orderEndpoint + "/get-by-id?orderId=" + id;
        if(MiraHealth.status.equals(MiraHealth.RUNNING) ||
                MiraHealth.failedTime.before(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(timeout))))) {
            Mono<HashMap<String,Object>> response = webClient.get().uri(url).accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<>() {});
            MiraHealth.setRunning();
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
    public Mono<HashMap<String,Object>> setOrder(List<HashMap<String,Object>> subOrder) {

        if (subOrder.isEmpty()) return getPanicResult();
        if(MiraHealth.status.equals(MiraHealth.RUNNING) ||
                MiraHealth.failedTime.before(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(timeout))))) {
            String url = orderEndpoint + "/set-order";
            Mono<HashMap<String,Object>> response = webClient.post().uri(url).accept(MediaType.APPLICATION_JSON)
                    .bodyValue(subOrder)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<>() {});
            MiraHealth.setRunning();
            return response;
        }
        return setOrderFallback(subOrder);
    }

    @Override
    public Mono<HashMap<String,Object>> cancelOrderById(Optional<String> id) {
        return null;
    }

    /**
     * Catch exception.
     * @param id id the unique order identifier.
     * @param ex the exception raised by the initial method.
     * @return the order response with the error.
     */

    @SuppressWarnings("unused")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    private Mono<HashMap<String,Object>> getOrderFallback(String id, Exception ex) {
        if(ex.getClass() == TimeoutException.class) {
            MiraHealth.setStopped();
        }
        return createGetOrderFailedResponse(id,ex);
    }

    private Mono<HashMap<String,Object>> getPanicResult() {
        HashMap<String,Object> panicResult = new HashMap<>();
        panicResult.put("status","failed");
        panicResult.put("errorMessage","Sub orders object has not been received.");
        panicResult.put("errorReason","PanicError");
        return Mono.just(panicResult);
    }

    private Mono<HashMap<String,Object>> queueOrder(List<HashMap<String, Object>> subOrder) {
        String url = fallbackOrderEndpoint + "/save";

        return webClient.post().uri(url).accept(MediaType.APPLICATION_JSON)
                    .bodyValue(subOrder)
                    .retrieve()
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals,response -> {
                        log.error("CRITICAL STATUS. Neither the order service nor its fallback have responded.");
                        return Mono.error(new RuntimeException("Neither the order service nor its fallback have responded."));
                    })
                    .bodyToMono(new ParameterizedTypeReference<>() {});

    }

    @Retry(name = CB_ORDER_CONFIG)
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap<String,Object>> setOrderFallback(List<HashMap<String, Object>> subOrder) {
        return queueOrder(subOrder);
    }

    @SuppressWarnings("unused")
    @Retry(name = CB_ORDER_CONFIG)
    @Bulkhead(name = CB_ORDER_CONFIG)
    private Mono<HashMap<String,Object>> setOrderFallback(List<HashMap<String, Object>> subOrder, Exception ex) {
        return queueOrder(subOrder);
    }

}
