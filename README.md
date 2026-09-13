# Vaadin Deployment on Hetzner — Demo Application

The Vaadin Flow application that the article series **„Vaadin –
Deployment auf Hetzner"** deploys, part by part, onto a Debian server at
Hetzner. This repository is the code side of the series; the articles
themselves are published separately.

It is not a starter kit. It is one concrete application, carried through
seven parts, while the boundary between *application* and *environment*
moves outward with every part.

## Where the series stands

| Part | Topic | Boundary |
|---|---|---|
| 0 | Preparing a Hetzner Debian server | no application yet |
| 1 | From the Vaadin project to a systemd service | the application **is** the WAR |
| 2 | Publicly reachable, with Caddy and HTTPS | the environment gains a reverse proxy |
| 3 | Embedded Jetty — the server becomes part of the application | Jetty moves in |
| 4 | Fat JAR and thin distribution: building both forms | packaging changes |
| 5 | Delivering, switching, comparing | releases and rollback |
| 6 | Self-contained Vaadin with jlink | the JVM moves in |

## Checking out the code for one part

**Tags mark code states, not article numbers.** A part gets its own tag
only where its code differs from the previous one; where two parts share
a state, both articles point at the same tag. Part 0 sets up the server
and never touches this repository, so it has no tag at all.

| Tag | Parts it covers | State |
|---|---|---|
| `teil-02` | 1 and 2 | single Maven module, build produces `target/ROOT.war` |
| `teil-03` | 3 | Maven reactor, one module per delivery form |
| `teil-04` | 4 and 5 | both packaging forms, releases and rollback |
| `teil-06` | 6 | thin distribution carrying its own jlink runtime image |

```bash
git checkout teil-02      # the state parts 1 and 2 describe
git diff teil-02 teil-03  # what "the reactor splits the module" means in code
```

The project version stays at `00.01.00` — the tags carry the mapping to
the articles, so nothing depends on a version that moves per part.

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
./mvnw -Pproduction clean verify                    # all three modules, 226 tests
./mvnw -Pproduction package                         # → war-jetty/target/ROOT.war
                                                    # → embedded-jetty/target/app jar + lib/
./mvnw -P_mutation-gate \
       org.pitest:pitest-maven:mutationCoverage \
       verify                                       # PIT + enforce coverage floors
./mvnw versions:display-dependency-updates          # dependency audit
```

The production build produces **`war-jetty/target/ROOT.war`** — the artifact name
the server expects under `/opt/vaadinapp/webapps/`, so no renaming step
sits between build and deployment.

## Runtime requirements

The application needs two JVM options wherever it runs:

```
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
--enable-native-access=ALL-UNNAMED
```

EclipseStore reaches into JDK internals, and on JDK 26 that is refused without
the first flag; the second silences the restricted-method warning for the same
access.

**They are properties of the application, not of the build.** Both are listed in
`.mvn/jvm.config`, which configures the *Maven* JVM - it applies while building
and while running tests, and it does not travel with the artifact. Deploy the
WAR or the distribution without adding the flags to the service definition and
the storage layer fails on first write:

```
java.lang.Error: Could not obtain access to "jdk.internal.misc.Unsafe",
please start the VM with --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
```

That failure is unpleasant because of where it lands: the initial administrator
setup reports success, a second attempt is refused as "already initialised", and
the account cannot be signed in to. Nothing was written.

The thin distribution carries both flags in `bin/start.sh`, so a release brings
them along. Anyone building their own service definition has to add them.

Storage location is configured with `app.storage.dir` or `APP_STORAGE_DIR`;
without either the application writes to `./data` relative to the working
directory, and logs once which of the three it resolved.

## Deployment target

Part 1 puts the WAR into this shape on the server:

```
Internet → Caddy (443, TLS) → Jetty (127.0.0.1:8080) → ROOT.war
                                    ↑
                            systemd: vaadinapp.service
```

- **Jetty 12.1** (`ee11` branch — Vaadin 25 requires it; 12.0 does not
  carry those modules), installed as `/opt/jetty`, base `/opt/vaadinapp`
- **Service account** `vaadinapp`, no login shell, loopback only
- **Configuration** outside the artifact via
  `EnvironmentFile=-/etc/vaadinapp/environment`
- **Secrets** via `systemd-creds` — `LoadCredentialEncrypted=`, never as
  a process argument

## What the application contributes to the articles

Each chapter needs something concrete to point at. This is what the
application supplies:

| Chapter | Needs | Provided by |
|---|---|---|
| 4 | a real production build with frontend compilation | `-Pproduction` |
| 14 | Vaadin Push | `@Push` in `AppShell`, `views/main/PushDemoView` |
| 15 | a configuration value read from outside the WAR | `Application.resolve(...)` — system property, then environment variable, then default |
| 16 | a secret | *not yet wired* — tracked as an open item |
| 19 | visible server-side session state | login sessions, `views/SessionsView` |

## Architecture at a glance

```
core/src/main/java/com/svenruppert/flow/
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
    ├── ui/                   ← design system: AppBrand, BrandMark,
    │                            PageHeader, MetricTile, FeatureCard
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

The application's identity — name, tagline, icon, CSS classes — lives in
a single file: `views/ui/AppBrand.java`. Colors and spacing tokens live
in `core/src/main/frontend/themes/my-theme/styles.css`. Full design-system
docs: [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

## Security layering — three additive layers

The bootstrap pipeline is an SPI (`BootstrapExtension`) that picks up
every layer registered in `META-INF/services`. Each layer overrides
only the slice it cares about; the order is fixed by `order()`.

| Layer | Order | Provides |
|---|---|---|
| Default | 0 | Ring-buffer audit + logging, PBKDF2 hashing |
| Persistence | 10 | Eclipse-Store-backed audit + session store |
| Hardening | 20 | Argon2id hashing, drift-detection wiring |

Security comes from **jCustos**, not from hand-rolled auth — which is
why the deployment chapters can talk about sessions, audit and secrets
without first building an authentication system.

## Mutation-coverage gate

PIT mutation tests have per-package floors — see
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

**The gate currently does not complete.** Every PIT minion JVM pays a
full Argon2id derivation at bootstrap, and PIT forks one JVM per
mutation unit. It needs a cheap hashing profile for mutation runs before
it is usable again. `clean verify -Pproduction` is green.

## Origin

Imported from `core-vaadin-project-template` at its commit `98aca4f3`
(2026-09-04). History starts fresh here; the template keeps its own. The
two repositories share nothing but the content of that one import.

## Issue tracking

* [GitHub Issues](https://github.com/svenruppert/Blog-Vaadin-Deployment-on-Hetzner/issues)

## License

European Union Public Licence 1.2 — see `pom.xml` for the per-file
header that every source must carry.
