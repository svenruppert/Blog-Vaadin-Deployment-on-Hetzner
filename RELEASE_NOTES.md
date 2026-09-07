# Release Notes

## Unreleased

### Project identity

The repository was imported from `core-vaadin-project-template` and
carried the template's identity. It now describes the demo application
of the *Vaadin – Deployment auf Hetzner* article series.

- `pom.xml`: `artifactId` `flow-template` → **`vaadinapp`**, version
  `00.10.00` → **`00.01.00`**. Name, description, `url`, `scm` and
  `issueManagement` now point at this repository.
- Version scheme going forward: `00.0N.00` for part N of the series,
  with a matching git tag `teil-0N`.
- `TemplateBrand` → **`AppBrand`**, with brand name, tagline and intro
  copy rewritten for the series.
- Template wording removed from the UI and both translation bundles
  (`translations.properties`, `translations_de.properties`) — the
  "fork it, ship it" framing is gone; the text now describes an
  application that gets deployed.
- `README.md` rewritten: series overview, per-part tags, deployment
  target (Caddy → Jetty → `ROOT.war` under `vaadinapp.service`), and
  what each chapter of the articles points at.

The release history up to `00.10.00` belongs to
`core-vaadin-project-template` and stays in that repository.
