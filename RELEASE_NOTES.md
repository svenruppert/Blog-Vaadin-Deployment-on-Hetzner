# Release Notes

## Unreleased

## 00.10.00 — 2026-06-14

First named cut of the template. Bundles the security stack
migration to jSentinel V00.74.00, the design-system + theming
overhaul, the i18n rewrite, the storage-paths/test-isolation pass,
the fail-fast bootstrap, the HIBP password leak check and the
persistent drift-detection store.

### Security stack

- Bumped to **jSentinel V00.74.00** (`com.svenruppert.jsentinel:*`),
  up from V00.73.00. Surfaces the new Token-Propagation API
  (`TokenCredentialStore`, `BearerToken/OidcAccessToken/RefreshToken/ApiKey`,
  `OutboundTokenStrategy`, `@PropagateToken`) — the
  `VaadinSessionTokenCredentialStore` is wired by default and the
  `SecurityFeaturesView` card panel documents the surface.
- `BootstrapExtension` SPI splits the wiring into three additive
  layers — `DefaultBootstrapExtension` (order 0),
  `PersistenceBootstrapExtension` (order 10) and
  `HardeningBootstrapExtension` (order 20). Each layer contributes
  to `.audit(...) / .sessions(...) / .credentials(...) /
  .policies(...)` independently, picked up via `ServiceLoader` at
  service init.
- Restructured the `com.svenruppert.flow.security` package into focused
  sub-packages: `model`, `roles`, `permissions`, `services`, `bootstrap`,
  `storage`.
- Typed permission catalog: `app:view`, `audit:read`,
  `admin:sessions`, `admin:roles` (`AppPermission`).
- Hybrid SPI registration: `@JSentinelAutoService` (annotation processor)
  for `AppAuthenticationService` and `AppAuthorizationService`;
  hand-written `META-INF/services` files for `AccessEvaluator`,
  `LoginListener`, `BootstrapExtension`, `JSentinelVersionStore`,
  `SubjectIdResolver`, and `VaadinServiceInitListener`.
- Eclipse-Store persistence: `EclipseStoreJSentinelStorage` opens at
  `app.storage.dir/jsentinel` (default `./data/jsentinel`). User
  directory, session store, audit ring buffer and the
  drift-detection version counter all share that backend.
  `JSentinelStorageProvider` exposes a lazy singleton + a
  `setStorage(...)` test seam; a shutdown hook closes the storage
  cleanly on JVM exit.
- First-admin bootstrap via `BootstrapWiring` + `SetupView` — a
  one-time token (`bootstrap.token` in the storage dir, validity
  `PT24H`) gates the initial admin creation. Username pattern
  `[A-Za-z0-9._-]` (1–64 chars); password policy minimum 12 chars.
- **Argon2id password hashing** (BouncyCastle modern profile)
  replaces PBKDF2. Legacy PBKDF2 hashes still verify and auto-rehash
  on next successful login via the `RehashDecisionEngine`.
- **HIBP password leak check** (`PasswordPreflight`) — k-anonymity
  range API call against `api.pwnedpasswords.com`. Only the first
  5 SHA-1 hex chars leave the JVM. Fail-open on network errors
  (CWE-359), opt-in via `app.hibp.enabled` (default `true` in
  production; Surefire sets it to `false` so tests skip the network
  call).
- **Phase-4c drift detection** — role mutations in `AdminRolesView`
  call `VersionBumper.bump(user)` to increment the per-subject
  `JSentinelVersion`; `JSentinelVersionEnforcerListener` reroutes
  the affected session to `/login` on the next request.
  `PersistentJSentinelVersionStoreProvider` adapts the
  Eclipse-Store-backed `JSentinelVersionStore` for `ServiceLoader`
  so the version counter survives JVM restarts.
- **Fail-fast bootstrap** — `JSentinelBootstrapInitListener` throws
  `IllegalStateException` with a diagnosis message when
  `AuthenticationService` or `AuthorizationService` is absent,
  instead of silently no-opping. Misconfigured wiring surfaces at
  startup, not at the first login attempt.
- **Lazy `UserDirectoryProvider`** — Initialization-on-Demand Holder
  refactor: the persistent directory opens on first access, not at
  classload time. Tests that swap the directory before any access
  still win; SpotBugs `MS_EXPOSE_REP` suppression dropped because
  the field is no longer eagerly exposed.
