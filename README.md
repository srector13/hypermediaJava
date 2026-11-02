# ☕ HypermediaJava

**A proof of concept showing that a server-rendered Java stack can feel as reactive and modern as a Vue/React SPA, without the SPA.**

Spring Boot 4 · Thymeleaf · htmx 2 · Tailwind CSS 4 · DaisyUI 5 · JDBC *and* JPA · H2

- **50,000-row data table** with server-side pagination, sorting, filtering and debounced live search, all synced to the URL (Back/Forward and bookmarks work).
- The **same UI on two data-access techniques**: plain JDBC (`JdbcClient`, hand-written SQL) and JPA (Spring Data, Specifications). A built-in **query inspector** shows the SQL each one runs.
- **Inline editing** with server-side Bean Validation (HTTP 422) and **optimistic locking** (HTTP 409 conflict notice).
- **Create modal** with as-you-type uniqueness validation, **bulk actions**, delete with fade-out, toasts.
- **Infinite scroll** with **keyset (seek) pagination**: JDBC `WHERE (last_name, id) > (?, ?)` vs Spring Data `Window<T>`.
- **Real-time, multi-tab updates** over Server-Sent Events: edit a row in one tab and it flashes in every other tab. A built-in traffic simulator lets you see this alone.
- SPA-style navigation (`hx-boost`), loading indicators, keyboard paging (← →), 8 themes, responsive layout.

The app has **no application JavaScript files, no JSON endpoints and no JS bundle**. The only client code is four `hx-on` one-liners (select-all checkbox, close modal ×2, Esc). htmx (~50 KB) and its SSE extension are served from WebJars.

---

## Quick start

Requires **Java 21+**. Node is *not* required: Maven downloads a private copy to build the stylesheet.

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. Then:

1. Open **Table · JDBC**, type in the search box, click column headers, page around, then press the browser's **Back** button.
2. Open the same page in a **second browser tab**. Edit a row in one tab (✎, change salary, <kbd>Enter</kbd>) and watch it flash in the other.
3. On the **Dashboard**, click **Start simulator**, then go to a table page: rows near you change on their own.
4. Switch between **JDBC** and **JPA** (tabs top right) and compare the SQL in the query inspector.

Other useful URLs: `/how-it-works` (feature map vs. a Vue SPA), `/h2-console` (JDBC URL `jdbc:h2:mem:hypermedia`, user `sa`, no password).

### Build a jar

```bash
./mvnw clean package
```

```bash
java -jar target/hypermedia-java-0.1.0-SNAPSHOT.jar
```

### Fast dev loop

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
npm run watch:css
```

The `dev` profile reads templates straight from `src/`, so a browser refresh shows template edits. The second command (in another terminal, with Node installed) rebuilds the CSS when you use new Tailwind/DaisyUI classes. Java changes trigger a DevTools restart.

---

## How it works

Every interaction follows one loop: **an element makes an HTTP request, and the server answers with HTML that htmx swaps into the page.**

```
 typing "ana" in search
        │  hx-trigger="input changed delay:300ms"   (debounce; hx-sync cancels stale requests)
        ▼
 GET /jdbc/employees?q=ana&sort=name&dir=asc        HX-Request: true
        │
        ▼
 EmployeeController.list()  ──►  EmployeeStore.search(query)   (JDBC or JPA, chosen by the URL)
        │
        ▼
 FragmentsRendering:  "employees/fragments :: table"           → swapped into #table-container
                    + "employees/fragments :: inspector-oob"  → out-of-band swap into #inspector
        │
        ▼
 htmx swaps both and pushes the URL into history (hx-push-url)
```

The same URL returns the **full page** for a normal request, boosted navigation or history-cache miss, and **only the fragment** for an htmx request (`Htmx.wantsFragment`). A `Vary: HX-Request` header keeps caches from mixing them up.

### Feature → mechanism

| Feature | htmx / server mechanism | What a Vue SPA typically needs |
|---|---|---|
| Live search | `hx-trigger="input changed delay:300ms, search"`, `hx-sync="…:replace"` | watcher, debounce, AbortController, loading ref |
| Sort / paginate / filter | Server renders every link with the full state (`PageView.sortUrl`, `pageUrl`) | store, query param sync, REST endpoint, DTOs |
| Back button / bookmarks | `hx-push-url="true"` | router query sync |
| SPA navigation | `hx-boost="true"` on `<body>` | vue-router |
| Inline edit | Row ⇄ editor fragments, `hx-put` + `hx-include="closest tr"` | per-row edit state, form lib |
| Validation | Bean Validation → **422** + re-rendered editor (htmx `responseHandling` swaps 422) | duplicate client-side rules |
| Concurrent edits | `@Version` (JPA) / `WHERE version = ?` (JDBC) → **409** + conflict notice | same + client error mapping |
| Toasts | Any response can carry `<template><div hx-swap-oob="beforeend:#toasts">…` | toast store / event bus |
| "Refresh yourself" | Response header `HX-Trigger: employees-changed` | cache invalidation plumbing |
| Errors | `@ControllerAdvice` → `HX-Retarget: #toasts` | global interceptor + UI |
| Real-time | SSE: `hx-trigger="sse:employee-42"`, `sse-swap="activity"` | WebSocket client, schema, store patching |
| Infinite scroll | `hx-trigger="revealed"` on a loader row (`hx-swap="outerHTML"`) | IntersectionObserver composable |
| Loading states | `hx-indicator` + `.htmx-request` CSS | `isLoading` per request |
| Keyboard paging | `hx-trigger="keyup[key=='ArrowRight'] from:body"` | key listener + router push |
| Theming | DaisyUI `theme-controller` (pure CSS) + cookie via `hx-post` | theme store + localStorage |

