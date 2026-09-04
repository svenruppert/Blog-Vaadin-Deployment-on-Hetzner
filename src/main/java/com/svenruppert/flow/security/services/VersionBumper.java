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

package com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.model.AppUser;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.tenant.TenantId;
import eu.jsentinel.jcustos.logout.SubjectId;
import eu.jsentinel.jcustos.session.JCustosVersionKey;
import eu.jsentinel.jcustos.session.JCustosVersionStore;

import java.util.Optional;

/**
 * Increments the per-subject {@code JCustosVersion} so any session
 * captured before the bump drifts on the next request and the
 * affected user is rerouted to {@code AppLoginView} by
 * {@code JCustosVersionEnforcerListener}.
 *
 * <p>Call sites: every role-mutating operation in {@code AdminRolesView}
 * — {@code assignRole}, {@code revokeRole}, {@code deleteUser}. The
 * bumper is a no-op when the SPI is absent (hardening skill reverted),
 * so the call sites can stay unconditional.
 */
public final class VersionBumper {

  private VersionBumper() {
  }

  /**
   * Increments the per-subject version for {@code user}. Returns the
   * post-increment value, or empty when the
   * {@link JCustosVersionStore} SPI is not registered.
   */
  public static Optional<Long> bump(AppUser user) {
    if (user == null) {
      return Optional.empty();
    }
    Optional<JCustosVersionStore> storeOpt =
        JCustosServiceResolver.findJCustosVersionStore();
    if (storeOpt.isEmpty()) {
      return Optional.empty();
    }
    JCustosVersionKey key = new JCustosVersionKey(
        TenantId.DEFAULT, SubjectId.of(user.id().toString()));
    return Optional.of(storeOpt.get().increment(key).value());
  }
}
