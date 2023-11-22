![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mathieu-clnk_order-proxy-api&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=mathieu-clnk_order-proxy-api)
[![Vulnerabilities](https://snyk.io/test/github/mathieu-clnk/order-proxy-api/badge.svg)](https://snyk.io/test/github/mathieu-clnk/order-proxy-api/)


# order-proxy-api

## Overview

The purpose of this microservice is to take a sub-order sent by the `order-management-api` and forward it to the related endpoints.

This service improves the platform resilience and fault tolerance.

## Runtime

This application has been tested with Amazon Corretto 17 version.
The choice of using this version of OpenJDK was made by the [long-term support](https://aws.amazon.com/corretto/faqs/#support) policies of Amazon.
As a consequence all `javax` libraries have been replaced to use `jakarta`.

## Build

The application can be built with the following command:

``` bash
mvn clean compile -DskipTests
```

The unit tests can be executed with:
``` bash
mvn test
```

### Test

### Run