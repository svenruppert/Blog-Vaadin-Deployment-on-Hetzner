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

package com.svenruppert.flow.security.model;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.bootstrap.JCustosStorageProvider;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-default {@link UserDirectoryPersistence}. Holds the user
 * map as the root of the application-owned Eclipse-Store manager —
 * the {@code app()} half of the single {@link JCustosStorageProvider}
 * storage pair, <strong>not</strong> a second independent storage.
 *
 * <p>Before V00.74.20 this class spun up its own
 * {@code EmbeddedStorageManager} with a separate storage directory,
 * shutdown hook and lock, running in parallel to the framework store.
 * It now binds to the shared {@code pair.app()} manager: one storage
 * pair, one lifecycle, one shutdown hook (owned by
 * {@link JCustosStorageProvider}). This class no longer owns any
 * resource — {@link #close()} is a deliberate no-op.
 *
 * <p>Eclipse-Store uses its own type-mapping, not Java serialisation
 * — {@link AppUser} does <strong>not</strong> need
 * {@code implements Serializable}, and adding a field to the record
 * does not corrupt the on-disk format (Eclipse-Store generates a
 * legacy-type mapping automatically).
 */
public final class EclipseStoreUserDirectoryPersistence
    implements UserDirectoryPersistence, HasLogger {

  private final EmbeddedStorageManager manager;
  private volatile AppUsersRoot root;

  /** Production: persist on the shared {@code app()} store of the pair. */
  public EclipseStoreUserDirectoryPersistence() {
    this(JCustosStorageProvider.app());
  }

  /**
   * Bind to a caller-owned application-store manager — either
   * {@code JCustosStorageProvider.app()} in production or a
   * test-owned {@code EmbeddedStorage} instance. The manager's
   * lifecycle belongs to the caller; this class never shuts it down.
   */
  public EclipseStoreUserDirectoryPersistence(EmbeddedStorageManager manager) {
    this.manager = Objects.requireNonNull(manager, "manager");
  }

  @Override
  public synchronized Map<String, StoredUser> load() {
    ensureRoot();
    return new HashMap<>(root.users);
  }

  @Override
  public synchronized void save(Map<String, StoredUser> snapshot) {
    ensureRoot();
    root.users.clear();
    root.users.putAll(snapshot);
    manager.store(root.users);
    logger().debug("EclipseStoreUserDirectoryPersistence: persisted {} users to the app store",
        root.users.size());
  }

  /**
   * No-op. The shared {@code app()} manager is owned and closed by the
   * single {@link JCustosStorageProvider} storage pair, never here.
   */
  @Override
  public void close() {
    // intentionally empty — the storage pair owns the lifecycle
  }

  /**
   * Resolves (and, on a fresh store, installs) the {@link AppUsersRoot}
   * as the root of the application-owned manager. The user directory is
   * the sole owner of the app-store root.
   */
  private void ensureRoot() {
    if (root != null) return;
    Object loaded = manager.root();
    if (loaded instanceof AppUsersRoot existing) {
      root = existing;
      logger().info("EclipseStoreUserDirectoryPersistence: bound to app store ({} users present)",
          root.users.size());
    } else if (loaded == null) {
      root = new AppUsersRoot();
      manager.setRoot(root);
      manager.storeRoot();
      logger().info("EclipseStoreUserDirectoryPersistence: initialised fresh user root on the app store");
    } else {
      throw new IllegalStateException(
          "Unexpected app-store root type: " + loaded.getClass().getName()
              + " — expected " + AppUsersRoot.class.getName());
    }
  }

  /** Eclipse-Store root container. Public type so EclipseStore can map it. */
  public static final class AppUsersRoot {
    public final Map<String, StoredUser> users = new ConcurrentHashMap<>();
  }
}
