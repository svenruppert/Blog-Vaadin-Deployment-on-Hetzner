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

import eu.jsentinel.jcustos.session.JCustosVersion;
import eu.jsentinel.jcustos.session.JCustosVersionKey;
import eu.jsentinel.jcustos.session.JCustosVersionStore;

/**
 * ServiceLoader-friendly adapter that exposes the Eclipse-Store-backed
 * {@code JCustosVersionStore} via the
 * {@code META-INF/services/eu.jsentinel.jcustos.session.JCustosVersionStore}
 * entry. Without this adapter, the framework's drift-detection state
 * (the per-subject version counter) lives in
 * {@code InMemoryJCustosVersionStore} and gets reset on every JVM
 * restart — admins who revoked a role pre-restart see those subjects
 * drift right back into their old session afterwards.
 *
 * <p>This adapter has a public no-arg constructor (required by
 * {@link java.util.ServiceLoader}) and delegates to whatever the
 * {@link JCustosStorageProvider} currently exposes via
 * {@code securityVersionStore()}. The underlying
 * {@code EclipseStoreJCustosVersionStore} class is package-private
 * upstream — only the {@code JCustosVersionStore} interface
 * surface is consumable.
 *
 * <p>Lazy delegate: {@link JCustosStorageProvider#framework()} is
 * called on first use rather than at constructor time, so a test
 * that installs a substitute pair via
 * {@code JCustosStorageProvider.setPair(...)} before any drift
 * check still wins.
 */
public final class PersistentJCustosVersionStoreProvider
    implements JCustosVersionStore {

  private volatile JCustosVersionStore delegate;

  public PersistentJCustosVersionStoreProvider() {
  }

  private JCustosVersionStore delegate() {
    JCustosVersionStore local = delegate;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (delegate == null) {
        delegate = JCustosStorageProvider.framework().securityVersionStore();
      }
      return delegate;
    }
  }

  @Override
  public JCustosVersion current(JCustosVersionKey key) {
    return delegate().current(key);
  }

  @Override
  public JCustosVersion increment(JCustosVersionKey key) {
    return delegate().increment(key);
  }

  @Override
  public void reset(JCustosVersionKey key) {
    delegate().reset(key);
  }
}
