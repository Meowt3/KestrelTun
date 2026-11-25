# Privacy Policy / Политика конфиденциальности

## English

**KestrelTun** is an open-source, local-only Android networking and proxy client.

- **No Data Collection**: KestrelTun does not collect, store, sell, or transmit any personal data, usage analytics, crash telemetry, or advertising identifiers.
- **No Built-in Servers**: The application does not include or operate any VPN or proxy servers. All network configurations, subscriptions, and routing rules are supplied solely by the user and remain stored locally on the user's device.
- **Permissions**:
  - `BIND_VPN_SERVICE` / `FOREGROUND_SERVICE`: Required by Android to create a local TUN interface and route device traffic according to the user's configuration.
  - `QUERY_ALL_PACKAGES`: Used strictly locally on the device to list installed apps for user-configured per-app Split Tunneling. App lists never leave the device.
  - `POST_NOTIFICATIONS`: Used to display the active foreground service notification.
- **Contact**: Questions regarding this policy may be opened via [GitHub Issues](https://github.com/Meowt3/KestrelTun/issues).

---

## Русский

**KestrelTun** — открытый локальный сетевой и прокси-клиент для Android.

- **Без сбора данных**: KestrelTun не собирает, не хранит и не передаёт персональные данные, телеметрию, аналитику или рекламные идентификаторы.
- **Без встроенных серверов**: приложение не предоставляет собственные VPN- или прокси-серверы. Все конфигурации, подписки и правила маршрутизации добавляются пользователем самостоятельно и хранятся исключительно локально на устройстве.
- **Разрешения**:
  - `BIND_VPN_SERVICE` / `FOREGROUND_SERVICE`: требуются Android для создания локального TUN-интерфейса и маршрутизации трафика по пользовательской конфигурации.
  - `QUERY_ALL_PACKAGES`: используется строго локально на устройстве для отображения списка установленных приложений в настройке разделения трафика (Split Tunneling). Список приложений никуда не отправляется.
  - `POST_NOTIFICATIONS`: используется для отображения системного уведомления активного сетевого сервиса.
- **Обратная связь**: вопросы можно задать через [GitHub Issues](https://github.com/Meowt3/KestrelTun/issues).
