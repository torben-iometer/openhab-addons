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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link ReadingDTO} maps the JSON response of the {@code GET /v1/reading} endpoint. Registers
 * that are not supported by the connected meter are omitted from the response entirely.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class ReadingDTO {
    public @Nullable String installationId;
    public @Nullable Meter meter;

    public static class Meter {
        public @Nullable String number;
        public @Nullable Reading reading;
    }

    public static class Reading {
        public @Nullable String time;
        public @Nullable List<Register> registers;
    }

    public static class Register {
        public @Nullable String obis;
        public double value;
        public @Nullable String unit;
    }
}
