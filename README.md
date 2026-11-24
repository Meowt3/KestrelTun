# KestrelTun — Fast Native Android VPN Client (mihomo / Clash.Meta + Full TUN)

[![Build Release APK](https://github.com/Meowt3/KestrelTun/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Meowt3/KestrelTun/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/Meowt3/KestrelTun?label=Download%20APK)](https://github.com/Meowt3/KestrelTun/releases/latest)
[![Platform](https://img.shields.io/badge/Android-10%2B%20(arm64--v8a)-3DDC84?logo=android&logoColor=white)](https://github.com/Meowt3/KestrelTun/releases)
[![Core](https://img.shields.io/badge/Core-mihomo%20v1.19.34-blue)](https://github.com/MetaCubeX/mihomo)

**KestrelTun** is a lightweight, battery-efficient, open-source **native Android VPN & proxy client** built with **Kotlin + Jetpack Compose (Material You 120Hz)**, powered by the native **mihomo (Clash.Meta)** Go core and **hev-socks5-tunnel** C Full TUN bridge.

> **KestrelTun** — быстрый нативный Android VPN-клиент с открытым исходным кодом на ядре **mihomo (Clash.Meta)** и **hev-socks5-tunnel (Full TUN)**. Поддержка **VLESS Reality, Hysteria2, TUIC, WireGuard, Trojan, VMess, Shadowsocks**, встроенный **AdBlock**, обход РФ-сайтов и приложений, автогруппы с быстрым пингом и минимальный расход батареи.

---

## Download / Скачать APK

Download the latest R8-optimized signed Release APK (`arm64-v8a`) from [**GitHub Releases**](https://github.com/Meowt3/KestrelTun/releases/latest).

---

## Key Features / Возможности

| Feature | Details |
| :--- | :--- |
| **Native Full TUN Engine** | Native Go **mihomo v1.19.34** + native C **hev-socks5-tunnel v2.20.0** (`mapdns`, IPv4 + IPv6 capture, MTU 1500, TCP keep-alive) |
| **All Modern Protocols** | **VLESS** (Reality, TLS, Vision, WS, gRPC, HTTPUpgrade, h2), **Hysteria2**, **Hysteria v1**, **TUIC**, **WireGuard**, **Trojan**, **VMess**, **Shadowsocks / SSR**, **SOCKS5**, **HTTP** & Clash YAML subscriptions |
| **Instant Batch Ping** | Uses mihomo's native Go `/group/{name}/delay` (`unified-delay: true`, HTTP 204) to test 500+ servers in ~2–3 seconds |
| **Manual & Auto Groups** | Built-in **All** and **Auto** groups plus custom manual/auto groups with arbitrary test interval timers (1–1440 min) and filter-based bulk server selection |
| **Smart Routing & Rules** | One-tap **AdBlock** (`category-ads-all.mrs`), **RU Bypass** (`category-ru.mrs` + `geoip/ru.mrs`), **Gemini VPN + DoH** (with UDP:443 QUIC fallback), and custom Always-VPN / Always-Direct domain lists |
| **Per-App Split Tunneling** | Exclude or include specific Android apps + one-tap **Smart RU Apps** auto-selection (banks, Gosuslugi, marketplaces, Yandex) |
| **120Hz Bento UI & Zero Idle Drain** | FlClash-inspired Material You Bento dashboard with rolling speed wave, session timer, traffic donut, subscription GB/expiry bar, AMOLED/Dark/Light themes, and **0% background UI/animation overhead** |
| **Mobile LTE Resilience** | Automatic stale-socket cleanup on Wi-Fi ↔ LTE/5G handoff, screen-off eco pause, and 1-tap diagnostic log download/share |

---

## Architecture / Архитектура

- **Data Plane (Native C + Go)**: `hev-socks5-tunnel` (C) captures Android `VpnService` TUN packets and bridges them over local SOCKS5 + `mapdns` to `mihomo` (Go) for zero-overhead routing and encryption.
- **Control & UI Layer (Native Kotlin + Compose Release/R8)**: AOT-compiled ARM64 UI with lifecycle-bound telemetry (`Lifecycle.State.STARTED`), Quick Settings Tile, and cold-start subscription auto-refresh.

---

## Build from Source / Сборка из исходников

GitHub Actions automatically builds a signed Release APK on every push to `main` (see [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)).

To build locally with JDK 17 and Android SDK 35:

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a
curl -fsSL https://github.com/MetaCubeX/mihomo/releases/download/v1.19.34/mihomo-android-arm64-v8-v1.19.34.gz | gunzip > app/src/main/jniLibs/arm64-v8a/libmihomo.so
gradle assembleRelease
```

---

## Keywords

Android VPN client, mihomo Android, Clash Meta Android, VLESS Reality Android, Hysteria2 VPN Android, TUIC, WireGuard, Trojan, VMess, Full TUN VPN, hev-socks5-tunnel, FlClash alternative, open-source VPN Android, AdBlock VPN, Split Tunneling Android, Jetpack Compose VPN, Material You VPN, ВПН для Андроид, обход блокировок, VLESS клиент Android.
