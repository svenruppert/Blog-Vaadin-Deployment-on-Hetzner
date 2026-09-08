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

import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.SubjectIdResolver;
import eu.jsentinel.jcustos.credential.password.bouncycastle.BouncyCastleHashingServices;
import eu.jsentinel.jcustos.dx.bootstrap.CredentialBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.SessionBootstrap;
import eu.jsentinel.jcustos.session.JCustosVersionStore;

import java.util.Optional;

/**
 * Hardening-layer extension. Contributes:
 *
 * <ul>
 *   <li>Argon2id hashing via {@link BouncyCastleHashingServices#modern()}
 *       — replaces the layer-1 PBKDF2 default. Legacy PBKDF2 / bcrypt /
 *       scrypt envelopes still verify and auto-rehash on next successful
 *       login.</li>
 *   <li>Phase-4c drift detection via {@code SessionBootstrap.securityVersion}
 *       + {@code subjectIdResolver} — when both SPIs are SPI-registered
 *       (META-INF/services entries for {@code JCustosVersionStore} and
 *       {@code SubjectIdResolver}), revoking a role mid-session forces
 *       the affected user back to login on the next navigation.</li>
 * </ul>
 *
 * <p>Registered via
 * {@code META-INF/services/} + {@link BootstrapExtension#SERVICE_NAME}.
 * {@link #order()} returns 20 so this runs after any persistence
 * extension — persistence's {@code storeBacked(...)} session-store
 * binding stays in effect while hardening adds the version-store
 * binding on top.
 */
public final class HardeningBootstrapExtension implements BootstrapExtension {

  @Override
  public void contributeCredentials(CredentialBootstrap c) {
    c.hashing(BouncyCastleHashingServices.modern());
  }

  @Override
  public void contributeSessions(SessionBootstrap s) {
    Optional<JCustosVersionStore> versionStore =
        JCustosServiceResolver.findJCustosVersionStore();
    Optional<SubjectIdResolver<Object>> resolver =
        JCustosServiceResolver.findSubjectIdResolver();
    versionStore.ifPresent(s::securityVersion);
    resolver.ifPresent(s::subjectIdResolver);
  }

  @Override
  public int order() {
    return 20;
  }
}
