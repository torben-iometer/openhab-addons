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
package org.openhab.binding.iometer.internal.discovery;

import static org.openhab.binding.iometer.internal.IometerBindingConstants.*;

import java.net.Inet4Address;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link IometerMDNSDiscoveryParticipant} discovers IOmeter Bridge devices on the local network
 * via mDNS. The device announces itself under the service type {@code _iometer._tcp.local.}.
 *
 * @author torben-iometer - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "discovery.iometer")
public class IometerMDNSDiscoveryParticipant implements MDNSDiscoveryParticipant {

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Set.of(THING_TYPE_DEVICE);
    }

    @Override
    public String getServiceType() {
        return MDNS_SERVICE_TYPE;
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        Inet4Address[] addresses = service.getInet4Addresses();
        if (addresses.length == 0 || addresses[0] == null) {
            return null;
        }
        return new ThingUID(THING_TYPE_DEVICE, service.getName().replaceAll("[^a-zA-Z0-9_]", "_"));
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        ThingUID thingUID = getThingUID(service);
        if (thingUID == null) {
            return null;
        }

        Inet4Address[] addresses = service.getInet4Addresses();
        String hostAddress = addresses[0].getHostAddress();

        return DiscoveryResultBuilder.create(thingUID).withProperty("hostname", hostAddress)
                .withRepresentationProperty("hostname").withLabel("IOmeter (" + hostAddress + ")").build();
    }
}
