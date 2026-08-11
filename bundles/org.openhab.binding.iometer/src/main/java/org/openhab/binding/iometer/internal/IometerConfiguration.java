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

/**
 * The {@link IometerConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
public class IometerConfiguration {

    /**
     * Hostname or IP address of the IOmeter Bridge.
     */
    public String hostname = "";

    /**
     * Interval in seconds the /v1/status endpoint is polled.
     */
    public int statusRefreshInterval = 30;
}
