# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Companion repository for the *Blog - Vaadin - Deployment on Hetzner* article series — the demo application the series deploys, not a template. `com.svenruppert:vaadinapp`, EUPL 1.2. A Vaadin Flow 25.2 web application on Java 26. Since part 3 a Maven reactor with one module per delivery form: WAR on an external Jetty, and an embedded Jetty with its own `main()`. Uses `vaadin-core` (free components only, Hilla disabled). Parent POM is `com.svenruppert:dependencies:06.02.05` which provides plugin and dependency version management. **`pom.xml` is the authoritative version reference — see "Source of truth" at the end of this file.**

## Origin

Imported from `core-vaadin-project-template` at its commit `98aca4f3` (2026-09-04). History starts fresh here: `733c90c` is that import, and the template keeps its own history. The two repositories share nothing but the content of that one import — changes do not flow between them.

Remotes: `origin` is Forgejo (`git.jsentinel.eu/sven.ruppert/Blog-Vaadin-Deployment-on-Hetzner`), `github` is the public GitHub copy. A Forgejo push mirror (`sync_on_commit`, 8 h interval) forwards to GitHub, so pushing to `origin` normally suffices — verify with `git ls-remote --heads github` if in doubt, that mirror has silently broken before.

## Identity and release tagging

`artifactId vaadinapp`, starting version `00.01.00`. The name matches the server side: the series deploys the service as `vaadinapp` (`/opt/vaadinapp`, `vaadinapp.service`, service account `vaadinapp`), and `<warName>ROOT</warName>` makes the build emit `war-jetty/target/ROOT.war` directly — the name Jetty serves from `/opt/vaadinapp/webapps/`, so no renaming step sits between build and deployment.

Version scheme: `00.0N.00` for part N of the series, plus a git tag `teil-0N`, so a reader can check out the exact state an article describes and `git diff teil-01 teil-02` shows what a part changed in code.

The application's brand identity — name, tagline, icon, CSS class names — lives in a single file: `views/ui/AppBrand.java`. The Java package names (`com.svenruppert.flow`) carry no template reference and stay as they are.

## Open items

1. **The mutation gate does not complete.** `-P_mutation-gate` has not finished since jCustos 00.83.00. Every PIT minion JVM pays a full Argon2id derivation at bootstrap (`BouncyCastleHashingServices.modern()` → `DefaultDummyVerificationService.<init>`), roughly 70 s of CPU per JVM. The application pays that once and still starts in under two seconds, but PIT forks one JVM per mutation unit. It needs a cheap hashing profile for mutation runs before the gate is usable again. Everything else is green: `clean verify -Pproduction` passes with 226 tests, Checkstyle 0 and SpotBugs 0.

## Module

Since part 3 the project is a Maven reactor with one module per delivery form.
Until then it was a single module that built both, the WAR by default and a fat
jar through a `_shadejar` profile — that double role was the root cause of
T1-A04. The state parts 1 and 2 describe is preserved as tag `teil-02`.

| Directory | artifactId | Packaging | Contains |
|---|---|---|---|
| `core/` | `vaadinapp-core` | jar | the application: views, security, persistence, i18n, all 226 tests, **and the Vaadin production bundle** |
| `war-jetty/` | `vaadinapp-war-jetty` | war | `war-jetty/target/ROOT.war` for an externally installed Jetty. No Java code — only `web.xml` |
| `embedded-jetty/` | `vaadinapp-embedded-jetty` | jar | `Application.java`, an own `main()` that assembles Jetty. Ships as `app.jar` plus `target/lib/` |

The `artifactId`s carry a `vaadinapp-` prefix on purpose: a module called `core`
would clash with `com.svenruppert:core`, which this application depends on.

**`build-frontend` runs exactly once, in `core`.** Vaadin's multi-module
guidance puts the plugin in the UI module rather than the packaging module; the
priming build generates the frontend into the module that configures the plugin.
For jar packaging the bundle lands in `target/classes/META-INF/VAADIN/webapp/`
and travels inside `core`'s jar, so both packagings resolve it from the
classpath. Do not add the plugin to a packaging module.

**Static web resources live in `core/src/main/resources/META-INF/resources/`**,
not in `src/main/webapp`. Per servlet spec a jar contributes web resources from
there, which keeps images and icons reachable under the same URLs in both
packagings from a single source.

**`java -jar` does not work for the embedded module** and the manifest
deliberately carries no `Class-Path`. With `-jar`, `java.class.path` holds a
single entry, and that property is what Jetty's `MetaInfConfiguration` reads to
decide which archives to scan. No scan means Vaadin's `RouteRegistryInitializer`
never runs and every request fails with a `NullPointerException` on
`StaticFileHandler` — while the server looks healthy and starts in 139 ms
instead of 1523 ms. Start it with an explicit classpath.

## Build & Run Commands

```bash
# Full build, all three modules
./mvnw -Pproduction clean verify

# Production build only (frontend bundle is built under -Pproduction, and only there)
./mvnw -Pproduction clean package
#   -> war-jetty/target/ROOT.war
#   -> embedded-jetty/target/vaadinapp-embedded-jetty-<version>.jar + target/lib/

# Development build: prepare-frontend runs, build-frontend does not
./mvnw clean package

# Run unit tests (they live in core)
./mvnw -pl core test
./mvnw -pl core test -Dtest=ClassName#methodName

# Dev server on the WAR
./mvnw -DskipTests -am -pl war-jetty -Pproduction package jetty:run-war

# Run the embedded form the way it is deployed
cd embedded-jetty/target && java -cp 'vaadinapp-embedded-jetty-<version>.jar:lib/*' \
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    com.svenruppert.flow.Application

# Mutation testing with PiTest + per-package coverage gate
./mvnw -pl core -P_mutation-gate \
       org.pitest:pitest-maven:mutationCoverage \
       verify

# Check for dependency updates
./mvnw versions:display-dependency-updates
```

