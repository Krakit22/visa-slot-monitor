# visa-slot-monitor

Spring Boot 3 / Java 21 service that polls the SuperSaaS schedule used by the **Embassy of Greece in Cyprus — VISA Department** (or any other SuperSaaS schedule, via configuration) and pushes a Telegram message the moment a new appointment slot becomes bookable.

## How the slot detection works

The schedule page (https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA) renders the calendar entirely from JavaScript — the initial HTML payload contains only an empty `<div id="viewholder">`. So scraping the rendered DOM would force us into Playwright / Selenium.

Instead, the service hits the same internal AJAX endpoint the browser uses:

```
GET /ajax/capacity/{rp_id}?token={token}&afrom=YYYY-M-D HH:MM&ato=...&efrom=...&eto=...
X-Requested-With: XMLHttpRequest
```

The response is compact JSON:

```json
{
  "app": [[from_ts, to_ts, slot_id, capacity, booked, color_busy, color_full, ...], ...],
  "exc": [[from_ts, to_ts, ...], ...]
}
```

Field semantics come straight from `capacity.js`. The JS helper `v = i[3] - i[4]` computes the spots-remaining badge and the cell flips to the "full" colour exactly when `i[3] <= i[4]`. So per entry:

| Index | Meaning |
| --- | --- |
| `i[0]`, `i[1]` | from / to (epoch seconds, UTC) |
| `i[2]` | slot ID (database PK) |
| `i[3]` | total capacity |
| `i[4]` | booked count |
| `i[5]`, `i[6]` | busy / full colour indices |

**Detection rule** (`SlotDetector`): a slot in `app[]` is "available" if and only if

1. `i[3] > 0` and `i[4] < i[3]` (capacity remaining), AND
2. its time range does not overlap any entry in `exc[]`, AND
3. it intersects the requested lookahead window.

The key insight is that the slot **must already exist in `app[]`**. A naïve "enumerate every Mon-Fri 09:00-14:00 candidate from `open_times` and subtract bookings" approach produces hundreds of false positives, because capacity-style SuperSaaS schedules only publish specific days — every other day in the open-hours grid is just not bookable on the UI even though the schedule's default open-hours window says otherwise.

`rp_id`, `token`, the weekly opening hours (`open_times`) and the default slot length (`default_length`) are embedded as inline JS on the schedule page (when fetched with `?view=week`, which the client appends automatically). The service downloads the page once on startup, regex-parses these constants, and caches them for `supersaas.metadata-ttl` (6 h by default). `open_times` / `default_length` are kept as metadata for diagnostics; they no longer drive slot enumeration.

The filtered set is fed into `InMemoryAvailabilityStore.diffAndStore(...)`, which returns only slots that are **new** since the last tick. Net effect:

* Embassy publishes a new batch of slots → first tick that observes the new `app[]` entries with `booked < capacity` ships a Telegram alert.
* Someone cancels a previously-booked slot → its `i[4]` decrements (or the entry reappears with `booked=0`); next tick alerts.
* Nothing changes → silent.
* All published slots are full → silent (this matches the current Embassy VISA state: 20 bookings, all capacity-1, all fully taken; the service should not, and does not, spam the chat).

The dedup memory is reset every `monitor.dedup-reset` (24 h) so an always-available slot is re-announced once a day in case the original message was missed.

## Project layout

```
src/main/java/com/payperless/visaslot
├── SlotCheckerApplication.java        — entry point
├── config/                            — typed @ConfigurationProperties + RestClient beans
├── model/                             — ScheduleMetadata, CapacityResponse, Appointment, AvailableSlot
├── supersaas/
│   ├── ScheduleMetadataParser.java    — regex parse of inline JS
│   ├── SuperSaasClient.java           — HTML + JSON HTTP calls (RestClient, @Retryable)
│   └── SlotDetector.java              — compute-then-diff algorithm
├── state/InMemoryAvailabilityStore.java — process-local dedup
├── monitor/SlotMonitorService.java    — orchestration of one check tick
├── scheduler/SlotCheckScheduler.java  — @Scheduled wrapper
└── telegram/
    ├── TelegramClient.java            — Bot API sendMessage (RestClient, @Retryable)
    └── TelegramNotifier.java          — message formatting + enabled flag
```

## Configuration (`application.yml` / env vars)

