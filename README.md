# FinTrack

A personal finance tracker. React (Vite) frontend, Spring Boot 3.5 / Java 21 backend.

## Run it

You need Java 21+ and Node 18+. Maven is not needed; the backend includes the Maven wrapper.

**Backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

**Frontend** (http://localhost:5173, proxies `/api` to the backend):

```bash
cd frontend
npm install
npm run dev
```

On first start the backend creates the **Owner** account: username `owner`, password `ChangeMe123!`.
Set `OWNER_USERNAME`, `OWNER_EMAIL` and `OWNER_PASSWORD` before the first start to choose your own,
or change the password from the Profile page after signing in.

Data is stored in an H2 file database in `backend/data/`. Delete that folder to start over.

### Configuration

| Setting | Where | Default |
|---|---|---|
| JWT signing secret | `JWT_SECRET` env var (32+ chars) | a dev-only value: **set this outside local development** |
| Owner account | `OWNER_USERNAME`, `OWNER_EMAIL`, `OWNER_PASSWORD` | `owner` / `ChangeMe123!` |
| Bill reminder window | `app.notifications.bill-due-days` | 3 days |
| Daily reminder check | `app.notifications.cron` | 08:00 every day |
| PostgreSQL | run with `--spring.profiles.active=postgres` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | H2 file DB |

### Single deployable

`npm run build` in `frontend`, copy `frontend/dist/*` into `backend/src/main/resources/static/`,
then `./mvnw package`. The jar in `backend/target/` serves both the app and the API.

## How the requirements map to the app

| Requirement | Where |
|---|---|
| Register / sign in on the home page; everything else needs a profile | `Home.jsx`; `SecurityConfig` (only `/api/auth/**` is public) and `RequireAuth` in `App.jsx` |
| Passwords hashed | BCrypt (`SecurityConfig.passwordEncoder`) |
| Bills: label, amount due, due date, recurring | `Bill`, Bills page. Types: utilities, rent, loan, transportation payment, subscription |
| Down payment | Optional on any bill; recorded once as a *Large purchase* cost. Bills stay active until the member deletes them |
| Mark paid | Once per billing period: never more than one period ahead, and monthly/quarterly/yearly bills never twice in the same calendar month. Overdue periods can be caught up. **Undo** reverses the last payment (deleting the payment from Costs does the same). Double clicks are blocked in the browser and by an optimistic lock on the server |
| Sign-in lockout | 4 wrong passwords → 10-minute lock. The next failure after the lock ends → account locked until an administrator unlocks it (the owner only ever gets 10-minute locks, since nobody can unlock the owner). Settings: `app.security.max-login-attempts`, `app.security.lockout-minutes` |
| Support form | "Request support" on the sign-in page (public, max 3 per IP per hour). Admins see it under Administration → Support requests, with an Unlock button |
| Profile change log | Every account change (registration, profile edits, requests and their review, role changes, savings %, password changes, locks, unlocks, deletions) with who did it and before → after values. Administration → Profile change log |
| Sign-in log | Every sign-in attempt with result, username entered, IP address and browser. Attempts on an account that is already locked are refused without checking the password and are not logged, so they can't flood the log. Administration → Sign-in log (admins and owner) |
| Manual lock | Admins can lock members; the owner can lock admins and members. Nobody can lock the owner or themselves. A lock takes effect immediately, including for sessions already signed in, and is recorded in the profile change log with the optional reason |
| Unique user id | `User.id` is the primary key: assigned by the database, never null, never duplicated, never changed. `UserIdTest` checks this, including raw SQL inserts with a null or duplicate id |
| Notify when bills are due | Daily scheduled check, plus a check whenever the member opens the app. Reminders `bill-due-days` ahead, and again when overdue |
| Income: one-off or recurring, amount and date | `Income`, Income page (weekly, bi-weekly, monthly, quarterly, yearly; optional end date) |
| Spending with purchase date | `Expense`, Costs page |
| Cost categories | Bills, groceries, transportation, entertainment, large purchases. "Mark paid" on a bill adds a *Bills* cost |
| Automatic total amount of money | All income received to date minus all costs to date (dashboard) |
| Savings percentage with warning | Profile page. Spending limit = month's income − savings share; going over shows a warning and sends a notification (once a month) |
| Planning function | Planner page: one or more items, shows money before and after, the effect on this month's limit, and a 30-day projection including upcoming bills and income. Nothing is saved |
| Query cost, income or total | Search page, by date range (and category for costs) |
| Goals with a saving plan, notify when affordable | Goals page. Saved = monthly amount × months since start + top-ups; notification when it reaches the price |
| Upload a bank statement | CSV upload with a review step (categories are guessed, you confirm), then import as costs and income |
| Roles: Owner, Administrator, Member | `Role` enum |
| Members add / modify / delete / view their financial info | All finance endpoints are scoped to the signed-in user |
| Only the member can view their own financial records | No endpoint, admin ones included, returns another user's finances; looking up another user's record returns 404 |
| Members request profile modifications | Profile page → Administration page (approve / reject, with a note). The member is notified |
| Admins modify or delete members' profiles | Administration page. Admins can manage members only |
| Owner promotes Administrators and can do everything admins can | Owner can make or remove admins, and edit or delete members and admins |

## Notes and limits

- Bank statements: CSV only. PDF statements would need a parsing library and per-bank layouts.
- Notifications are in-app. Email would need SMTP settings and `spring-boot-starter-mail`.
- Money is stored as `DECIMAL(14,2)` and displayed in US dollars.
- IP addresses come from the direct connection (`::1` / `0:0:0:0:0:0:0:1` is localhost). Behind a reverse proxy, set
  `server.forward-headers-strategy=native` so the client's real address is logged.
- The schema is managed by Hibernate (`ddl-auto=update`). For production, switch to Flyway or Liquibase migrations.

## API overview

All endpoints are under `/api` and take `Authorization: Bearer <token>` except `auth/*`.

```
POST auth/register, auth/login, auth/support
GET  me              PUT me/savings, me/password
GET/POST me/change-requests
GET/POST bills       PUT/DELETE bills/{id}     POST bills/{id}/pay, bills/{id}/undo-payment
GET/POST incomes     PUT/DELETE incomes/{id}
GET/POST expenses    PUT/DELETE expenses/{id}
GET/POST goals       PUT/DELETE goals/{id}     POST goals/{id}/contribute
GET  dashboard       POST planning             GET query?type=COST|INCOME|TOTAL&from&to&category
GET  statements      POST statements/preview (multipart), statements/import
GET  notifications   POST notifications/{id}/read, notifications/read-all   DELETE notifications/{id}
Admin/Owner:  GET admin/users  PUT/DELETE admin/users/{id}  POST admin/users/{id}/unlock
              GET admin/audit-log?username=   GET admin/login-attempts?username=&outcome=
              GET admin/support-requests?status=  POST admin/support-requests/{id}/resolve
              GET admin/change-requests?status=  POST admin/change-requests/{id}/approve|reject
Owner:        PUT owner/users/{id}/role
```
