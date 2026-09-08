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

package com.svenruppert.flow.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.AppLoginView;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.session.JCustosVersionEnforcer;
import eu.jsentinel.jcustos.session.JCustosVersionStore;
import eu.jsentinel.jcustos.session.vaadin.JCustosVersionEnforcerListener;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.UIInitListener;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.Registration;

import java.util.Optional;

/**
 * Registers {@link JCustosVersionEnforcerListener} on every UI so
 * drifted sessions reroute to {@link AppLoginView}.
 *
 * <p>The listener only fires when the {@code AppLoginView} captured a
 * {@link eu.jsentinel.jcustos.session.JCustosVersion} snapshot
 * at login time — which itself requires both an SPI-registered
 * {@link JCustosVersionStore} and a
 * {@link eu.jsentinel.jcustos.authorization.api.SubjectIdResolver}.
 *
 * <p>Without an SPI-registered store this initialiser logs a warning
 * and skips registration — the app still runs, but with drift
 * detection silently disabled.
 *
 * <p>Registered via
 * {@code META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener}.
 */
public class JCustosVersionInitListener
    implements VaadinServiceInitListener, HasLogger {

  @Override
  public void serviceInit(ServiceInitEvent event) {
    Optional<JCustosVersionStore> storeOpt =
        JCustosServiceResolver.findJCustosVersionStore();
    if (storeOpt.isEmpty()) {
      logger().warn("JCustosVersionStore SPI not registered — "
          + "Phase 4c drift detection disabled");
      return;
    }
    JCustosVersionEnforcer enforcer = new JCustosVersionEnforcer(
        storeOpt.get(), JCustosServiceResolver.securityAuditService());
    event.getSource().addUIInitListener((UIInitListener) uiInitEvent -> {
      JCustosVersionEnforcerListener listener =
          new JCustosVersionEnforcerListener(enforcer, AppLoginView.class);
      Registration reg = uiInitEvent.getUI().addBeforeEnterListener(listener);
      uiInitEvent.getUI().addDetachListener(detach -> reg.remove());
    });
  }
}
