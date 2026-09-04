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

package junit.com.svenruppert.flow.security.model;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.EclipseStoreUserDirectoryPersistence;
import com.svenruppert.flow.security.model.StoredUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link EclipseStoreUserDirectoryPersistence} over a real,
 * test-owned {@code EmbeddedStorageManager} — the same shape the
 * production wiring gets from {@code JCustosStorageProvider.app()},
 * but opened on a {@code @TempDir} so the test owns its lifecycle. No
 * mocks; the round-trip-survives-restart test really shuts the manager
 * down and reopens the same directory.
 */
@DisplayName("EclipseStoreUserDirectoryPersistence — app-store root round-trip")
class EclipseStoreUserDirectoryPersistenceTest {

  @Test
  @DisplayName("fresh store returns an empty map on load")
  void freshStorageIsEmpty(@TempDir Path tempDir) {
    EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir.resolve("users"));
    try {
      EclipseStoreUserDirectoryPersistence p =
          new EclipseStoreUserDirectoryPersistence(manager);
      Map<String, StoredUser> loaded = p.load();
      assertNotNull(loaded);
      assertTrue(loaded.isEmpty());
    } finally {
      manager.shutdown();
    }
  }

  @Test
  @DisplayName("save then shutdown + reopen + load yields the same entries")
  void roundTripSurvivesRestart(@TempDir Path tempDir) {
    Path dir = tempDir.resolve("users-roundtrip");

    AppUser alice = new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.ADMIN));
    AppUser bob = new AppUser(2L, "Bob", EnumSet.of(AuthorizationRole.USER));
    Map<String, StoredUser> snapshot = new HashMap<>();
    snapshot.put("alice", new StoredUser(alice, "$argon2id$fake$alice"));
    snapshot.put("bob", new StoredUser(bob, "$argon2id$fake$bob"));

    EmbeddedStorageManager m1 = EmbeddedStorage.start(dir);
    try {
      new EclipseStoreUserDirectoryPersistence(m1).save(snapshot);
    } finally {
      m1.shutdown();
    }

    // Re-open the same directory in a fresh manager — must see the data.
    EmbeddedStorageManager m2 = EmbeddedStorage.start(dir);
    try {
      Map<String, StoredUser> reloaded =
          new EclipseStoreUserDirectoryPersistence(m2).load();
      assertEquals(2, reloaded.size());
      assertEquals("$argon2id$fake$alice", reloaded.get("alice").passwordHash());
      assertEquals("$argon2id$fake$bob", reloaded.get("bob").passwordHash());
      assertEquals(alice, reloaded.get("alice").user());
      assertEquals(bob, reloaded.get("bob").user());
    } finally {
      m2.shutdown();
    }
  }

  @Test
  @DisplayName("save replaces previous content wholesale (delete by omission)")
  void saveIsReplaceNotMerge(@TempDir Path tempDir) {
    EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir.resolve("users-replace"));
    try {
      EclipseStoreUserDirectoryPersistence p =
          new EclipseStoreUserDirectoryPersistence(manager);
      Map<String, StoredUser> first = new HashMap<>();
      first.put("alice",
          new StoredUser(new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER)),
              "$alice"));
      first.put("bob",
          new StoredUser(new AppUser(2L, "Bob", EnumSet.of(AuthorizationRole.USER)),
              "$bob"));
      p.save(first);
      assertEquals(2, p.load().size());

      Map<String, StoredUser> second = new HashMap<>();
      second.put("carol",
          new StoredUser(new AppUser(3L, "Carol", EnumSet.of(AuthorizationRole.ADMIN)),
              "$carol"));
      p.save(second);

      Map<String, StoredUser> reloaded = p.load();
      assertEquals(1, reloaded.size());
      assertEquals("$carol", reloaded.get("carol").passwordHash());
    } finally {
      manager.shutdown();
    }
  }

  @Test
  @DisplayName("close() is a no-op — it must NOT shut down the shared manager")
  void closeDoesNotTouchSharedManager(@TempDir Path tempDir) {
    EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir.resolve("users-shared"));
    try {
      EclipseStoreUserDirectoryPersistence p =
          new EclipseStoreUserDirectoryPersistence(manager);
      p.save(Map.of("alice",
          new StoredUser(new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER)), "$h")));

      p.close();
      p.close();

      // The shared manager is still alive and the data still readable.
      assertTrue(manager.isRunning(), "close() must not shut down the shared manager");
      assertEquals(1, p.load().size());
    } finally {
      manager.shutdown();
    }
  }

  @Test
  @DisplayName("load returns a defensive copy — caller mutations do not bleed into storage")
  void loadReturnsCopy(@TempDir Path tempDir) {
    EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir.resolve("users-defensive"));
    try {
      EclipseStoreUserDirectoryPersistence p =
          new EclipseStoreUserDirectoryPersistence(manager);
      p.save(Map.of("alice",
          new StoredUser(new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER)), "$h")));

      Map<String, StoredUser> loaded = p.load();
      loaded.clear();

      // A re-load must still see the saved entry.
      assertEquals(1, p.load().size());
    } finally {
      manager.shutdown();
    }
  }
}
