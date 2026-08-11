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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link IometerBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class IometerBindingConstants {

    private static final String BINDING_ID = "iometer";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    // mDNS discovery
    public static final String MDNS_SERVICE_TYPE = "_iometer._tcp.local.";

    // Local HTTP API
    public static final String API_BASE_PATH = "/v1";
    public static final String API_SIMPLE_READING_PATH = API_BASE_PATH + "/reading";
    public static final String API_STATUS_PATH = API_BASE_PATH + "/status";

    // OBIS codes of the /v1/reading registers
    public static final String OBIS_POWER = "01-00:10.07.00*ff";
    public static final String OBIS_POWER_PHASE1 = "01-00:24.07.00*ff";
    public static final String OBIS_POWER_PHASE2 = "01-00:38.07.00*ff";
    public static final String OBIS_POWER_PHASE3 = "01-00:4C.07.00*ff";
    public static final String OBIS_ENERGY_IMPORT = "01-00:01.08.00*ff";
    public static final String OBIS_ENERGY_IMPORT_TARIFF1 = "01-00:01.08.01*ff";
    public static final String OBIS_ENERGY_IMPORT_TARIFF2 = "01-00:01.08.02*ff";
    public static final String OBIS_ENERGY_EXPORT = "01-00:02.08.00*ff";

    // List of all Channel ids
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_POWER_PHASE1 = "power-phase1";
    public static final String CHANNEL_POWER_PHASE2 = "power-phase2";
    public static final String CHANNEL_POWER_PHASE3 = "power-phase3";
    public static final String CHANNEL_ENERGY_IMPORT = "energy-import";
    public static final String CHANNEL_ENERGY_IMPORT_TARIFF1 = "energy-import-tariff1";
    public static final String CHANNEL_ENERGY_IMPORT_TARIFF2 = "energy-import-tariff2";
    public static final String CHANNEL_ENERGY_EXPORT = "energy-export";

    public static final String CHANNEL_BRIDGE_RSSI = "bridge-rssi";
    public static final String CHANNEL_CORE_CONNECTION_STATUS = "core-connection-status";
    public static final String CHANNEL_CORE_RSSI = "core-rssi";
    public static final String CHANNEL_CORE_POWER_STATUS = "core-power-status";
    public static final String CHANNEL_CORE_BATTERY_LEVEL = "core-battery-level";
    public static final String CHANNEL_CORE_ATTACHMENT_STATUS = "core-attachment-status";
    public static final String CHANNEL_CORE_PIN_STATUS = "core-pin-status";

    // Thing properties
    public static final String PROPERTY_INSTALLATION_ID = "installationId";
    public static final String PROPERTY_METER_NUMBER = "meterNumber";
    public static final String PROPERTY_DEVICE_ID = "deviceId";
    public static final String PROPERTY_BRIDGE_VERSION = "bridgeVersion";
    public static final String PROPERTY_CORE_VERSION = "coreVersion";
}