- `AppStoragePaths` — single source of truth for all storage
  locations (`frameworkStorageDir`, `userDirectoryDir`,
  `bootstrapTokenFile`). Driven by `-Dapp.storage.dir` so Surefire
  redirects tests to `target/test-data` and never touches the
  repo-rooted `./data/` tree.
- Brute-force protection: `AppAuthenticationService` consults
  `LoginAttemptPolicy` for throttling and records success / failure
  on every login attempt.
- Audit pipeline: `JSentinelAuditService` (Eclipse-Store-backed)
  receives `LoginSucceeded`, `LoginFailed`, `LogoutPerformed`,
  `SessionCreated`, `SessionInvalidated`, `RoleAssigned`,
  `RoleRevoked`, `UserCreated`, `UserDeleted`, `SessionStale` events.
- Logout goes through `VaadinLogoutService` — clears the
  `SubjectStore`, closes the Vaadin session, invalidates the HTTP
  session, redirects to `/login`.

### Views

- `MainLayout` — AppLayout shell carrying the brand mark, role-gated
  drawer entries and the locale + theme + auth-action switchers in
  the navbar. Drawer entries use
  `SecuredUi.link(...).requiresPermission(...).hideWhenDenied()` so
  admin entries vanish for non-privileged subjects.
- **`SecurityFeaturesView`** — public landing page that documents the
  security stack as 11 feature cards (audit, sessions, drift
  detection, Argon2id hashing, HIBP, token propagation V00.74, …)
  plus a three-row table showing the BootstrapExtension chain
  (Default / Persistence / Hardening) and a per-layer status row.
- `PushDemoView` — atmos / observer / `@Push` grid showcasing all
  three Vaadin push styles inside the new layout.
- `AppLoginView` — adds an Enter-key login shortcut via
  `Shortcuts.addShortcutListener` + `ComponentUtil.fireEvent`,
  records a `SessionRecord` on success, and forwards to `/setup`
  when the bootstrap state is `BOOTSTRAP_REQUIRED`.
- `SetupView` (`@Route("setup")`) — initial-admin form; consumes the
  bootstrap token and the chosen username/password through
  `PasswordPreflight` (local blocklist + HIBP) before handing off to
  `InitialAdminBootstrapService.createInitialAdmin(...)`.
- `AuditView` (`@RequiresPermission("audit:read")`) — renders the
  audit feed as a sortable grid.
- `SessionsView` (`@RequiresPermission("admin:sessions")`) — extends
  the framework's `SessionManagementView` over the persistent
  `SessionStore`.
- `AdminRolesView` (`@RequiresPermission("admin:roles")`) — list /
  create / delete users, assign / revoke roles. Every mutation
  publishes an audit event and calls `VersionBumper.bump(user)`.
- `AboutView`, `YoutubeView` rewired to use `MainLayout` as parent
  and the new `roles` package (`@VisibleFor(AuthorizationRole.USER)`).

### Frontend / theming

- Custom `my-theme` Lumo theme with brand tokens, plus **dark** and
  **jSentinel** variants selectable through the `ThemeSwitcher` in
  the navbar (preference persists per Vaadin session via
  `SessionPreferencesInitListener`).
- Design-system primitives under `views/ui/`: `TemplateBrand`,
  `BrandMark`, `PageHeader`, `MetricTile`, `FeatureCard`,
  `EmptyState`, `FilterBar`, `LocaleSwitcher`, `ThemeSwitcher`.
- Lumo badge readability fixes
  (`[theme~="badge"][theme~="contrast"|"success"|"error"]`) for the
  dark and jSentinel themes — the upstream Lumo light-mode colours
  were unreadable on the new backgrounds.
- jSentinel-theme text colour overrides (`--lumo-header-text-color`,
  explicit h1–h6 + form-control colours) so headings and form
  labels are not rendered black on a dark brand background.

### i18n

- Custom `AppI18NProvider` registered via the `i18n.provider`
  init-param in `WEB-INF/web.xml` — Vaadin V25 ignores
  `META-INF/services` for `I18NProvider`, so the SPI mechanism the
  predecessor used was a silent no-op.
