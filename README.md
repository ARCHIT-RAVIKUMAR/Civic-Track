# CivicTrack - Civic Issue Reporting and Management System

Citizens report public problems, municipal authorities review and assign them, field workers update the work.
Built with **Java 21, Spring Boot 3.5, Thymeleaf, Bootstrap, Spring Security, Spring Data JPA, Flyway, PostgreSQL (Supabase)**.
College demonstration project - not a production municipal service.

> **Honesty note:** this code was written without being compiled or run in the authoring environment
> (no Maven/network was available). Expect to fix a few small errors on first build. Paste the exact error
> message and they can be fixed quickly. See "First run checklist" below.

## Features
- Register / log in (BCrypt-hashed passwords), role-based access (Citizen, Authority, Field Worker, Admin)
- Citizens: submit complaints (title, description, category, location, optional JPEG/PNG photo), track status, view history timeline, give feedback after resolution
- Authority: filter by status / category / date, pagination, start review, set priority, assign / re-assign workers, reject with reason, verify and close, send work back, CSV export
- Field workers: see assigned complaints, start work, add progress notes, mark completed
- Admin: system overview, create staff users, change roles, enable/disable users, manage categories
- Workflow enforced in code: `Submitted -> Under Review -> Assigned -> In Progress -> Resolved -> Closed` (+ `Rejected`)
- Status history table records old status, new status, user, time and notes

## Prerequisites
JDK 21, Maven 3.9+ (or IntelliJ's bundled Maven), Git, a free Supabase project.

## First run checklist
1. **Create `.env`** in the project root (copy `.env.example`) and fill in your Supabase values:
   ```
   DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<project-ref>     # or "postgres" for the direct connection
   DB_PASSWORD=<your password>
   ```
   Supabase: *Project Settings -> Database -> Connection string*. If the direct host fails on your network (IPv6 only),
   use the **Session pooler** string (port 5432). Avoid the *Transaction* pooler (port 6543): it breaks Flyway and prepared statements.
   Passwords containing `\` are awkward in `.env`; pick a password without backslashes.
2. Run: `mvn spring-boot:run` (IntelliJ: run `CivicTrackApplication`). Flyway creates all tables on first start.
3. Open http://localhost:8080.
4. Dev profile creates demo accounts (never in production):

   | Role | Email | Password |
   |------|-------|----------|
   | Admin | admin@civictrack.local | Admin@1234 |
   | Authority | authority@civictrack.local | Demo@1234 |
   | Field worker | worker@civictrack.local | Demo@1234 |
   | Citizen | citizen@civictrack.local | Demo@1234 |

5. Demo flow: citizen submits -> authority "Start review" -> "Assign" -> worker "Start work", note, "Mark as completed" -> authority "Verify and close" -> citizen leaves feedback.

## Tests
```
mvn test            # command line
```
IntelliJ: right-click `src/test/java` -> *Run 'All Tests'*. Tests use an in-memory H2 database (profile `test`), so they need no Supabase connection.

| Test class | Covers |
|------------|--------|
| `ComplaintStatusTest` | valid / invalid status transitions |
| `ComplaintTest` | entity validation and encapsulation |
| `ComplaintServiceImplTest` | assignment, role permissions, ownership rules (Mockito) |
| `UserServiceImplTest` | registration, password hashing, duplicate email |
| `ComplaintRepositoryTest` | JPA mapping and filter specifications |
| `SecurityAccessTest` | protected routes, CSRF, registration, login, disabled users |

## Project structure
```
config/      SecurityConfig, DataInitializer
controller/  Home, Auth, Citizen, Complaint (shared), Authority, Worker, Admin
dto/         form objects with validation annotations
entity/      BaseEntity, User, Category, Complaint, Assignment, StatusHistory, Feedback
enums/       Role, ComplaintStatus (with transition rules), Priority
exception/   custom exceptions + GlobalExceptionHandler
repository/  Spring Data interfaces + ComplaintSpecs (dynamic filters)
service/     interfaces (ComplaintService, NotificationService, ...)
service/impl implementations (business rules)
security/    CustomUserDetails, CustomUserDetailsService
validation/  @StrongPassword
```

## Java OOP concepts in this project
| Concept | Where | How |
|---------|-------|-----|
| Encapsulation | `Complaint`, `User` | private fields, no `setStatus()`; `changeStatus()` validates; constructors validate input |
| Abstraction | `ComplaintService`, `NotificationService`, `FileStorageService` | controllers depend on interfaces, not implementations |
| Inheritance | `BaseEntity` (`@MappedSuperclass`) | shared id + timestamps for all entities. The only inheritance used. |
| Polymorphism | `NotificationService` -> `LogNotificationService` / `EmailNotificationService` | same call, different delivery; chosen by `civictrack.notification.mode` |
| Composition | `ComplaintServiceImpl` | built from repositories + other services via constructor injection |
| Enums | `Role`, `Priority`, `ComplaintStatus` | `ComplaintStatus.allowedNext()` holds the workflow rules |
| Exceptions | `exception` package | `ResourceNotFoundException`, `ForbiddenOperationException`, `BusinessRuleException`, `InvalidStatusTransitionException`; `GlobalExceptionHandler` shows friendly pages, never stack traces |
| SOLID | layers + interfaces | single responsibility per layer; dependency inversion; new notification channel needs no change to existing code |

## Security summary
BCrypt passwords; URL rules in `SecurityConfig` **and** role/ownership checks in the services; CSRF on; session fixation protection;
HTTP-only SameSite cookies (Secure in prod); validated uploads (size + real file signature, random file names, served only through an access-checked endpoint);
no secrets in source - all from environment variables; stack traces never shown.

## Database
See `docs/ER_DIAGRAM.md` and `src/main/resources/db/migration/`. Flyway applies `V1` (schema) and `V2` (seed categories) once, in order,
and records them in `flyway_schema_history`. Hibernate runs in `validate` mode: if an entity does not match a table, startup fails with a clear message.
**Never edit an applied migration** - add `V3__...sql` instead.

## Optional features
| Feature | Status | Extra requirement |
|---------|--------|-------------------|
| Image upload | included | Free hosts (e.g. Render Free) lose files on restart. Use Supabase Storage/S3 for permanence (not implemented) |
| Pagination + filters | included | none |
| CSV export | included | none |
| Priority levels | included | none |
| Dashboard statistics | included (counts) | none |
| E-mail notifications | included, off by default | SMTP account: set `NOTIFICATION_MODE=email` and `spring.mail.*` (host, port, username, password) |
| Duplicate detection, maps/geolocation | not implemented | maps would need an API key |

## Deployment
See `docs/DEPLOYMENT.md`.
