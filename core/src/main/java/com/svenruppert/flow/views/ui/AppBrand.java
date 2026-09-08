/*
 * Copyright © 2013 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.svenruppert.flow.views.ui;

import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Single source of truth for the application's brand identity.
 *
 * <p>Every view, hero, navbar and metric tile pulls its strings, icon
 * and tagline from here — change the identity in <strong>this file
 * only</strong>.
 *
 * <p>For colors, typography and spacing tokens see
 * {@code src/main/frontend/themes/my-theme/styles.css}.
 */
public final class AppBrand {

  /** Display name shown in the navbar wordmark and document titles. */
  public static final String NAME = "Vaadin on Hetzner";

  /** Subtitle shown alongside the wordmark on the public landing page. */
  public static final String TAGLINE = "The demo application of the "
      + "\"Vaadin - Deployment on Hetzner\" article series.";

  /** Long-form intro for the public landing-page hero. */
  public static final String LANDING_INTRO =
      "This is the application the series deploys: from the production "
          + "build to a WAR served by Jetty behind Caddy. Authentication, "
          + "role-based access, persistent storage and an audit log keep "
          + "the deployment chapters concrete instead of hypothetical.";

  /** Icon used as the brand mark next to the wordmark. */
  public static final VaadinIcon ICON = VaadinIcon.SERVER;

  /** CSS class applied to the brand-color text (see styles.css). */
  public static final String CSS_BRAND_TEXT = "app-brand-text";

  /** CSS class for the soft-glow brand background used on heroes. */
  public static final String CSS_HERO_SURFACE = "app-hero-surface";

  /** CSS class for elevated card surfaces (dashboard tiles, feature cards). */
  public static final String CSS_CARD = "app-card";

  /** CSS class for muted helper-text labels. */
  public static final String CSS_MUTED = "app-muted";

  private AppBrand() {
  }
}
