package com.erp.gateway.error;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;

/**
 * Maps downstream connection failures to HTTP 503 so clients get a stable JSON error instead of 500 + stack traces.
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = DownstreamErrors.unwrap(getError(request));
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        if (DownstreamErrors.isDownstreamFailure(error)) {
            attributes.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            attributes.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
            attributes.put(
                    "message",
                    "Downstream service is unavailable or closed the connection. Start IAM / entity-builder or check gateway URIs.");
        }
        return attributes;
    }
}
