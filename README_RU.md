<div align="center">

<img src="https://raw.githubusercontent.com/Meowt3/KestrelTun/main/snapshots/icon.png" alt="KestrelTun" width="96" />

# KestrelTun

**Открытый нативный Android-клиент для маршрутизации трафика и работы с прокси-серверами на ядре Mihomo (Clash.Meta) и Full TUN.**

[![English](https://img.shields.io/badge/Language-English-1f6feb?style=for-the-badge)](README.md) [![Русский](https://img.shields.io/badge/Язык-Русский-2ea44f?style=for-the-badge)](README_RU.md)

[![Последний релиз](https://img.shields.io/badge/%D1%80%D0%B5%D0%BB%D0%B8%D0%B7-v1.2.0-2ea44f?style=flat-square&logo=github)](https://github.com/Meowt3/KestrelTun/releases/latest)
[![Загрузки](https://img.shields.io/github/downloads/Meowt3/KestrelTun/total?style=flat-square&logo=github&label=загрузки)](https://github.com/Meowt3/KestrelTun/releases)
[![Сборка APK](https://img.shields.io/github/actions/workflow/status/Meowt3/KestrelTun/build-apk.yml?style=flat-square&label=сборка)](https://github.com/Meowt3/KestrelTun/actions/workflows/build-apk.yml)
[![Android](https://img.shields.io/badge/Android-10%2B%20%7C%20Universal-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Meowt3/KestrelTun/releases/latest)
[![Ядро](https://img.shields.io/badge/mihomo-v1.19.34-blue?style=flat-square)](https://github.com/MetaCubeX/mihomo)
[![Лицензия](https://img.shields.io/github/license/Meowt3/KestrelTun?style=flat-square&label=лицензия)](LICENSE)

[Скачать](#скачать) · [Возможности](#возможности) · [Протоколы](#поддерживаемые-протоколы) · [Архитектура](#архитектура) · [Сборка из исходников](#сборка-из-исходников) · [Отказ от ответственности](#отказ-от-ответственности-и-конфиденциальность)

</div>

---

## О проекте

**KestrelTun** — лёгкий открытый нативный Android-клиент для маршрутизации сетевого трафика без рекламы и телеметрии, написанный с нуля на **Kotlin** и **Jetpack Compose** и вдохновлённый интерфейсом и концепциями **[FlClash](https://github.com/chen08209/FlClash)** и **[FlClashX](https://github.com/pluralplay/FlClashX)**. Приложение объединяет движок маршрутизации **Mihomo (Clash.Meta)** и нативный C-мост **hev-socks5-tunnel (Full TUN)** для стабильной работы по Wi-Fi и мобильной сети.

## Скриншоты

| Панель | Сеть и подписки | Настройки и темы |
| :---: | :---: | :---: |
| ![KestrelTun — Панель](https://raw.githubusercontent.com/Meowt3/KestrelTun/main/snapshots/dashboard.png) | ![KestrelTun — Сеть и подписки](https://raw.githubusercontent.com/Meowt3/KestrelTun/main/snapshots/routing.png) | ![KestrelTun — Настройки и темы](https://raw.githubusercontent.com/Meowt3/KestrelTun/main/snapshots/settings.png) |

## Скачать

Последнюю подписанную Release-сборку APK можно скачать на странице [GitHub Releases](https://github.com/Meowt3/KestrelTun/releases/latest):

| Архитектура | Файл | Для каких устройств |
| --- | --- | --- |
| **Universal** | [`KestrelTun-v1.2.0-universal-release.apk`](https://github.com/Meowt3/KestrelTun/releases/latest/download/KestrelTun-v1.2.0-universal-release.apk) | Любой Android 10+ (если не знаете архитектуру) |
| **`arm64-v8a`** | [`KestrelTun-v1.2.0-arm64-v8a-release.apk`](https://github.com/Meowt3/KestrelTun/releases/latest/download/KestrelTun-v1.2.0-arm64-v8a-release.apk) | Современные 64-битные смартфоны (рекомендуется) |
| **`armeabi-v7a`** | [`KestrelTun-v1.2.0-armeabi-v7a-release.apk`](https://github.com/Meowt3/KestrelTun/releases/latest/download/KestrelTun-v1.2.0-armeabi-v7a-release.apk) | Старые 32-битные смартфоны и Android TV-приставки |
| **`x86_64`** | [`KestrelTun-v1.2.0-x86_64-release.apk`](https://github.com/Meowt3/KestrelTun/releases/latest/download/KestrelTun-v1.2.0-x86_64-release.apk) | Эмуляторы, Chromebook, WSA и x86_64 планшеты |

### Быстрый старт

1. Скачайте и установите актуальный APK из раздела [Releases](https://github.com/Meowt3/KestrelTun/releases/latest).
2. Перейдите в **Сеть → Подписки** и добавьте ссылку на собственную конфигурацию (Clash YAML или подписку).
3. Выберите узел или включите **Автотуннелинг** на вкладке **Прокси**, затем нажмите **Подключиться** на главной панели.

## Возможности

- **Автотуннелинг**: встроенный режим **Авто** и пользовательские группы автотуннелинга с 64-слотовой параллельной проверкой задержки, умной очередью (рабочие серверы проверяются первыми) и автоматическим выбором самого быстрого узла.
- **Полноценный Full TUN**: связка `mihomo` + `hev-socks5-tunnel` с перехватом IPv4/IPv6, `mapdns`, шифрованным DNS (DoH) и автоматическим сбросом зависших соединений при переключении Wi-Fi ↔ мобильная сеть.
- **Разделение трафика по приложениям (Split Tunneling)**: гибкие списки включений и исключений по приложениям с автовыбором локальных программ.
- **Маршрутизация по правилам и фильтрация рекламы**: встроенный фильтр рекламы (`category-ads-all.mrs`), прямой маршрут для локальных доменов (`DIRECT`), защищённый DoH-маршрут и пользовательские списки доменов.
- **Управление профилями и локальный прокси**: автообновление профилей при запуске, отображение квоты и срока действия (`Subscription-Userinfo`), раздача прокси по Wi-Fi (Allow LAN).
- **Интерфейс Material 3 Expressive**: мягкая графитовая тёмная тема, чистый **AMOLED**-режим, светлая тема, 9 пастельных гамм, плавающая нижняя панель-пилюля, пружинные анимации, просмотр соединений, предпросмотр YAML и экспорт логов ядра.
- **Тихое переключение в фоне**: поддержка системных ярлыков Android и фонового действия «Переключение» без сворачивания активного приложения.

## Поддерживаемые протоколы

| Категория | Протоколы и транспорты |
| --- | --- |
| **Современные протоколы** | `VLESS` (Reality, TLS, XTLS-Vision, WS, gRPC, HTTPUpgrade, h2), `Hysteria` / `Hysteria2`, `TUIC` |
| **Классические и туннели** | `WireGuard`, `Trojan`, `VMess`, `Shadowsocks` / `SSR`, `SOCKS5`, `HTTP` |
| **Форматы конфигураций** | Clash / Mihomo YAML, списки Base64, прямые URI-ссылки |

## Архитектура

| Компонент | Реализация |
| --- | --- |
| **Ядро прокси** | [Mihomo (Clash.Meta)](https://github.com/MetaCubeX/mihomo) `v1.19.34` (`libmihomo.so`, Go) |
| **TUN-мост** | [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) `v2.20.0` (`libhev-socks5-tunnel.so`, C) |
| **Интерфейс** | Нативный Android — Kotlin, Jetpack Compose, Material 3 |
| **Целевая платформа** | Android 10–15+ (`minSdk 29`, `targetSdk 35`, `arm64-v8a` / `armeabi-v7a` / `x86_64` / `universal`) |

## Сборка из исходников

Каждый коммит в ветку `main` автоматически запускает модульные тесты и собирает подписанные Release APK через [GitHub Actions](.github/workflows/build-apk.yml).

Для локальной сборки (требуются **JDK 17**, **Android SDK 35** и **Gradle 8.9**):

```bash
git clone https://github.com/Meowt3/KestrelTun.git
cd KestrelTun

mkdir -p app/src/main/jniLibs/arm64-v8a
curl -fsSL https://github.com/MetaCubeX/mihomo/releases/download/v1.19.34/mihomo-android-arm64-v8-v1.19.34.gz \
  | gunzip > app/src/main/jniLibs/arm64-v8a/libmihomo.so

gradle assembleRelease
```

## Отказ от ответственности и конфиденциальность

- **Только клиентское ПО**: KestrelTun — независимый сетевой клиент с открытым исходным кодом, предназначенный для работы с собственными серверами, маршрутизации и тестирования сетей. Приложение **не содержит** встроенных VPN-серверов, подписок и не оказывает услуги доступа к сети.
- **Ответственность пользователя**: пользователь самостоятельно отвечает за импортируемые конфигурации и соблюдение применимого законодательства своей страны.
- **Конфиденциальность**: приложение не содержит аналитики, телеметрии и рекламы; все данные и настройки хранятся только локально на устройстве. Подробнее — в [PRIVACY.md](PRIVACY.md).
- **Товарные знаки**: все упомянутые названия сторонних продуктов принадлежат их правообладателям. Проект не аффилирован со сторонними брендами.

## Благодарности и сторонние лицензии

- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) (`GPL-3.0`) — ядро маршрутизации прокси
- [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (`MIT`) — нативный C-туннель TUN-to-SOCKS5
- [chen08209/FlClash](https://github.com/chen08209/FlClash) и [pluralplay/FlClashX](https://github.com/pluralplay/FlClashX) (`GPL-3.0`) — вдохновение для Bento-интерфейса, фоновых быстрых действий и схемы параллельной проверки задержки

Полный список сторонних лицензий указан в файле [NOTICE](NOTICE).

## Лицензия

KestrelTun распространяется под открытой лицензией [GNU General Public License v3.0](LICENSE).