### Real-time updates (SSE)

Each tab opens **one** `EventSource` (`sse-connect` on `<body>`, kept open across boosted navigation). `LiveHub` holds the `SseEmitter`s and publishes two kinds of events:

- **Signals** carry no HTML, e.g. `employee-42`, `employees-changed`, `stats-changed`. Elements subscribe with `hx-trigger="sse:employee-42"` and **re-fetch themselves through their own URL**. A row on a JPA page refreshes via JPA and a row on a JDBC page via JDBC, and the server never has to know what each client is showing.
- **HTML payloads**, e.g. `activity` and `viewers`: rendered once with Thymeleaf and appended by `sse-swap="activity" hx-swap="afterbegin"`.

Every htmx request carries an `X-Client-Id` header (set once with `hx-headers` on `<body>`), so the tab that made a change gets its update in the HTTP response and is excluded from the broadcast. Table refreshes are skipped while you're editing a row (`sse:employees-changed[!document.querySelector('tr.editing')]`).

### JDBC vs JPA

Both implement `EmployeeStore`; `EngineParityTest` asserts they return **identical pages** for every sort column, filter combination and keyset scroll.

| | JDBC (`JdbcEmployeeStore`) | JPA (`JpaEmployeeStore`) |
|---|---|---|
| Filtering | Tiny dynamic `WHERE` builder, named params | `Specification` composition (`EmployeeSpecs`) |
| Sorting | Whitelisted column names (`SortColumn`) + `id` tiebreaker | `Sort.by(...)` from the same whitelist |
| Offset paging | `LIMIT/OFFSET` + **`COUNT(*) OVER()`**: page and total in **1 round-trip** | `findAll(spec, Pageable)` → `Page<T>`: select **+ count** query |
| Keyset paging | `(last_name > ? OR (last_name = ? AND id > ?))`, fetch `limit + 1` | `findBy(spec, q -> q.limit(n).scroll(ScrollPosition.keyset()))` → `Window<T>` |
| Optimistic lock | `UPDATE … SET version = version + 1 WHERE id = ? AND version = ?` | `@Version` + version check against the form |
| Bulk update | `UPDATE … WHERE id IN (:ids)` (collection param expands) | `@Modifying @Query` JPQL |
| SQL visibility | Recorded by the store | Captured by a Hibernate `StatementInspector` (`SqlCapture`) |

Both escape `%`/`_` in search input, and the sort parameter is only ever used to *look up* a whitelisted column, so `?sort=salary;drop table employee` falls back to the default sort.

---

## Project layout

```
src/main/java/dev/hypermediajava/
├── employee/
│   ├── EmployeeController.java     one controller for /jdbc/** and /jpa/** (list, row, edit, create, bulk…)
│   ├── FeedController.java         infinite scroll (keyset)
│   ├── EmployeeStore.java          the data-access contract
│   ├── jdbc/JdbcEmployeeStore.java JdbcClient + SQL
│   ├── jpa/                        entity repository, Specifications, JpaEmployeeStore
│   ├── EmployeeQuery.java          query state ⇄ URL (normalisation + link building)
│   └── PageView.java, ScrollView.java, SortColumn.java, Employee*.java
├── live/                           LiveHub (SSE), Simulator, LiveController
├── dashboard/                      stats (GROUP BY via JDBC), dashboard + how-it-works pages
├── sql/                            SqlCapture (query inspector), Timed
├── web/                            Htmx header helpers, error → toast advice
├── config/                         Vary filter, global model (theme, client id), theme cookie
└── seed/DataSeeder.java            50k deterministic employees via JDBC batch insert

src/main/resources/
├── templates/
│   ├── layout.html                 navbar, SSE connection, boost, toasts/modal containers
│   ├── employees/table.html        full page
│   ├── employees/fragments.html    table, row, edit-row, modal, inspector… (all independently renderable)
│   ├── employees/feed.html         infinite scroll page + batch fragments
│   ├── dashboard/                  dashboard (live stats + activity), how-it-works
│   └── live/fragments.html         HTML pushed over SSE
├── schema.sql                      shared schema + indexes (Hibernate doesn't own DDL)
└── application.yml / application-dev.yml

src/main/frontend/app.css           Tailwind + DaisyUI entry point + ~60 lines of transition CSS
```

**Code budget** (non-blank, non-comment lines): Java ≈ 1,800 (≈ 120 of which is the data seeder), templates ≈ 950, CSS ≈ 70, application JavaScript **0**.

## Testing

```bash
./mvnw test
```

- `EngineParityTest`: JDBC and JPA return identical pages, clamping and keyset scrolls.
- `EmployeeWebTest`: the htmx contract (full page vs fragment, 422/409, `HX-Trigger`, `HX-Retarget`, `Vary`).
- `PageViewTest`: pagination window, input normalisation, link building.

## Notes and limits (it's a POC)

- **Database**: in-memory H2, re-seeded on every start (`app.seed.count`, default 50,000). The SQL is standard (window functions, `LIMIT/OFFSET`), so pointing `spring.datasource.*` at PostgreSQL should work with no code changes. Only H2 is tested.
- **SSE and HTTP/1.1**: browsers allow about 6 connections per host over HTTP/1.1, and each tab's SSE stream uses one. That's fine for a demo; in production, serve over HTTP/2 (TLS) or behind a proxy that does.
- **Single instance**: `LiveHub` broadcasts in-memory. With multiple instances, fan out through Redis pub/sub, Postgres `LISTEN/NOTIFY` or similar.
- **No security**: there is no authentication or CSRF protection. Add Spring Security (and htmx's `hx-headers` for the CSRF token) before using this pattern in production.
- Virtual threads are enabled, so each open SSE connection parks a cheap virtual thread rather than a platform thread.
