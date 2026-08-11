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
package org.openhab.binding.iometer.internal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.iometer.internal.IometerBindingConstants.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;

/**
 * Tests for {@link IometerHandler}.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
class IometerHandlerTest {

    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_DEVICE, "test-device");

    private static final String READING_JSON = """
            {
              "installationId": "install-1",
              "meter": {
                "number": "meter-1",
                "reading": {
                  "time": "2026-08-11T12:00:00Z",
                  "registers": [
                    {"obis": "01-00:10.07.00*ff", "value": 512.5, "unit": "W"},
                    {"obis": "01-00:24.07.00*ff", "value": 170.1, "unit": "W"},
                    {"obis": "01-00:38.07.00*ff", "value": 171.2, "unit": "W"},
                    {"obis": "01-00:4C.07.00*ff", "value": 171.2, "unit": "W"},
                    {"obis": "01-00:01.08.00*ff", "value": 12345.6, "unit": "Wh"},
                    {"obis": "01-00:01.08.01*ff", "value": 6000.0, "unit": "Wh"},
                    {"obis": "01-00:01.08.02*ff", "value": 6345.6, "unit": "Wh"},
                    {"obis": "01-00:02.08.00*ff", "value": 42.0, "unit": "Wh"},
                    {"obis": "99-00:99.99.99*ff", "value": 1.0, "unit": "Wh"}
                  ]
                }
              }
            }
            """;

    private static final String STATUS_JSON = """
            {
              "installationId": "install-1",
              "meter": {"number": "meter-1"},
              "device": {
                "id": "device-1",
                "bridge": {"rssi": -55, "version": "1.2.3"},
                "core": {
                  "connectionStatus": "connected",
                  "rssi": -60,
                  "version": "4.5.6",
                  "powerStatus": "battery",
                  "batteryLevel": 87,
                  "attachmentStatus": "attached",
                  "pinStatus": "ok"
                }
              }
            }
            """;

    private static final String STATUS_JSON_MINIMAL = """
            {
              "installationId": "install-1",
              "device": {
                "id": "device-1",
                "core": {"connectionStatus": "connected"}
              }
            }
            """;

    /**
     * Bundles the mocks needed to exercise a freshly constructed {@link IometerHandler} without
     * running the full {@code initialize()} lifecycle.
     */
    private record Fixture(Thing thing, HttpClientFactory httpClientFactory, ThingHandlerCallback callback,
            IometerHandler handler) {
    }

    private static Fixture newFixture() {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(THING_UID);
        HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);

        IometerHandler handler = new IometerHandler(thing, httpClientFactory);
        handler.setCallback(callback);

        return new Fixture(thing, httpClientFactory, callback, handler);
    }

    private static ChannelUID channel(String id) {
        return new ChannelUID(THING_UID, id);
    }

    @Test
    void initializeWithBlankHostnameSetsConfigurationError() {
        Fixture fx = newFixture();
        when(fx.thing().getConfiguration()).thenReturn(new Configuration());

        fx.handler().initialize();

        verify(fx.callback()).statusUpdated(eq(fx.thing()), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
        verifyNoInteractions(fx.httpClientFactory());
    }

    @Test
    void onReadingEventWithValidJsonUpdatesChannelsAndSetsOnline() {
        Fixture fx = newFixture();

        fx.handler().onReadingEvent(READING_JSON);

        verify(fx.callback()).stateUpdated(channel(CHANNEL_POWER), new QuantityType<>(512.5, Units.WATT));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_POWER_PHASE1), new QuantityType<>(170.1, Units.WATT));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_POWER_PHASE2), new QuantityType<>(171.2, Units.WATT));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_POWER_PHASE3), new QuantityType<>(171.2, Units.WATT));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_ENERGY_IMPORT),
                new QuantityType<>(12345.6, Units.WATT_HOUR));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_ENERGY_IMPORT_TARIFF1),
                new QuantityType<>(6000.0, Units.WATT_HOUR));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_ENERGY_IMPORT_TARIFF2),
                new QuantityType<>(6345.6, Units.WATT_HOUR));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_ENERGY_EXPORT), new QuantityType<>(42.0, Units.WATT_HOUR));
        // the unmapped OBIS register must not produce any additional channel update
        verify(fx.callback(), times(8)).stateUpdated(any(), any());

        verify(fx.callback()).statusUpdated(eq(fx.thing()), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void onReadingEventWithOnlyUnmappedObisUpdatesNoChannel() {
        Fixture fx = newFixture();

        fx.handler().onReadingEvent("""
                {"meter":{"reading":{"registers":[{"obis":"99-00:99.99.99*ff","value":1.0}]}}}
                """);

        verify(fx.callback(), never()).stateUpdated(any(), any());
        verify(fx.callback()).statusUpdated(eq(fx.thing()), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void onReadingEventWithMalformedJsonDoesNotUpdateOrThrow() {
        Fixture fx = newFixture();

        fx.handler().onReadingEvent("{not valid json");

        verifyNoInteractions(fx.callback());
    }

    @Test
    void onReadingEventWithoutRegistersDoesNothing() {
        Fixture fx = newFixture();

        fx.handler().onReadingEvent("""
                {"meter":{"reading":{}}}
                """);

        verifyNoInteractions(fx.callback());
    }

    @Test
    void pollStatusWithHttpOkUpdatesPropertiesChannelsAndSetsOnline() throws Exception {
        Fixture fx = newFixture();
        fx.handler().setHttpClient(mockHttpClientReturning(200, STATUS_JSON));
        fx.handler().setConfig(configWithHostname());
        when(fx.thing().getProperties()).thenReturn(Map.of());

        fx.handler().pollStatus();

        verify(fx.thing()).setProperty(PROPERTY_INSTALLATION_ID, "install-1");
        verify(fx.thing()).setProperty(PROPERTY_METER_NUMBER, "meter-1");
        verify(fx.thing()).setProperty(PROPERTY_DEVICE_ID, "device-1");
        verify(fx.thing()).setProperty(PROPERTY_BRIDGE_VERSION, "1.2.3");
        verify(fx.thing()).setProperty(PROPERTY_CORE_VERSION, "4.5.6");
        verify(fx.callback()).thingUpdated(fx.thing());

        verify(fx.callback()).stateUpdated(channel(CHANNEL_BRIDGE_RSSI),
                new QuantityType<>(-55, Units.DECIBEL_MILLIWATTS));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_CONNECTION_STATUS), new StringType("connected"));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_RSSI),
                new QuantityType<>(-60, Units.DECIBEL_MILLIWATTS));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_POWER_STATUS), new StringType("battery"));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_BATTERY_LEVEL), new QuantityType<>(87, Units.PERCENT));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_ATTACHMENT_STATUS), new StringType("attached"));
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_PIN_STATUS), new StringType("ok"));

        verify(fx.callback()).statusUpdated(eq(fx.thing()), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void pollStatusWithHttpErrorSetsOfflineCommunicationError() throws Exception {
        Fixture fx = newFixture();
        fx.handler().setHttpClient(mockHttpClientReturning(500, ""));
        fx.handler().setConfig(configWithHostname());

        fx.handler().pollStatus();

        verify(fx.callback()).statusUpdated(eq(fx.thing()),
                eq(new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "HTTP status 500")));
        verify(fx.callback(), never()).stateUpdated(any(), any());
        verify(fx.thing(), never()).setProperty(any(), any());
    }

    @Test
    void pollStatusWithMissingOptionalFieldsSetsUndefStatesWithoutThrowing() throws Exception {
        Fixture fx = newFixture();
        fx.handler().setHttpClient(mockHttpClientReturning(200, STATUS_JSON_MINIMAL));
        fx.handler().setConfig(configWithHostname());
        when(fx.thing().getProperties()).thenReturn(Map.of());

        fx.handler().pollStatus();

        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_RSSI), UnDefType.UNDEF);
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_BATTERY_LEVEL), UnDefType.UNDEF);
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_POWER_STATUS), UnDefType.UNDEF);
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_ATTACHMENT_STATUS), UnDefType.UNDEF);
        verify(fx.callback()).stateUpdated(channel(CHANNEL_CORE_PIN_STATUS), UnDefType.UNDEF);
        verify(fx.callback(), never()).stateUpdated(eq(channel(CHANNEL_BRIDGE_RSSI)), any());

        verify(fx.callback()).statusUpdated(eq(fx.thing()), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void handleCommandWithRefreshTypeTriggersStatusPoll() throws Exception {
        Fixture fx = newFixture();
        fx.handler().setHttpClient(mockHttpClientReturning(200, STATUS_JSON));
        fx.handler().setConfig(configWithHostname());
        when(fx.thing().getProperties()).thenReturn(Map.of());

        fx.handler().handleCommand(channel(CHANNEL_POWER), RefreshType.REFRESH);

        verify(fx.callback(), timeout(2000)).statusUpdated(eq(fx.thing()),
                argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    private static IometerConfiguration configWithHostname() {
        IometerConfiguration config = new IometerConfiguration();
        config.hostname = "192.168.1.50";
        return config;
    }

    private static HttpClient mockHttpClientReturning(int statusCode, String content) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        Request request = mock(Request.class, withSettings().defaultAnswer(RETURNS_SELF));
        ContentResponse response = mock(ContentResponse.class);

        when(httpClient.newRequest(anyString())).thenReturn(request);
        when(request.timeout(anyLong(), any(TimeUnit.class))).thenReturn(request);
        when(request.method(any(HttpMethod.class))).thenReturn(request);
        when(request.send()).thenReturn(response);
        when(response.getStatus()).thenReturn(statusCode);
        when(response.getContentAsString()).thenReturn(content);

        return httpClient;
    }
}
