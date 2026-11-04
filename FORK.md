# SkyGlass TV

A personal fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) for a Sony BRAVIA on Google TV in Thailand:
the same debloat tools and Apple TV style launcher, plus the weather and the air on Home, an iOS-Weather sky, PM2.5
haze and liquid glass. ทีวีหน้าตาเรียบแบบ Apple TV พร้อมสภาพอากาศ ฝน และ PM2.5 (Air4Thai) บนหน้า Home

Everything upstream still applies: [README.md](README.md), [PROMPT.md](PROMPT.md), `tools/`. This page lists only
what is different.

## What this fork adds

| Where | What |
|---|---|
| [`addons/air-weather/`](addons/air-weather/) | An add-on that fetches the weather (Open-Meteo) and the PM2.5 (Air4Thai stations, else CAMS) every hour, for the place of the TV's network or one searched by name, and hands Home one row through a content provider. Its own page: now, 12 hours, 7 days, two switches. |
| Launcher, weather on Home | `config.xml` `weather_provider`: big emoji and temperature, feels like, rain chance and when rain gets likely, PM2.5 on its band's colour from moderate up, a mask reminder, the place. Shown with the top shelf while the dock has focus. The launcher keeps **no network permission**: it only reads the provider (`WeatherRow`). |
| Launcher, sky and haze | `WeatherSceneView`: a sky for night, dawn, day, sunset and dusk on the real sunrise and sunset, pulled grey by the clouds, with drifting clouds, stars, rain and lightning. `HazeView`: dusty fog and specks over Home, from nothing at 12 µg/m³ to thick at 120. One Choreographer beat at 25 fps, only while Home is resumed; the add-on's switch turns both off. |
| Launcher, liquid glass | `config.xml` `liquid_glass`: the top pills and the dock band as glass built from light (tint, sheen, lit rim, dark lip) with cached shaders and no blur, so it costs about 1 ms a frame. |
| `config.xml` | This TV's choices: dock YouTube, Netflix, Disney+, Apple TV; Live TV `com.sony.dtv.tvx`; Sony's utility tiles hidden; no greeting. |

Measured on a Sony BRAVIA 4K VH2 (Android 12): about 9 ms a frame and 10 % CPU for Home in light weather, 18 ms
and 30 % in a storm with heavy haze, nothing at all with the sky and haze switched off.

## Install

As upstream: debloat with `tools/disable.sh` and the TV's list in `examples/`, then

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
# the add-on first, so Home has a provider to read (addons/air-weather/README.md)
cd addons/air-weather && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/air-weather/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
# then the launcher
TV_HOST=<tv ip> launcher/build-and-install.sh
```

Without the add-on, or with `weather_provider` empty, the launcher behaves exactly as upstream's.

## Keeping up with upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # once
git fetch upstream && git merge upstream/main
```

The changes sit in a few places (`MainActivity` weather section, `activity_main.xml`, `config.xml`, `DockDecoration`),
so merges usually conflict there if at all.

## Licence

MIT, as upstream ([LICENSE](LICENSE)); the original copyright notice stays. Weather data from
[Open-Meteo](https://open-meteo.com) (CC BY 4.0), PM2.5 from [Air4Thai](http://air4thai.pcd.go.th) (Pollution Control
Department, Thailand) and CAMS.
