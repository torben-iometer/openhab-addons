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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IometerSseConnection} connects to an IOmeter SSE endpoint (e.g. {@code /v1/json}) and
 * forwards the payload of every {@code data:} line to a listener. The connection is automatically
 * re-established with a fixed retry delay whenever it is closed unexpectedly.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class IometerSseConnection {

    private static final long RECONNECT_DELAY_SECONDS = 10;
    private static final long IDLE_TIMEOUT_SECONDS = 60;

    private final Logger logger = LoggerFactory.getLogger(IometerSseConnection.class);

    private final HttpClient httpClient;
    private final String url;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> eventListener;
    private final Consumer<Throwable> errorListener;

    private volatile boolean active;
    private @Nullable Request currentRequest;

    public IometerSseConnection(HttpClient httpClient, String url, ScheduledExecutorService scheduler,
            Consumer<String> eventListener, Consumer<Throwable> errorListener) {
        this.httpClient = httpClient;
        this.url = url;
        this.scheduler = scheduler;
        this.eventListener = eventListener;
        this.errorListener = errorListener;
    }

    /**
     * Opens the SSE connection. Safe to call multiple times, subsequent calls while already
     * connecting/connected are ignored.
     */
    public synchronized void connect() {
        if (active) {
            return;
        }
        active = true;
        connectInternal();
    }

    /**
     * Closes the SSE connection and stops any pending reconnect attempt.
     */
    public synchronized void disconnect() {
        active = false;
        Request request = currentRequest;
        if (request != null) {
            request.abort(new IOException("IOmeter SSE connection closed by binding"));
            currentRequest = null;
        }
    }

    private void connectInternal() {
        if (!active) {
            return;
        }
        logger.debug("Connecting to IOmeter SSE endpoint {}", url);
        Request request = httpClient.newRequest(url).method(HttpMethod.GET)
                .header(HttpHeader.ACCEPT, "text/event-stream").timeout(0, TimeUnit.SECONDS)
                .idleTimeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        InputStreamResponseListener listener = new InputStreamResponseListener();
        currentRequest = request;
        request.send(listener);
        scheduler.execute(() -> readEvents(listener));
    }

    private void readEvents(InputStreamResponseListener listener) {
        try (InputStream inputStream = listener.getInputStream()) {
            logger.debug("Connected to IOmeter SSE endpoint {}", url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder data = new StringBuilder();
            String line;
            while (active && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        eventListener.accept(data.toString());
                        data.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
                // other SSE fields (event:, id:, comments starting with ':') are not used by IOmeter
            }
        } catch (Exception e) {
            if (active) {
                errorListener.accept(e);
            }
        } finally {
            currentRequest = null;
            if (active) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        logger.debug("IOmeter SSE connection to {} lost, reconnecting in {}s", url, RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::connectInternal, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
