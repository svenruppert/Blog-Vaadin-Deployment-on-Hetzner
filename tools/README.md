# Mutation-Coverage-Gate

CI-Gate, das Mutation-Test-Regressionen verhindert.

## Was es macht

Nach jedem PIT-Lauf (`./mvnw org.pitest:pitest-maven:mutationCoverage`)
prüft `pit-gate.sh` `target/pit-reports/index.html` gegen die in
`pit-baselines.txt` hinterlegten Floors **pro Package** plus ein
projektweites `__overall__`. Fällt eine Mutation-Coverage unter ihren
Floor, exited das Skript mit `1` — und unter dem
`_mutation-gate`-Maven-Profil bricht damit der Build.

## Ein-Schuss-Lauf

```bash
./mvnw -P_mutation-gate \
       org.pitest:pitest-maven:mutationCoverage \
       verify
```

Das Profil bindet das Gate-Skript an die `verify`-Phase. PIT läuft als
expliziter Goal-Aufruf, das Gate hängt sich an `verify`.

## Manuell — nur Gate, nach manuellem PIT-Lauf

```bash
./mvnw -o org.pitest:pitest-maven:mutationCoverage
./tools/pit-gate.sh
```

## Floors anpassen

Floors in `pit-baselines.txt` haben das Format `<package>=<min-percent>`.
Konvention:

- **Floors immer 2-3 Punkte unter dem aktuellen Messwert.** Refactors
  dürfen 1-2 Mutanten freisetzen, ohne den Build zu sprengen; eine
  echte Regression fängt der Gate trotzdem.
- **Floor anheben nur, nachdem die höhere Coverage über mehrere
  Commits stabil war.** Sonst wackelt der Build.
- **Floor senken nur mit Begründung im Commit.** Sonst verfällt die
  Coverage stillschweigend.

Der Sonder-Key `__overall__` setzt den projektweiten Floor.

## CI-Integration

In GitHub Actions oder GitLab CI als **separates Job** einrichten, nicht
in der Per-PR-Pipeline:

- PIT dauert auf diesem Repo ~8 min — zu lang für jeden Commit.
- Setup: Nightly-Schedule + manuell triggerbar (`workflow_dispatch`).
- Bei Fail: PR-Comment via `gh pr comment`, Issue eröffnen.

Beispiel-Stub (`.github/workflows/mutation-gate.yml`):

```yaml
on:
  schedule:
    - cron: '0 3 * * *'
  workflow_dispatch:
jobs:
  mutation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with: { java-version: '25', distribution: 'temurin' }
      - run: ./mvnw -P_mutation-gate org.pitest:pitest-maven:mutationCoverage verify
```

## Warum kein einfacher `mutationThreshold` in PIT?

PIT unterstützt nur einen **globalen** `mutationThreshold` — kein
per-package-Floor. Das hier ist die feinere Variante, die berücksichtigt,
dass Views (60-75 %) und Service-Layer (85-95 %) unterschiedliche
realistische Ziele haben (siehe `vaadin-mutation-browserless`-Skill §3.5).
