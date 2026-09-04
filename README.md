# Core Vaadin Project Template

A polished Vaadin Flow 25 starter that ships with everything a serious
internal product needs: authentication, role-based access, persistent
storage, audit log, mutation-tested core and a design system.

Fork it, edit `TemplateBrand.java` plus six CSS hex values, and you're
shipping product on top of a hardened base instead of fighting
boilerplate.

## What's in the box

| Concern | What you get | Where it lives |
|---|---|---|
| Authentication | Username/password, role + permission catalog, drift detection | `security/{services,roles}` |
| First-admin bootstrap | One-time-token flow, persistent token file, `/setup` view | `security/bootstrap`, `views/SetupView` |
| Persistence | Eclipse-Store for users, sessions, audit events | `security/model/PersistentUserDirectory` |
| Audit log | Ring buffer + persistent sink, live `/audit` grid | `views/AuditView` |
| Session admin | Active-session inventory, revoke-on-click | `views/SessionsView` |
| Role admin | Add/remove users + roles, version-bump-on-mutation | `views/AdminRolesView` |
| Mutation tests | PIT + Browserless, per-package coverage floors enforced in CI | `tools/`, `src/test/.../*BrowserlessTest.java` |
| Design system | `TemplateBrand` + theme tokens + reusable components | `views/ui/`, `frontend/themes/my-theme/styles.css` |
| Push demo | Vaadin `@Push` example view | `views/main/PushDemoView` |

## Quick start

```bash
./mvnw                              # default goal = jetty:run on :8080
```

The first start prints a bootstrap token to stdout and writes it to
`./data/jcustos/bootstrap.token`. Open `http://localhost:8080/login`
— you'll be redirected to `/setup` until you've used the token to
create the first admin.

## Build commands

```bash
./mvnw                                              # dev server
./mvnw test                                         # unit + browserless tests
./mvnw -Pproduction package                         # production WAR
./mvnw -P_shadejar -DskipTests package              # standalone Jetty fat-jar
./mvnw -P_mutation-gate \
       org.pitest:pitest-maven:mutationCoverage \
       verify                                       # PIT + enforce coverage floors
./mvnw versions:display-dependency-updates          # dependency audit
```

## Rebranding a fork (30 minutes)

1. **`src/main/java/com/svenruppert/flow/views/ui/TemplateBrand.java`**
   — change `NAME`, `TAGLINE`, `LANDING_INTRO`, `ICON`. The wordmark,
   navbar, hero copy and document title all pull from here.
2. **`src/main/frontend/themes/my-theme/styles.css`** — change the six
   `--app-brand-*` hex values at the top. Lumo's `--lumo-primary-*` is
   mapped to it, so the entire app retheme follows.
3. **`PublicHomeView.buildFeatureGrid()`** — replace the three
   `FeatureCard`s with what your product actually ships.
4. **`MainLayout.buildDrawer()`** — add or remove drawer sections.
   Role-gated visibility is automatic.

Full design-system docs: [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

## Architecture at a glance

```
src/main/java/com/svenruppert/flow/
├── Application.java          ← standalone Jetty launcher (fat-jar mode)
├── AppShell.java             ← @Push, theme, viewport
├── AppServlet.java           ← VaadinServlet, error handling
├── security/
│   ├── bootstrap/            ← BootstrapExtension SPI, layered wiring
│   ├── model/                ← AppUser, persistent directory
│   ├── roles/                ← role enum, @VisibleFor evaluator
│   ├── permissions/          ← permission name catalog
│   └── services/             ← auth, version-bump, password preflight
└── views/
    ├── ui/                   ← design system: BrandMark, PageHeader,
    │                            MetricTile, FeatureCard, EmptyState
    ├── MainLayout.java       ← AppLayout shell, role-gated drawer
    ├── PublicHomeView.java   ← landing page (hero + features)
    ├── DashboardView.java    ← post-login metric tiles + activity
    ├── AppLoginView.java     ← jCustos-backed login form
    ├── SetupView.java        ← first-admin bootstrap form
    ├── AdminRolesView.java   ← user/role admin
    ├── AuditView.java        ← persistent audit grid
    ├── SessionsView.java     ← session inventory
    ├── AboutView.java        ← about / author profile
    └── YoutubeView.java      ← embed example
```

## Security layering — three additive layers

The bootstrap pipeline is an SPI (`BootstrapExtension`) that picks up
every layer registered in `META-INF/services`. Each layer overrides
only the slice it cares about; the order is fixed by `order()`.

| Layer | Order | Provides |
|---|---|---|
| Default | 0 | Ring-buffer audit + logging, PBKDF2 hashing |
| Persistence | 10 | Eclipse-Store-backed audit + session store |
| Hardening | 20 | Argon2id hashing, drift-detection wiring |

Adding a fourth layer (MFA, multi-tenant, …) is one new `BootstrapExtension`
implementation + one line in `META-INF/services`. Nothing else changes.

## Mutation-coverage gate

PIT mutation tests have per-package floors that CI enforces — see
[`tools/README.md`](tools/README.md). Current floors:

| Package | Floor |
|---|---|
| `security` | 90 % |
| `security.bootstrap` | 75 % |
| `security.model` | 80 % |
| `security.permissions` | 90 % |
| `security.roles` | 80 % |
| `security.services` | 80 % |
| `views` | 25 % |
| `views.main` | 20 % |
| **overall** | **42 %** |

A failing gate either means real regression (kill the surviving
mutants) or that the team decided to lower a floor — both demand a
written reason in the commit message.

## Hardening you might want to add

Already wired via skills, but not pre-configured here:

- **HIBP password leak check** — flip a flag in the
  hardening skill, `PasswordPreflight` queries `api.pwnedpasswords.com`
  with k-anonymity range.
- **Persistent JCustosVersionStore** — swap the in-memory drift
  store for the Eclipse-Store-backed one in `META-INF/services`.
- **Multi-tenant** — `TenantId` already threads through audit events
  and sessions; add per-tenant storage partitioning in
  `JCustosStorageProvider`.

## Issue tracking

* [GitHub Issues](https://github.com/svenruppert/core-vaadin-project-template/issues)
* [GitHub Projects](https://github.com/svenruppert/core-vaadin-project-template/projects)

## Vulnerability hunting

Free scanners that integrate as GitHub PR checks — useful even for
personal projects, since they often complement each other:

* [Snyk](https://snyk.io/)
* [OX Security](https://app.ox.security/)
* [FaradaySec](https://faradaysec.com/)

## License

European Union Public Licence 1.2 — see `pom.xml` for the per-file
header that every source must carry.
