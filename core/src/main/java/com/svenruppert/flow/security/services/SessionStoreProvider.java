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

import com.svenruppert.flow.security.bootstrap.JCustosStorageProvider;
import eu.jsentinel.jcustos.session.SessionStore;

/**
 * Delegates to the Eclipse-Store-backed
 * {@link eu.jsentinel.jcustos.persistence.eclipsestore.EclipseStoreJCustosStorage}
 * via {@link JCustosStorageProvider}.
 *
 * <p>Session records survive JVM restarts as long as the
 * {@code ./data/jcustos/} directory survives.
 */
public final class SessionStoreProvider {

  private SessionStoreProvider() {
  }

  public static SessionStore sessionStore() {
    return JCustosStorageProvider.framework().sessionStore();
  }
}