- `ResourceBundle.Control.getNoFallbackControl(FORMAT_PROPERTIES)`
  defeats the JVM-default-locale `ResourceBundle` fallback trap: a
  missing translation for `Locale.ENGLISH` no longer cascades to
  the JVM default (German on the dev machine), it falls back to the
  base bundle.
- `I18n` static facade + `I18nSupport` mixin: every translation call
  carries an inline fallback string, so missing keys render the
  fallback rather than the raw key.
- Translation bundles under `src/main/resources/vaadin-i18n/`:
  `translations.properties` (EN ground truth) and
  `translations_de.properties` (~180 keys each).
- Runtime locale switching: `LocaleSwitcher` sets the locale via a
  `?lang=` URL parameter and `SessionPreferencesInitListener`
  applies it before any view renders — survives full reload.

### Tooling

- Maven Wrapper regenerated to **Maven 4.0.0-rc-5** (Apache wrapper
  3.3.4, `only-script` variant). The old Takari `maven-wrapper.jar`
  + `MavenWrapperDownloader.java` are gone.
- Java target **JDK 26** (`maven.compiler.release=26`); Vaadin
  25.1.1, Jetty 12.1.8.
- `_mutation-gate` Maven profile runs PIT mutation coverage and the
  `tools/pit-gate.sh` per-package floor checker.
  `tools/pit-baselines.txt` carries the calibrated floors
  (overall 35 %, security packages 68 %–100 %, view packages
  22 %–35 %); recalibrated after the V00.74 bump and the persistent
  drift store landed.
- Surefire `<systemPropertyVariables>` redirects tests away from
  the repo-rooted `./data/` tree
  (`app.storage.dir=target/test-data`) and disables HIBP network
  egress (`app.hibp.enabled=false`).
- `maven-compiler-plugin` `annotationProcessorPaths` keep
  `*-processor` **before** `*-annotations` (path order matters on
  JDK 21+ — reversed = silent no-emit, no `META-INF/services`
  entries written).
- `_shadejar` profile builds a standalone Jetty fat-jar
  (`application.jar`) via nano-vaadin-jetty 04.00.00.
- Vaadin-Maven-plugin-generated frontend tooling
  (`package.json`, `tsconfig.json`, `types.d.ts`, `vite.config.ts`)
  checked into VCS per Vaadin's own recommendation;
  `vite.generated.ts` stays untracked (auto-regenerated).
- Dropped redundant top-level `tools.jackson.core:jackson-core` /
  `jackson-databind` dependencies — both come transitively via
  `flow-server`.
- `com.fasterxml.jackson.core:jackson-annotations:2.21` retained
  **with a load-bearing comment**: Vaadin 25.1.1's transitive
  `tools.jackson.core:jackson-databind:3.1.x` loads
  `com.fasterxml.jackson.annotation.JsonSerializeAs` in its static
  initializer as a migration bridge. Removing the dep crashes
  `vaadin:prepare-frontend` with `NoClassDefFoundError`. Drop when
  a future Vaadin release ships a databind that no longer triggers
  the Jackson 2 fallback.

### Tests

- 196 unit + Browserless tests across views, listeners, providers,
  i18n, bootstrap and security services. Test classes live under
  `junit.com.svenruppert.*` (the PIT test pattern).
- New `PersistentJSentinelVersionStoreProviderTest` exercises the
  `current / increment / reset` delegation and verifies two
  providers share the same Eclipse-Store-backed state.

### Code quality

- SpotBugs clean. `AppUser` record copies the `roles` set via
  `Set.copyOf` in the compact constructor;
  `UserDirectoryProvider` no longer needs an `MS_EXPOSE_REP`
  suppression after the IODH refactor.

### Removed

- `com.svenruppert:security-for-flow:00.50.00` dependency.
- Default `InMemoryJSentinelVersionStore` SPI binding (replaced by
  `PersistentJSentinelVersionStoreProvider` so role revocations
  survive JVM restarts).
- `views/main/GreetService.java`; the old `MainView` placeholder
  replaced by `SecurityFeaturesView` + the new `MainLayout`.
- Old `META-INF/services` entries for
  `com.svenruppert.vaadin.security.authorization.*`.
- Obsolete docker scripts, `deploy.sh` and superseded views from the
  pre-jSentinel layout.