When packaging the embedded form on macOS, suppress AppleDouble files —
otherwise `._*.jar` companions land in `lib/` and Java's `lib/*` wildcard picks
them up as invalid archives:

```bash
COPYFILE_DISABLE=1 tar czf paket.tgz app.jar lib
```

## Architecture

- **AppShell** (`com.svenruppert.flow.AppShell`) - App shell configurator. Sets viewport, PWA metadata, the `my-theme` Lumo theme, and enables `@Push` (server push via WebSocket).
- **AppServlet** (`com.svenruppert.flow.AppServlet`) - Custom `VaadinServlet`. Mapped + configured via `war-jetty/src/main/webapp/WEB-INF/web.xml` in the WAR, and by `Application.java` in the embedded module, where the critical `i18n.provider` init-param points at `com.svenruppert.flow.i18n.AppI18NProvider` (Vaadin V25 ignores `META-INF/services` for `I18NProvider`).
- **MainLayout** (`com.svenruppert.flow.views.MainLayout`) - `AppLayout` with brand mark + role-gated drawer + locale/theme switchers + auth-action button. All routed views use this as their parent layout.
- **Views** are in `com.svenruppert.flow.views` (and `.views.main`). Each view declares a `public static final String PATH` (or `NAV`) constant used for routing and nav links.
- **i18n** - Translation bundles in `core/src/main/resources/vaadin-i18n/translations*.properties`. Views implement `I18nSupport` and call `tr(K_KEY, "English fallback")`. Custom `AppI18NProvider` defeats the JVM-default-locale `ResourceBundle` fallback bug.
- **Storage paths** - Single point of truth: `com.svenruppert.flow.security.storage.AppStoragePaths`. Production defaults to `./data/`; tests fork with `-Dapp.storage.dir=target/test-data` via Surefire `<systemPropertyVariables>`.
- **Frontend** - Custom theme at `core/src/main/frontend/themes/my-theme/`, view-specific CSS in `core/src/main/frontend/styles/`. Everything the Vaadin plugin reads (`package.json`, `vite.config.ts`, `tsconfig.json`, `node_modules/`) lives in `core/` as well — the plugin's default `npmFolder` is the module basedir, so these files must stay together. The `generated/` directory is auto-generated by Vaadin and should not be edited.

## Key Configuration

- `.mvn/maven.config` forces `-U` (update snapshots), strict test failures, verbose plugin validation.
- `.mvn/jvm.config` sets heap limits and `--enable-native-access=ALL-UNNAMED`.
- Maven enforcer requires Maven >= 3.9.9.
- PiTest targets `com.svenruppert.*` prod classes and `junit.com.svenruppert.*` test classes.
- Test convention: test classes go under `junit.com.svenruppert.*` package (not the standard `com.svenruppert.*`).
- Surefire passes `-Dapp.storage.dir=${project.build.directory}/test-data` so tests never touch the repo-rooted `./data/` tree.
- PIT forks its own minion JVMs and does **not** inherit Surefire's `<systemPropertyVariables>`, so the pitest plugin repeats the same two properties via `<jvmArgs>` — pointed at `target/pit-test-data`, a separate tree from Surefire's. Without this the mutation run writes the repo-rooted `./data/`; sharing one tree instead makes a later plain `mvn test` fail to reopen the store.
- SpotBugs (`spotbugs:check`, bound by the parent POM at `verify`) reads `tools/spotbugs-exclude.xml`, which scopes out `CT_CONSTRUCTOR_THROW` for `com.svenruppert.flow.views` only.
- Surefire also passes `-Dapp.hibp.enabled=false` so `PasswordPreflight` skips the Have-I-Been-Pwned k-anonymity range call. Production runs leave this unset (default = `true`); the HIBP layer is fail-open on network errors (CWE-359), so a network outage never blocks legitimate password changes.

## Profiles

- `production` - Vaadin production frontend build with optimized bundle.
- `_java` - Additional compiler setup with newer ASM (9.8) for Java 26 support.
- `_shadejar` - **gone.** The whole construction (war execution set to phase none, an invented second primary artifact, a shade run with a ServicesResourceTransformer) existed only because a single module with war packaging had to produce a jar as well. The embedded module has jar packaging and needs none of it. Fat jar and thin distribution are the subject of part 5.
- `_mutation-gate` - Runs PIT mutation coverage + `tools/pit-gate.sh` to enforce per-package floors (build fails on regression). **Currently does not complete** — see "Open items".

## Source of truth

The reactor `pom.xml` is the authoritative version reference for: parent POM version, JDK version, Vaadin version, Jetty version, jCustos version, ASM version. Update it first, then mirror values into `CLAUDE.md` and `AGENTS.md`. Cross-check with:

```bash
./mvnw versions:display-dependency-updates
```

The current versions snapshot is:

| Property | Value |
|---|---|
| Project (`com.svenruppert:vaadinapp`) | `00.01.00` |
| Parent (`com.svenruppert:dependencies`) | `06.02.05` |
| JDK / `maven.compiler.release` | `26` |
| `vaadin.version` | `25.2.6` |
| `jetty.version` | `12.1.12` |
| `jcustos.version` | `00.83.00` |
| `asm.version` | `9.10.1` |
