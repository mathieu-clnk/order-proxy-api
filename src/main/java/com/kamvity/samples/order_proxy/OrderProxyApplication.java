package com.kamvity.samples.order_proxy;

import com.kamvity.samples.order_proxy.health.TerminalHealth;
import com.kamvity.samples.order_proxy.mira.MiraHealth;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Hooks;

import java.time.Duration;
@SuppressWarnings("unused")
@SpringBootApplication
public class OrderProxyApplication {

    protected WebClient webClient;
    public static void main(String[] args) {
        SpringApplication.run(OrderProxyApplication.class,args);
        Hooks.enableAutomaticContextPropagation();
    }
    @Bean
    public WebClient webClientInitializer() {
        return WebClient.create();
    }

    @Bean
    public CircuitBreakerConfigCustomizer testCustomizer() {

        return CircuitBreakerConfigCustomizer
                .of("orderMiraConfig", builder -> builder.slidingWindowSize(100)
                        .waitDurationInOpenState(Duration.ofSeconds(5)));
    }
    @Bean
    public static void checkBackendsHealth() {
        TerminalHealth.status = TerminalHealth.RUNNING;
        MiraHealth.setRunning();
    }
}
