# ✅ Dawam Tasks Backend
A Spring Boot REST API for a daily task/habit tracker — sections, one-time tasks, recurring tasks with per-day completion tracking, a calendar endpoint, and cross-section task moving. Secured by Supabase Auth JWTs. Powers the [Dawam-Tasks-Web](https://github.com/xAdhm/Dawam-Tasks-Web) frontend.

---

## 🛠️ Tech Stack
- Java 17 + Spring Boot 4.1.0 (`spring-boot-starter-parent`)
- Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Spring Validation
- Spring Security + OAuth2 Resource Server — validates Supabase Auth JWTs (ES256) against a JWKS endpoint
- PostgreSQL (hosted on Supabase) via Hibernate/JPA, `ddl-auto=update` (schema auto-sync, no migration tool)
- Lombok
- Maven (Maven Wrapper included) + Dockerfile for containerized builds

---

## ✅ Prerequisites
- Java 17 (JDK)
- Maven (or use the included `./mvnw` / `mvnw.cmd` wrapper)
- A PostgreSQL database — this project is wired to a specific Supabase Postgres instance and Supabase Auth project (see below); point it at your own Supabase project or adapt `application.properties` for a different Postgres + JWT issuer
- A Supabase Auth (or other ES256-JWT-issuing) identity provider to mint bearer tokens — this API has no login endpoint of its own

---

## 🚀 Setup

### 1. Clone the repo
```bash
git clone https://github.com/xAdhm/Dawam-Tasks-Backend.git
cd Dawam-Tasks-Backend
```

### 2. Configure environment variables
`src/main/resources/application.properties` is checked into the repo and already points at a specific Supabase project (`aws-1-us-east-1.pooler.supabase.com`, database user `postgres.zvyppomoeyvpivalewwa`, and a matching JWKS URL). Only the password is externalized:
```bash
export DB_PASSWORD=your_supabase_postgres_password
```
`server.port` also respects a `PORT` environment variable, defaulting to `8080` if unset.

> ⚠️ The Supabase project host, DB username, and JWKS URL are hardcoded directly in `application.properties` rather than pulled from environment variables. If you fork this for your own Supabase project, you'll need to edit that file, not just set env vars — see Notes below.

### 3. Run the app
```bash
./mvnw spring-boot:run
```
The API starts on **port 8080** (or `$PORT` if set). There's no separate `npm install`-equivalent step — Maven resolves dependencies on first build.

### 4. Or run via Docker
```bash
docker build -t dawam .
docker run -p 8080:8080 -e DB_PASSWORD=your_supabase_postgres_password dawam
```

### 5. Authenticate
This API issues no tokens itself — every endpoint requires a valid Supabase Auth JWT (ES256) in the `Authorization: Bearer <token>` header, obtained from your Supabase Auth frontend flow (sign-up/sign-in happens outside this service). The API extracts the user's ID from the JWT's `sub` claim.

Other useful commands: `./mvnw test`, `./mvnw clean package`.

---

## 📡 API Endpoints
All endpoints below require a valid `Authorization: Bearer <supabase-jwt>` header — `SecurityConfig` applies `.anyRequest().authenticated()` globally, so there are no public routes, not even `/me`. CORS is restricted to `http://localhost:3000` and `https://dawam-tasks.vercel.app`.

### 👤 `GET /me`
Debug/whoami endpoint — confirms the token is valid and shows what the API sees.

**Auth required:** Bearer JWT.

**Response `200`:** plain text, not JSON:
```
Authenticated as user 3fa85f64-5717-4562-b3fc-2c963f66afa6 (jane@example.com)
```

**Errors:** `401` missing/invalid/expired JWT.

---

### 📂 `GET /sections`
Lists the authenticated user's sections, ordered by `position`.

**Auth required:** Bearer JWT.

**Response `200`:**
```json
[ { "id": "uuid", "name": "Morning Routine", "position": 0 } ]
```

**Errors:** `401` invalid/missing JWT.

---

### 📂 `POST /sections`
Creates a new section for the current user, appended to the end (`position` = current section count).

**Auth required:** Bearer JWT.

**Request body:**
```json
{ "name": "Morning Routine" }
```
`name` must not be blank (`@NotBlank`).

**Response `200`:** the created section — `{ "id": "uuid", "name": "Morning Routine", "position": 0 }`

**Errors:** `400` blank/missing `name` · `401` invalid/missing JWT.

---

### 📂 `PUT /sections/{id}`
Renames a section. Only works if the section belongs to the requesting user.

**Auth required:** Bearer JWT.

**Request body:** `{ "name": "New Name" }`

**Response `200`:** the updated section.

**Errors:** `400` blank name · `401` invalid/missing JWT · `404` section doesn't exist **or** belongs to another user (both cases return 404, not 403 — no ownership is ever revealed to a non-owner).

---

### 📂 `DELETE /sections/{id}`
Deletes a section (and, via JPA cascade through its tasks' relations, anything referencing it — see Notes on cascading below).

**Auth required:** Bearer JWT.

**Response `204`:** No Content.

**Errors:** `401` invalid/missing JWT · `404` not found / not owned by the user.

---

### 📂 `PUT /sections/reorder`
Bulk-reorders the user's sections.

**Auth required:** Bearer JWT.

**Request body:** a JSON array of section UUIDs in the desired order:
```json
["uuid-1", "uuid-2", "uuid-3"]
```
IDs not belonging to the user (or not found) are silently skipped rather than erroring.

**Response `200`:** the full, freshly-reordered list of the user's sections (same shape as `GET /sections`).

**Errors:** `401` invalid/missing JWT.

---

### 📝 `GET /sections/{sectionId}/tasks`
Lists tasks within a section, ordered by `position`, including today's completion state.

**Auth required:** Bearer JWT; the section must belong to the caller.

**Response `200`:**
```json
[
  {
    "id": "uuid", "sectionId": "uuid", "title": "Drink water",
    "type": "RECURRING", "dueDate": null, "completed": false,
    "frequency": "DAILY", "daysOfWeek": null,
    "doneToday": false, "dueTodayFlag": true, "position": 0
  }
]
```

**Errors:** `401` invalid/missing JWT · `404` section not found / not owned by the user.

---

### 📝 `POST /sections/{sectionId}/tasks`
Creates a task in a section. Behavior depends on `type`.

**Auth required:** Bearer JWT; section must belong to the caller.

**Request body (one-time task with datetime deadline):**
```json
{ "title": "File taxes", "type": "ONE_TIME", "dueDate": "2026-07-15T14:30:00" }
```

**Request body (recurring task):**
```json
{ "title": "Drink water", "type": "RECURRING", "frequency": "DAILY" }
```
or for specific weekdays:
```json
{ "title": "Gym", "type": "RECURRING", "frequency": "SPECIFIC_DAYS", "daysOfWeek": ["MON", "WED", "FRI"] }
```
`title` and `type` are required (`@NotBlank`/`@NotNull`). `type` is `ONE_TIME` or `RECURRING`; `frequency` is `DAILY` or `SPECIFIC_DAYS`; `daysOfWeek` entries are `MON`–`SUN`. `dueDate` is a full ISO datetime string (`LocalDateTime`) — not date-only.

**Response `200`:** the created task (`TaskResponse` shape, as above).

**Errors:** `400` missing `title`/`type` · `401` invalid/missing JWT · `404` section not found / not owned by the user.

---

### 📝 `PUT /sections/{sectionId}/tasks/{taskId}`
Updates a task's title, type, and type-specific fields (due date/time or recurrence rule). Switching `type` replaces the relevant fields — e.g. switching to `RECURRING` clears `dueDate` and creates/updates the linked `RecurrenceRule`; switching to `ONE_TIME` deletes the recurrence rule (`orphanRemoval`).

**Auth required:** Bearer JWT; the task's section must belong to the caller.

**Request body:** same shape as create.

**Response `200`:** the updated task.

**Errors:** `400` missing required fields · `401` invalid/missing JWT · `404` task not found, task doesn't belong to `sectionId`, or section not owned by the user.

---

### 📝 `DELETE /sections/{sectionId}/tasks/{taskId}`
Deletes a task (and its `RecurrenceRule`, via cascade).

**Auth required:** Bearer JWT; ownership enforced as above.

**Response `204`:** No Content.

**Errors:** `401` invalid/missing JWT · `404` not found / mismatched section / not owned by the user.

---

### 📝 `PUT /sections/{sectionId}/tasks/{taskId}/toggle`
Toggles completion. For a `ONE_TIME` task this flips the `completed` boolean directly. For a `RECURRING` task it instead adds or removes a `TaskCompletion` row for **today's date** — recurring tasks have no single "completed" state, only a completion history.

**Auth required:** Bearer JWT; ownership enforced as above.

**Response `200`:** the updated task, including `doneToday` reflecting the new state.

**Errors:** `401` invalid/missing JWT · `404` not found / mismatched section / not owned by the user.

---

### 📝 `PUT /sections/{sectionId}/tasks/reorder`
Bulk-reorders tasks within one section. Same semantics as section reorder — unknown/foreign IDs are skipped silently.

**Auth required:** Bearer JWT; section must belong to the caller.

**Request body:** `["taskUuid-1", "taskUuid-2", ...]`

**Response `200`:** the full, freshly-reordered task list for that section.

**Errors:** `401` invalid/missing JWT · `404` section not found / not owned by the user.

---

### 📝 `PUT /sections/{sectionId}/tasks/{taskId}/move`
Moves a task from one section to another. The task is appended to the end of the target section (`position` = target section's current task count). Both the source section and target section must belong to the caller.

**Auth required:** Bearer JWT; ownership enforced on both sections.

**Request body:**
```json
{ "targetSectionId": "uuid" }
```

**Response `200`:** the updated task with its new `sectionId` and `position`.

**Errors:** `401` invalid/missing JWT · `404` task not found, source section mismatch, target section not found, or either section not owned by the user.

---

### 📅 `GET /calendar`
Returns all tasks (one-time and recurring) that fall within a given date range, grouped by date. For one-time tasks, a task appears on the date matching its `dueDate`. For recurring tasks, `RecurrenceRule.isDueOn(LocalDate)` is evaluated for each day in the range — a `DAILY` task appears every day; a `SPECIFIC_DAYS` task appears only on its configured weekdays.

**Auth required:** Bearer JWT.

**Query parameters:**
- `start` — start date, inclusive, format `YYYY-MM-DD`
- `end` — end date, inclusive, format `YYYY-MM-DD`

**Response `200`:** a map of date string → task list. Every day in the range is included as a key, even days with no tasks:
```json
{
  "2026-07-01": [],
  "2026-07-02": [
    {
      "id": "uuid", "sectionId": "uuid", "title": "Morning run",
      "type": "RECURRING", "dueDate": null, "completed": false,
      "frequency": "DAILY", "daysOfWeek": null,
      "doneToday": true, "dueTodayFlag": true, "position": 0
    }
  ],
  "2026-07-03": []
}
```
`doneToday` is computed per-day for recurring tasks (checks `TaskCompletion` for that specific date), not globally.

**Errors:** `401` invalid/missing JWT.

---

## 🗃️ Data Models

### `Section`
```java
UUID id
UUID userId       // owner, matched against the JWT `sub` claim — no FK to a users table (Supabase Auth owns that)
String name        // not null
Integer position    // for manual ordering
```

---

### `Task`
```java
UUID id
Section section            // many-to-one, not null
String title               // not null
TaskType type              // ONE_TIME | RECURRING
LocalDateTime dueDate      // only set for ONE_TIME tasks; full datetime, not date-only
RecurrenceRule recurrenceRule  // one-to-one, only set for RECURRING tasks (cascade ALL, orphanRemoval)
boolean completed = false  // meaningful only for ONE_TIME tasks
Integer position = 0
```

---

### `RecurrenceRule`
```java
UUID id
Task task                    // one-to-one, not null
Frequency frequency            // DAILY | SPECIFIC_DAYS
List<DayOfWeek> daysOfWeek     // MON..SUN, only meaningful when frequency = SPECIFIC_DAYS
// isDueOn(LocalDate) — true every day if DAILY, or if the date's weekday is in daysOfWeek if SPECIFIC_DAYS
```

---

### `TaskCompletion`
```java
UUID id
Task task           // many-to-one, not null
LocalDate completedDate  // not null
// unique constraint on (task_id, completed_date) — one completion record per task per day
```
This is how recurring-task history is tracked; `ONE_TIME` tasks never produce rows here.

---

## 📝 Notes for Developers
- **Sensitive-looking config is committed, not fully externalized.** `application.properties` hardcodes the Supabase Postgres host, database username, and the Supabase project's JWKS URL directly in source control — only `DB_PASSWORD` is pulled from the environment. If you fork or redeploy this against a different Supabase project, you must edit `application.properties` (or convert these to env-var placeholders like `${SUPABASE_PROJECT_URL}`) rather than just setting new environment variables.

- **There's no signup/login endpoint in this API.** Authentication is entirely delegated to Supabase Auth — this service only *validates* JWTs (ES256, verified against Supabase's JWKS). A separate frontend (CORS allows `http://localhost:3000` and `https://dawam-tasks.vercel.app`) must handle the actual sign-up/sign-in flow and hand this API a bearer token.

- **Not-found vs. not-owned are indistinguishable by design.** Every task/section lookup filters by `userId`/section ownership before responding, and mismatches return a plain `404` rather than `403` — this prevents leaking whether a given ID exists at all to a user who doesn't own it, but means client error-handling can't tell "doesn't exist" from "not yours" apart.

- **`dueDate` is `LocalDateTime`, not `LocalDate`.** The database column was migrated from `DATE` to `TIMESTAMP` to support time-aware deadlines (`ALTER TABLE tasks ALTER COLUMN due_date TYPE TIMESTAMP USING due_date::TIMESTAMP`). The frontend sends ISO datetime strings (e.g. `"2026-07-15T14:30:00"`); existing date-only values were backfilled with midnight (`T00:00:00`).

- **`ddl-auto=update`** means Hibernate will auto-alter your schema to match the entities on every startup — convenient for a solo/early-stage project, but risky for anything with real production data. The `position` column on `tasks` was added via a manual SQL migration (`ALTER TABLE tasks ADD COLUMN IF NOT EXISTS position INTEGER DEFAULT 0`) rather than relying on `ddl-auto` due to the `NOT NULL` constraint on an existing populated table. Consider switching to Flyway/Liquibase before this matters at scale.

- **No global exception handler (`@ControllerAdvice`)** is defined, so validation and other errors fall back to Spring Boot's default error JSON shape (`timestamp`, `status`, `error`, `message`, `path`, and `errors` for binding failures — enabled via `server.error.include-message=always` / `include-binding-errors=always`). There are no custom, endpoint-specific error bodies documented beyond what Spring Boot produces by default.

- **`GET /me` returns plain text, not JSON** — inconsistent with every other endpoint in this API, which return JSON. Worth normalizing if this is meant to be a real diagnostic endpoint rather than a scratch/debug leftover.

- **Reorder endpoints silently ignore unknown or foreign IDs** in the submitted list rather than erroring — a client sending a stale or tampered ID list won't get feedback that some entries were dropped.

- **Recurring-task "done" semantics differ from one-time tasks:** `completed` on the `Task` entity is only meaningful for `ONE_TIME` tasks; for `RECURRING` tasks, always check `doneToday` in the API response instead, which is derived from `TaskCompletion` records for the current date. The `/calendar` endpoint computes `doneToday` per-day (not just "today") when building the date-keyed response map.

- **The backend is deployed on Render via Docker** (`eclipse-temurin:17-jre` runtime image) and connects to Supabase via the connection pooler (`*.pooler.supabase.com`) rather than the direct host — the direct host resolves to an IPv6 address that Render's outbound networking can't reach. `server.port=${PORT:8080}` is required for Render to detect the open port.