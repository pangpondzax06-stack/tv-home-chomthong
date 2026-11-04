# Air & Weather

The weather and the PM2.5 for where the TV is, refreshed every hour, for the [SkyGlass](../../FORK.md) Home: it hands
the launcher one row through a content provider, and the launcher draws it under the pills, with a sky, rain and
PM2.5 haze behind Home. Opened from its tile it is a full page: now, the next twelve hours and the next seven days.

- **Where:** automatic, from this network's internet address (ipwho.is, then ipapi.co), which is what "the Wi-Fi's
  location" can mean while the TV's own location setting is off. **Change location** searches by name (Open-Meteo
  geocoding) when the automatic place is a district off; **Use automatic location** goes back.
- **PM2.5 now:** measured at the nearest [Air4Thai](http://air4thai.pcd.go.th) station (Thailand's Pollution Control
  Department) within 50 km with a reading at most three hours old; elsewhere the CAMS model through Open-Meteo.
- **PM2.5 ahead:** CAMS reads low over Thai cities (7.9 µg/m³ where a Bangkok station measured 18.7), so near a station
  the hourly and daily forecast is scaled by how far the model was off at the station's own hour,
  `(station + 2) / (model + 2)`, kept between 0.25 and 4, fading halfway back to 1 every 12 hours (`Air.java`).
- **Bands:** Thailand's PCD scale since June 2023: ≤15 very good, ≤25 good, ≤37.5 moderate, ≤75 starting to affect
  health, above that affects health. From moderate up, Home shows the reading on its band's colour and
  **"😷 Don't forget to wear a mask"**.
- **Rain:** the chance for this hour, and the first of the next twelve at 50 % or more ("likely at 13:00 (63%)").
- **Weather:** Open-Meteo, now, hourly and seven days, with sunrise and sunset for the sky on Home.
- English and Thai, following the TV's language.

## Switches (on the page)

| Button | What it does |
|---|---|
| Home sky & haze | The launcher's sky, clouds, rain, lightning and PM2.5 haze. Off, Home draws no extra frames at all. |
| Mask reminder | The mask line on Home and on the page past the green band. |

## Refresh

A `JobScheduler` job every hour with a network (persisted across reboots), no service of its own. While the TV
sleeps the system holds the job back; when Home comes back and reads a row older than 65 minutes, the provider asks
for a refresh straight away (at most every five minutes).

## The provider

`content://tv.home.airweather.now`, read only, one row. Columns: `weather`, `air`, `air_color`, `place`, `updated`,
`temp`, `emoji`, `sky`, `feels_text`, `rain_text`, `rain_alert`, `air_text`, `pm25`, `pm_band`, `mask_text`, `code`,
`is_day`, `sunrise`, `sunset`, `effects`. Texts are already in the TV's language; the launcher only lays them out.

Over ADB only (the call checks for the shell's uid), ten minutes of other weather on Home, to see how it looks:

```bash
TV_HOST=<tv ip> tools/tv.py "content call --uri content://tv.home.airweather.now --method preview --arg 'pm=95,code=95,phase=sunset'"
TV_HOST=<tv ip> tools/tv.py "content call --uri content://tv.home.airweather.now --method preview"   # back to real
```

`phase` is `dawn`, `day`, `sunset`, `dusk` or `night`; `code` a WMO weather code (0 clear, 3 cloudy, 63 rain,
95 thunderstorm).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | The place from the internet address, the place search, Open-Meteo and Air4Thai. |
| `RECEIVE_BOOT_COMPLETED` | Only so the hourly job survives a reboot. |

What leaves the TV: the network's public address (every website sees it), a typed place name when searching, then
the place's latitude and longitude. No account, no telemetry, no location permission. The launcher itself still has
no network permission: it only reads this provider.

## Build and install

JDK 17, Android SDK platform 34 and build-tools 34.0.0, as for the launcher:

```bash
cd addons/air-weather && ./gradlew testReleaseUnitTest assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/air-weather/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Open it once from its tile (or `am start -n tv.home.airweather/.WeatherActivity`) so the first refresh runs. The
launcher asks once whether to show its module card.

## Tested on

Sony BRAVIA 4K VH2 (Asia-Pacific firmware), Google TV 12 / Android 12, Bangkok: automatic place, place search with
the TV keyboard, Air4Thai station readings, the hourly job across a reboot, every preview phase and weather, and the
two switches. Launcher frame time with the sky and haze on: about 9 ms in light weather, 18 ms in a storm with heavy
haze (`dumpsys gfxinfo`).

Data: [Open-Meteo](https://open-meteo.com) (CC BY 4.0), [Air4Thai](http://air4thai.pcd.go.th) / Pollution Control
Department, CAMS (Copernicus Atmosphere Monitoring Service).
