/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.iometer.internal.connection;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IometerSseConnection}. The {@code scheduler} is mocked so that the blocking
 * stream-reading loop started by {@code connect()} is never actually executed - only the
 * connect/disconnect lifecycle (request creation, headers, abort-on-disconnect) is verified.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
class IometerSseConnectionTest {

    private static final String URL = "http://192.168.1.50/v1/reading";

    /**
     * Bundles the mocks needed to exercise a freshly constructed {@link IometerSseConnection}.
     */
    private record Fixture(HttpClient httpClient, Request request, IometerSseConnection connection) {
    }

    @SuppressWarnings("unchecked")
    private static Fixture newFixture() {
        HttpClient httpClient = mock(HttpClient.class);
        Request request = mock(Request.class, withSettings().defaultAnswer(RETURNS_SELF));
        when(httpClient.newRequest(URL)).thenReturn(request);

        // a mocked scheduler never actually runs the submitted Runnable, so the blocking
        // stream-reading loop is never entered during these tests
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        Consumer<String> eventListener = mock(Consumer.class);
        Consumer<Throwable> errorListener = mock(Consumer.class);
        IometerSseConnection connection = new IometerSseConnection(httpClient, URL, scheduler, eventListener,
                errorListener);

        return new Fixture(httpClient, request, connection);
    }

    @Test
    void connectSendsGetRequestWithSseAcceptHeader() {
        Fixture fx = newFixture();

        fx.connection().connect();

        verify(fx.httpClient()).newRequest(URL);
        verify(fx.request()).method(HttpMethod.GET);
        verify(fx.request()).header(HttpHeader.ACCEPT, "text/event-stream");
        verify(fx.request()).send(any(InputStreamResponseListener.class));
    }

    @Test
    void connectCalledTwiceOnlyConnectsOnce() {
        Fixture fx = newFixture();

        fx.connection().connect();
        fx.connection().connect();

        verify(fx.httpClient(), times(1)).newRequest(URL);
    }

    @Test
    void disconnectWithoutConnectDoesNothing() {
        Fixture fx = newFixture();

        fx.connection().disconnect();

        verifyNoInteractions(fx.httpClient());
        verifyNoInteractions(fx.request());
    }

    @Test
    void disconnectAfterConnectAbortsPendingRequest() {
        Fixture fx = newFixture();

        fx.connection().connect();
        fx.connection().disconnect();

        verify(fx.request()).abort(any(IOException.class));
    }

    @Test
    void disconnectIsIdempotent() {
        Fixture fx = newFixture();

        fx.connection().connect();
        fx.connection().disconnect();
        fx.connection().disconnect();

        verify(fx.request(), times(1)).abort(any(IOException.class));
    }

    @Test
    void connectAfterDisconnectOpensNewRequest() {
        Fixture fx = newFixture();

        fx.connection().connect();
        fx.connection().disconnect();
        fx.connection().connect();

        verify(fx.httpClient(), times(2)).newRequest(URL);
    }
}
