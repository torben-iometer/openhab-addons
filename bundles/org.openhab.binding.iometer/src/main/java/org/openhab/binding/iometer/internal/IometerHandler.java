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

import static org.openhab.binding.iometer.internal.IometerBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.iometer.internal.connection.IometerSseConnection;
import org.openhab.binding.iometer.internal.dto.ReadingDTO;
import org.openhab.binding.iometer.internal.dto.StatusDTO;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link IometerHandler} connects to an IOmeter Bridge, subscribes to live meter readings via
 * SSE and periodically polls the device status.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class IometerHandler extends BaseThingHandler {

    private static final List<String> DATA_CHANNELS = List.of(CHANNEL_POWER, CHANNEL_POWER_PHASE1, CHANNEL_POWER_PHASE2,
            CHANNEL_POWER_PHASE3, CHANNEL_ENERGY_IMPORT, CHANNEL_ENERGY_IMPORT_TARIFF1, CHANNEL_ENERGY_IMPORT_TARIFF2,
            CHANNEL_ENERGY_EXPORT, CHANNEL_BRIDGE_RSSI, CHANNEL_CORE_CONNECTION_STATUS, CHANNEL_CORE_RSSI,
            CHANNEL_CORE_POWER_STATUS, CHANNEL_CORE_BATTERY_LEVEL, CHANNEL_CORE_ATTACHMENT_STATUS,
            CHANNEL_CORE_PIN_STATUS);

    private final Logger logger = LoggerFactory.getLogger(IometerHandler.class);
    private final HttpClientFactory httpClientFactory;
    private final Gson gson = new Gson();

    private IometerConfiguration config = new IometerConfiguration();
    private @Nullable HttpClient httpClient;
    private @Nullable IometerSseConnection sseConnection;
    private @Nullable ScheduledFuture<?> statusPollJob;

    public IometerHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super(thing);
        this.httpClientFactory = httpClientFactory;
    }

    /**
     * Test seam allowing unit tests to inject a mocked HTTP client without running {@link #initialize()}.
     */
    void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Test seam allowing unit tests to inject a configuration without running {@link #initialize()}.
     */
    void setConfig(IometerConfiguration config) {
        this.config = config;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::pollStatus);
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(IometerConfiguration.class);

        if (config.hostname.isBlank()) {
            setOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Hostname must be configured");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        HttpClient client = httpClientFactory.createHttpClient(httpClientConsumerName());
        this.httpClient = client;
        try {
            client.start();
        } catch (Exception e) {
            setOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Could not start HTTP client: " + e.getMessage());
            return;
        }

        String readingUrl = "http://" + config.hostname + API_SIMPLE_READING_PATH;
        IometerSseConnection connection = new IometerSseConnection(client, readingUrl, scheduler, this::onReadingEvent,
                this::onSseError);
        this.sseConnection = connection;
        connection.connect();

        statusPollJob = scheduler.scheduleWithFixedDelay(this::pollStatus, 0, config.statusRefreshInterval,
                TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> pollJob = statusPollJob;
        if (pollJob != null) {
            pollJob.cancel(true);
            statusPollJob = null;
        }

        IometerSseConnection connection = sseConnection;
        if (connection != null) {
            connection.disconnect();
            sseConnection = null;
        }

        HttpClient client = httpClient;
        if (client != null) {
            try {
                client.stop();
            } catch (Exception e) {
                logger.debug("Error stopping HTTP client for {}", thing.getUID(), e);
            }
            httpClient = null;
        }
    }

    void onReadingEvent(String json) {
        try {
            ReadingDTO reading = gson.fromJson(json, ReadingDTO.class);
            ReadingDTO.Meter meter = reading != null ? reading.meter : null;
            ReadingDTO.Reading meterReading = meter != null ? meter.reading : null;
            List<ReadingDTO.Register> registers = meterReading != null ? meterReading.registers : null;
            if (registers == null) {
                return;
            }
            for (ReadingDTO.Register register : registers) {
                applyRegister(register);
            }

            if (thing.getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Could not parse IOmeter reading event: {}", json, e);
            setOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Could not parse reading event");
        }
    }

    private void applyRegister(ReadingDTO.Register register) {
        String obis = register.obis;
        if (obis == null) {
            return;
        }
        switch (obis) {
            case OBIS_POWER:
                updateState(CHANNEL_POWER, new QuantityType<>(register.value, Units.WATT));
                break;
            case OBIS_POWER_PHASE1:
                updateState(CHANNEL_POWER_PHASE1, new QuantityType<>(register.value, Units.WATT));
                break;
            case OBIS_POWER_PHASE2:
                updateState(CHANNEL_POWER_PHASE2, new QuantityType<>(register.value, Units.WATT));
                break;
            case OBIS_POWER_PHASE3:
                updateState(CHANNEL_POWER_PHASE3, new QuantityType<>(register.value, Units.WATT));
                break;
            case OBIS_ENERGY_IMPORT:
                updateState(CHANNEL_ENERGY_IMPORT, new QuantityType<>(register.value, Units.WATT_HOUR));
                break;
            case OBIS_ENERGY_IMPORT_TARIFF1:
                updateState(CHANNEL_ENERGY_IMPORT_TARIFF1, new QuantityType<>(register.value, Units.WATT_HOUR));
                break;
            case OBIS_ENERGY_IMPORT_TARIFF2:
                updateState(CHANNEL_ENERGY_IMPORT_TARIFF2, new QuantityType<>(register.value, Units.WATT_HOUR));
                break;
            case OBIS_ENERGY_EXPORT:
                updateState(CHANNEL_ENERGY_EXPORT, new QuantityType<>(register.value, Units.WATT_HOUR));
                break;
            default:
                logger.trace("Ignoring unmapped IOmeter OBIS register {}", obis);
        }
    }

    private String httpClientConsumerName() {
        String consumerName = ("iometer-" + thing.getUID().getId()).replaceAll("[^a-zA-Z0-9_-]", "_");
        return consumerName.length() > 20 ? consumerName.substring(0, 20) : consumerName;
    }

    private void onSseError(Throwable error) {
        logger.debug("IOmeter SSE connection error for {}: {}", thing.getUID(), error.getMessage());
        setOffline(ThingStatusDetail.COMMUNICATION_ERROR, "SSE connection error: " + error.getMessage());
    }

    /**
     * Marks the thing OFFLINE and resets all data channels to {@code UNDEF} so that stale values are
     * neither shown in the UI nor tracked by persistence while the device is unreachable.
     */
    private void setOffline(ThingStatusDetail detail, @Nullable String description) {
        DATA_CHANNELS.forEach(channelId -> updateState(channelId, UnDefType.UNDEF));
        updateStatus(ThingStatus.OFFLINE, detail, description);
    }

    void pollStatus() {
        HttpClient client = httpClient;
        if (client == null) {
            return;
        }

        String statusUrl = "http://" + config.hostname + API_STATUS_PATH;
        try {
            ContentResponse response = client.newRequest(statusUrl).method(HttpMethod.GET).timeout(10, TimeUnit.SECONDS)
                    .send();
            if (response.getStatus() == 200) {
                StatusDTO status = gson.fromJson(response.getContentAsString(), StatusDTO.class);
                if (status != null) {
                    applyStatus(status);
                }
                updateStatus(ThingStatus.ONLINE);
            } else {
                setOffline(ThingStatusDetail.COMMUNICATION_ERROR, "HTTP status " + response.getStatus());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (JsonSyntaxException e) {
            logger.debug("Could not parse IOmeter status response", e);
            setOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Could not parse status response");
        } catch (Exception e) {
            setOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    void applyStatus(StatusDTO status) {
        Map<String, String> properties = new HashMap<>(editProperties());
        String installationId = status.installationId;
        if (installationId != null) {
            properties.put(PROPERTY_INSTALLATION_ID, installationId);
        }
        StatusDTO.Meter meter = status.meter;
        String meterNumber = meter != null ? meter.number : null;
        if (meterNumber != null) {
            properties.put(PROPERTY_METER_NUMBER, meterNumber);
        }

        StatusDTO.Device device = status.device;
        if (device != null) {
            String deviceId = device.id;
            if (deviceId != null) {
                properties.put(PROPERTY_DEVICE_ID, deviceId);
            }

            StatusDTO.Bridge bridge = device.bridge;
            if (bridge != null) {
                String bridgeVersion = bridge.version;
                if (bridgeVersion != null) {
                    properties.put(PROPERTY_BRIDGE_VERSION, bridgeVersion);
                }
                updateState(CHANNEL_BRIDGE_RSSI, toRssiState(bridge.rssi));
            }

            StatusDTO.Core core = device.core;
            if (core != null) {
                String coreVersion = core.version;
                if (coreVersion != null) {
                    properties.put(PROPERTY_CORE_VERSION, coreVersion);
                }
                updateState(CHANNEL_CORE_CONNECTION_STATUS, toStringState(core.connectionStatus));
                updateState(CHANNEL_CORE_RSSI, toRssiState(core.rssi));
                updateState(CHANNEL_CORE_POWER_STATUS, toStringState(core.powerStatus));
                Integer batteryLevel = core.batteryLevel;
                updateState(CHANNEL_CORE_BATTERY_LEVEL,
                        batteryLevel != null ? new QuantityType<>(batteryLevel, Units.PERCENT) : UnDefType.UNDEF);
                updateState(CHANNEL_CORE_ATTACHMENT_STATUS, toStringState(core.attachmentStatus));
                updateState(CHANNEL_CORE_PIN_STATUS, toStringState(core.pinStatus));
            }
        }

        updateProperties(properties);
    }

    private State toRssiState(@Nullable Integer rssi) {
        return rssi != null ? new QuantityType<>(rssi, Units.DECIBEL_MILLIWATTS) : UnDefType.UNDEF;
    }

    private State toStringState(@Nullable String value) {
        return value != null ? new StringType(value) : UnDefType.UNDEF;
    }
}
