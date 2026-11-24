# KestrelTun

[![Build Release APK](https://github.com/Meowt3/KestrelTun/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Meowt3/KestrelTun/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/Meowt3/KestrelTun?label=Download%20APK)](https://github.com/Meowt3/KestrelTun/releases/latest)
[![Android](https://img.shields.io/badge/Android-10%2B%20arm64--v8a-3DDC84?logo=android&logoColor=white)](https://github.com/Meowt3/KestrelTun/releases)

Fast native Android VPN & proxy tunnel built on **Mihomo (Clash.Meta)** and **hev-socks5-tunnel** (Full TUN), with smart auto split tunneling and a clean Material You interface.

> Быстрый нативный Android VPN-клиент на ядре **Mihomo (Clash.Meta)** и **hev-socks5-tunnel (Full TUN)**. Поддержка VLESS Reality, Hysteria2, TUIC, WireGuard, Trojan, VMess и Shadowsocks, авто-туннелирование приложений, автогруппы с быстрым пингом и AdBlock.

## Download

Get the latest signed **Release APK (`arm64-v8a`)** on the [Releases page](https://github.com/Meowt3/KestrelTun/releases/latest).

## Features

- **Native Full TUN Core**: Go **mihomo v1.19.34** + C **hev-socks5-tunnel v2.20.0** (`mapdns`, IPv4 + IPv6 capture, MTU 1500, TCP keep-alive).
- **Protocols**: VLESS (Reality / TLS / Vision / WS / gRPC / HTTPUpgrade / h2), Hysteria / Hysteria2, TUIC, WireGuard, Trojan, VMess, Shadowsocks / SSR, SOCKS5, HTTP & Clash YAML subscriptions.
- **Auto Split Tunneling**: Automatic per-app tunneling & smart regional app bypass (banking, government, marketplaces) alongside manual include/exclude lists.
- **Fast Batch Ping & Auto-Groups**: Native Go `/group/{name}/delay` testing (`unified-delay: true`), built-in **All** and **Auto** groups, plus custom manual/auto groups with configurable intervals.
- **Smart Routing**: AdBlock (`category-ads-all.mrs`), RU Bypass (`category-ru.mrs` + `geoip/ru.mrs`), Gemini VPN + DoH with QUIC fallback, and custom Always-VPN / Always-Direct domain lists.
- **Battery & Performance**: R8-optimized Release build, lightweight Material You Bento UI with zero background animation overhead, screen-off eco pause, and automatic socket reset on Wi-Fi ↔ LTE handoff.

## Build from Source

Every push to `main` builds and publishes a signed Release APK via [GitHub Actions](.github/workflows/build-apk.yml).

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a
curl -fsSL https://github.com/MetaCubeX/mihomo/releases/download/v1.19.34/mihomo-android-arm64-v8-v1.19.34.gz | gunzip > app/src/main/jniLibs/arm64-v8a/libmihomo.so
gradle assembleRelease
```

## License

GPL-3.0
