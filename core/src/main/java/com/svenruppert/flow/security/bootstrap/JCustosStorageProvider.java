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

import com.svenruppert.flow.security.storage.AppStoragePaths;
import eu.jsentinel.jcustos.persistence.eclipsestore.EclipseStoreJCustosStorage;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStorageFactory;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStoragePair;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Lazy singleton holder for the single {@link JCustosStoragePair}.
 *
 * <p>The first reference opens (or creates) <strong>one</strong>
 * Eclipse-Store storage pair under {@link AppStoragePaths#baseDir()}
 * via {@link JCustosStorageFactory#openAt(java.nio.file.Path)}:
 *
 * <ul>
 *   <li>{@link #framework()} — the jCustos framework store
 *       ({@code <base>/jcustos-store}): audit log, session store,
 *       drift-detection version store, …</li>
 *   <li>{@link #app()} — the application-owned Eclipse-Store manager
 *       ({@code <base>/app-store}): domain data such as the user
 *       directory. The app owns this manager's root.</li>
 * </ul>
 *
 * <p>A <strong>single</strong> JVM shutdown hook closes the pair.
 * {@link JCustosStoragePair#close()} is two-phase (app first, then
 * framework — the framework store is always closed even if the app
 * shutdown throws) and idempotent, so there is exactly one storage
 * lifecycle for the whole application. No second store, no second
 * shutdown hook, no second lock.
 *
 * <p>Tests can swap the pair via {@link #setPair(JCustosStoragePair)}
 * before any consumer initialises.
 */
public final class JCustosStorageProvider {

  /**
   * Private monitor guarding {@link #pair()} and {@link #setPair(JCustosStoragePair)}.
   * Deliberately not the class object: that monitor is reachable by anyone holding
   * the class literal, so untrusted code could hold it and stall storage start-up.
   */
  private static final Object LOCK = new Object();

  private static volatile JCustosStoragePair current;

  private JCustosStorageProvider() {
  }

  /** The single storage pair, opened lazily on first reference. */
  public static JCustosStoragePair pair() {
    JCustosStoragePair local = current;
    if (local != null) return local;
    synchronized (LOCK) {
      if (current == null) {
        current = JCustosStorageFactory.openAt(AppStoragePaths.baseDir());
        Runtime.getRuntime().addShutdownHook(
            new Thread(JCustosStorageProvider::closeCurrent, "jcustos-pair-close"));
      }
      return current;
    }
  }

  /** The jCustos framework store (audit, sessions, drift version, …). */
  public static EclipseStoreJCustosStorage framework() {
    return pair().framework();
  }

  /** The application-owned Eclipse-Store manager for domain data. */
  public static EmbeddedStorageManager app() {
    return pair().app();
  }

  /** Test seam — install a custom storage pair. */
  public static void setPair(JCustosStoragePair replacement) {
    synchronized (LOCK) {
      current = replacement;
    }
  }

  private static void closeCurrent() {
    JCustosStoragePair live = current;
    if (live != null) {
      live.close();
    }
  }
}
