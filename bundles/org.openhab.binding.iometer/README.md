# IOmeter Binding

This binding integrates the [IOmeter](https://www.iometer.de/) electrical utility meter reading device.

IOmeter consists of two devices:

- IOmeter Core: attached to the utility meter, transmits readings via a long range 868 MHz RF link.
- IOmeter Bridge: receives the readings from the Core and makes them available on the local network via WiFi.

The binding talks to the IOmeter Bridge's local HTTP API. Live power and energy readings are received in
real time via a Server-Sent Events (SSE) subscription, while the device/meter status is polled at a
configurable interval.

## Supported Things

- `device`: An IOmeter Bridge.

## Discovery

The IOmeter Bridge announces itself on the local network via mDNS under the service type
`_iometer._tcp.local.`. Devices found this way are added to the inbox automatically.

## Thing Configuration

### `device` Thing Configuration

| Name                  | Type    | Description                                                              | Default | Required | Advanced |
|------------------------|---------|---------------------------------------------------------------------------|---------|----------|----------|
| hostname               | text    | Hostname or IP address of the IOmeter Bridge                              | N/A     | yes      | no       |
| statusRefreshInterval  | integer | Interval in seconds the `/v1/status` endpoint is polled                   | 30      | no       | yes      |

Live readings (power, energy) are not affected by `statusRefreshInterval` since they are pushed to the
binding continuously via SSE as soon as they change on the device.

## Channels

| Channel                  | Type              | Read/Write | Description                                                      |
|---------------------------|-------------------|------------|--------------------------------------------------------------------|
| power                     | Number:Power       | R          | Current net power. Positive = consumption, negative = feed-in     |
| power-phase1              | Number:Power       | R          | Power on phase L1                                                  |
| power-phase2              | Number:Power       | R          | Power on phase L2                                                  |
| power-phase3              | Number:Power       | R          | Power on phase L3                                                  |
| energy-import             | Number:Energy      | R          | Cumulative energy consumption counter                              |
| energy-import-tariff1     | Number:Energy      | R          | Cumulative energy consumption counter, tariff 1                    |
| energy-import-tariff2     | Number:Energy      | R          | Cumulative energy consumption counter, tariff 2                    |
| energy-export             | Number:Energy      | R          | Cumulative energy production (feed-in) counter                     |
| bridge-rssi               | Number:Power (dBm)  | R          | WiFi signal strength of the IOmeter Bridge                         |
| core-rssi                 | Number:Power (dBm)  | R          | 868 MHz RF link signal strength of the IOmeter Core                |
| core-connection-status    | String             | R          | RF link status of the IOmeter Core (`connected`, `disconnected`)   |
| core-power-status         | String             | R          | Power supply of the IOmeter Core (`battery`, `wired`)              |
| core-battery-level        | Number:Dimensionless | R        | Battery level of the IOmeter Core, if powered by battery           |
| core-attachment-status    | String             | R          | Whether the IOmeter Core is attached to the meter (`attached`, `detached`) |
| core-pin-status           | String             | R          | Whether the utility meter PIN has been entered (`entered`, `pending`, `missing`) |

In addition to the channels, the thing exposes the meter's `installationId`, `meterNumber`, `deviceId`,
`bridgeVersion` and `coreVersion` as thing properties.

## Full Example

### Thing Configuration

```java
Thing iometer:device:mymeter "IOmeter" [ hostname="192.168.1.100", statusRefreshInterval=30 ]
```

### Item Configuration

```java
Number:Power    IOmeter_Power           "Power [%.1f %unit%]"          { channel="iometer:device:mymeter:power" }
Number:Energy   IOmeter_EnergyImport    "Energy Import [%.0f %unit%]"  { channel="iometer:device:mymeter:energy-import" }
Number:Energy   IOmeter_EnergyExport    "Energy Export [%.0f %unit%]"  { channel="iometer:device:mymeter:energy-export" }
```
