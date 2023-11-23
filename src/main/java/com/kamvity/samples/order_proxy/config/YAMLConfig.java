package com.kamvity.samples.order_proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("ALL")
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties
public class YAMLConfig {

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    private Endpoints endpoints = new Endpoints();
    public class Endpoints {
        public Mira getMira() {
            return mira;
        }

        public void setMira(Mira mira) {
            this.mira = mira;
        }

        private Mira mira = new Mira();
    }

    public class Mira {
        public Read getRead() {
            return read;
        }

        public void setRead(Read read) {
            this.read = read;
        }

        private Read read = new Read();

        public Write getWrite() {
            return write;
        }

        public void setWrite(Write write) {
            this.write = write;
        }

        private Write write = new Write();
    }

    public abstract class Endpoint {

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        private String url;

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        private Integer timeout;

    }

    public class Read extends Endpoint{

    }
    public class Write extends Endpoint{

        public Fallback getFallback() {
            return fallback;
        }

        public void setFallback(Fallback fallback) {
            this.fallback = fallback;
        }

        private Fallback fallback = new Fallback();

    }

    public class Fallback extends Endpoint{

    }
}