| Property | Env var | Default | Meaning |
| --- | --- | --- | --- |
| `monitor.check-interval` | `MONITOR_INTERVAL` | `PT5M` | How often to poll (ISO-8601 Duration). |
| `monitor.lookahead` | `MONITOR_LOOKAHEAD` | `P30D` | Forward window scanned each tick. |
| `monitor.schedule-zone` | `MONITOR_TZ` | `Asia/Nicosia` | IANA zone for slot computation. |
| `monitor.dedup-reset` | `MONITOR_DEDUP_RESET` | `PT24H` | Drop the dedup memory this often. |
| `supersaas.base-url` | `SUPERSAAS_BASE_URL` | `https://www.supersaas.com` | |
| `supersaas.schedule-path` | `SUPERSAAS_SCHEDULE_PATH` | `/schedule/EmbassyofGreeceinCyprus/VISA` | |
| `supersaas.booking-url` | `SUPERSAAS_BOOKING_URL` | (Embassy VISA URL) | Link sent in the Telegram message. |
| `supersaas.metadata-ttl` | `SUPERSAAS_METADATA_TTL` | `PT6H` | Re-bootstrap `rp_id` / `token` this often. |
| `supersaas.user-agent` | `SUPERSAAS_USER_AGENT` | recent Chrome on macOS | |
| `supersaas.connect-timeout` | `SUPERSAAS_CONNECT_TIMEOUT` | `PT5S` | |
| `supersaas.read-timeout` | `SUPERSAAS_READ_TIMEOUT` | `PT15S` | |
| `telegram.enabled` | `TELEGRAM_ENABLED` | `true` | When `false`, messages are only logged. |
| `telegram.bot-token` | `TELEGRAM_BOT_TOKEN` | (required) | From @BotFather. |
| `telegram.chat-id` | `TELEGRAM_CHAT_ID` | (required) | Numeric chat or channel ID. |
| `telegram.api-base-url` | `TELEGRAM_API_BASE_URL` | `https://api.telegram.org` | |

## Running locally

```bash
export TELEGRAM_BOT_TOKEN=123:abc
export TELEGRAM_CHAT_ID=12345678
./mvnw spring-boot:run
# or, if you don't use the wrapper:
mvn spring-boot:run
```

To smoke-test without a real Telegram bot, set `TELEGRAM_ENABLED=false` — the renderer will log the rendered message body instead of sending it.

## Running with Docker

```bash
cp .env.example .env       # then fill in TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID
docker compose up --build
```

## Tests

```bash
mvn test
```

Unit-test coverage focuses on the parts that have moving pieces and would silently rot:

* `ScheduleMetadataParserTest` — pinned against a captured copy of the real Embassy VISA page (`src/test/resources/sample-schedule-page.html`).
* `SlotDetectorTest` — Monday-only Embassy schedule, weekend exclusion, app/exc subtraction, partial-day windows.
* `InMemoryAvailabilityStoreTest` — first run, diff, disappearing slots, time-triggered and explicit resets.
* `SlotMonitorServiceTest` — orchestration: notify on new, silent on no-change, metadata-cache hit, fail-fast on bootstrap error, propagate notifier failures.
* `TelegramNotifierTest` — message contents, disabled-mode behaviour, long-list truncation.

## Reliability notes

* All outbound HTTP calls go through `@Retryable` (Spring Retry) with exponential backoff. Three attempts for the SuperSaaS calls, four for Telegram.
* The scheduler swallows `RuntimeException` from one tick so the next tick still runs — the site being briefly down or returning 5xx never kills the process.
* The HTTP client sends a realistic `User-Agent`, `Accept-Language`, `X-Requested-With: XMLHttpRequest`, and `Referer` (matching the actual browser flow), which is what SuperSaaS validates against. No headless browser needed.
* State is in-memory by design — restarting the process re-emits a single "snapshot of currently-available slots" message and then resumes diff mode. If you don't want that initial blast, set the launcher to mark the first tick as "primed" (currently the first tick reports all available slots; treat this as a feature so you confirm the service is alive).

## Limitations / future work

* The service only sees slots that the embassy has explicitly published into `app[]`. If a schedule's "this day is open" signal lives somewhere we haven't decoded (a separate `rules[]` payload, an auth-gated endpoint, etc.), we will under-report. The current model has been verified against the live Embassy VISA schedule with 20 entries, all `[capacity=1, booked=1]` — correctly reporting zero available.
* The dedup store is process-local. For multi-instance deployments switch to Redis or a small JDBC table — `AvailabilityStore` is an interface specifically for that swap.
* No metrics yet. The natural addition is a Micrometer counter on `CheckOutcome` variants + a gauge for the size of the current available set.
