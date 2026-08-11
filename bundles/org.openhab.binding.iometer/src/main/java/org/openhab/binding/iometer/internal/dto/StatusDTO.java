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
package org.openhab.binding.iometer.internal.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link StatusDTO} maps the JSON response of the {@code GET /v1/status} endpoint.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class StatusDTO {
    public @Nullable String installationId;
    public @Nullable Meter meter;
    public @Nullable Device device;

    public static class Meter {
        public @Nullable String number;
    }

    public static class Device {
        public @Nullable String id;
        public @Nullable Bridge bridge;
        public @Nullable Core core;
    }

    public static class Bridge {
        public @Nullable Integer rssi;
        public @Nullable String version;
    }

    public static class Core {
        public @Nullable String connectionStatus;
        public @Nullable Integer rssi;
        public @Nullable String version;
        public @Nullable String powerStatus;
        public @Nullable Integer batteryLevel;
        public @Nullable String attachmentStatus;
        public @Nullable String pinStatus;
    }
}
