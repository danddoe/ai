package com.erp.gateway.error;

import reactor.core.Exceptions;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;

/**
 * Detects transport failures while proxying to a fixed URI (IAM, entity-builder, etc.).
 * Spring Cloud Gateway would otherwise surface these as 500 with a raw stack trace.
 */
public final class DownstreamErrors {

    private DownstreamErrors() {}

    public static Throwable unwrap(Throwable t) {
        if (t == null) {
            return null;
        }
        Throwable u = Exceptions.unwrap(t);
        return u != null ? u : t;
    }

    public static boolean isDownstreamFailure(Throwable t) {
        Throwable cur = unwrap(t);
        while (cur != null) {
            if (cur instanceof ConnectException
                    || cur instanceof SocketException
                    || cur instanceof UnknownHostException
                    || cur instanceof NoRouteToHostException
                    || cur instanceof ClosedChannelException) {
                return true;
            }
            String name = cur.getClass().getName();
            if (name.equals("reactor.netty.http.client.PrematureCloseException")
                    || name.equals("io.netty.handler.timeout.ReadTimeoutException")
                    || name.endsWith("AnnotatedConnectException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
