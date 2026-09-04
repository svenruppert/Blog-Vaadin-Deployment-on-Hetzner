# Design system

The template ships with a small, opinionated UI kit so a forked
project starts looking like a polished product, not a Vaadin demo.
Everything in this kit follows three principles:

1. **One place to rebrand** — colors, name, tagline and icon live in
   `TemplateBrand.java` + `styles.css`. Forking? Edit those two files.
2. **Composable, not abstract** — components are plain Vaadin Flow
   classes (`Div`, `HorizontalLayout`) wrapped in helpers. No magic,
   no DI container, no annotation processor. Use them where they
   fit, ignore where they don't.
3. **Mutation-tested core, free-form chrome** — security/services/
   model are mutation-tested at 75-90 %. UI components are free to
   move; the design system catches up via the brand constants.

## Tokens

CSS custom properties in `src/main/frontend/themes/my-theme/styles.css`.
Refork the template ⇒ change six tokens and the whole app retheme's.

| Token | Default | Meaning |
|---|---|---|
| `--app-brand-50/100/300/500/600/700` | violet scale | Primary palette. Drives Lumo's `--lumo-primary-color`. |
| `--app-shadow-sm` / `-md` / `-lg` | soft slate | Elevation scale — gentler than Lumo defaults. |
| `--app-radius-sm` / `-md` / `-lg` / `-pill` | 6/12/20/999 px | Border radii. |
| `--app-hero-gradient` | radial brand → page | Public hero + setup view backdrop. |

Lumo overrides live in the same block (`--lumo-font-family`, body /
secondary / tertiary text color, primary color). These are the only
places where the template diverges from stock Lumo — replace with
your own and the whole app follows.

## Java components

All under `com.svenruppert.flow.views.ui`.

### `TemplateBrand`

Single source of truth: `NAME`, `TAGLINE`, `LANDING_INTRO`, `ICON`,
plus the CSS-class constants the UI components apply. Never hardcode
the app name or hero copy elsewhere — read it from here.

### `BrandMark`

Icon + wordmark for the navbar.

```java
addToNavbar(new DrawerToggle(), new BrandMark());
```

### `PageHeader`

H1 + optional subtitle on the left, action slot on the right.
Used by `DashboardView`. Adopt across admin views for consistent
page tops:

```java
root.add(new PageHeader("Active sessions",
    "All sessions across every tenant.")
    .withActions(new Button("Invalidate all", e -> ...)));
```

### `MetricTile`

Dashboard tile — icon, label, big number, optional hint.

```java
new MetricTile(VaadinIcon.USERS, "Registered users", "42", "+6 this week")
```

Stack 3-4 in a `FlexLayout(.WRAP)` for the dashboard hero row.

### `FeatureCard`

Icon-on-top + heading + body. Used on the public landing page to
advertise capabilities; reusable in marketing-ish surfaces.

### `EmptyState`

Drop-in empty placeholder for grids and lists.

```java
grid.setItems(events);
if (events.isEmpty()) {
  content.add(new EmptyState(VaadinIcon.RECORDS,
      "No audit events yet",
      "Events appear here as soon as someone signs in."));
}
```

Add an optional CTA:

```java
emptyState.withAction(new Button("Refresh", e -> refresh()));
```

## When to add a new component

Add a UI helper here when:

- The pattern appears in ≥ 3 views (page header, empty state).
- The pattern carries brand visuals that should stay consistent
  (cards, metric tiles, hero surfaces).

Keep it out of `ui/` when:

- It's specific to one view (e.g., `AdminRolesView`'s role editor).
- It's a wrapper around exactly one Vaadin component with one extra
  CSS class — apply the class directly.

## Rebrand checklist

To take this template for a new product:

1. **Open `TemplateBrand.java`** — change `NAME`, `TAGLINE`,
   `LANDING_INTRO`, `ICON`. That's the wordmark, navbar, hero copy,
   document title, all in one shot.
2. **Open `styles.css`** — change the `--app-brand-*` palette. Six
   hex values. Lumo's `--lumo-primary-*` will follow because it
   maps from `--app-brand-600`.
3. **Open `MainLayout.java`** if you want a different drawer
   structure. Sections are defined in `buildDrawer()` — add a new
   `section("Reports", ...)` line and the role-gated visibility
   handles itself.
4. **Open `PublicHomeView.java`** — change the three `FeatureCard`s
   to reflect what your product ships. The hero copy already reads
   from `TemplateBrand`.

You should be able to demo the rebranded app within 30 minutes.
