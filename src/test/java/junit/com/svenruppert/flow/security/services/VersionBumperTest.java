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

package junit.com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.services.VersionBumper;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.session.InMemoryJCustosVersionStore;
import eu.jsentinel.jcustos.session.JCustosVersion;
import eu.jsentinel.jcustos.session.JCustosVersionKey;
import eu.jsentinel.jcustos.session.JCustosVersionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VersionBumper — JCustosVersionStore.increment via static helper")
class VersionBumperTest {

  private JCustosVersionStore previousStore;

  @BeforeEach
  void setUp() {
    previousStore = JCustosServiceResolver.findJCustosVersionStore().orElse(null);
  }

  @AfterEach
  void tearDown() {
    JCustosServiceResolver.setJCustosVersionStore(previousStore);
  }

  @Test
  @DisplayName("null user → empty (no-op)")
  void nullUserIsEmpty() {
    JCustosServiceResolver.setJCustosVersionStore(new InMemoryJCustosVersionStore());
    assertFalse(VersionBumper.bump(null).isPresent());
  }

  @Test
  @DisplayName("registered SPI → version is incremented and the new value is returned")
  void registeredStoreGetsIncremented() {
    RecordingStore store = new RecordingStore();
    JCustosServiceResolver.setJCustosVersionStore(store);

    AppUser u = new AppUser(77L, "alice", EnumSet.of(AuthorizationRole.USER));
    Optional<Long> first = VersionBumper.bump(u);

    assertTrue(first.isPresent());
    assertEquals(1L, first.get());
    assertEquals(1, store.incrementCalls.size());
    assertEquals("77", store.incrementCalls.get(0).subjectId().value());
  }

  @Test
  @DisplayName("two consecutive bumps on the same user yield monotonically increasing values")
  void consecutiveBumpsIncrement() {
    JCustosServiceResolver.setJCustosVersionStore(new RecordingStore());
    AppUser u = new AppUser(8L, "u8", EnumSet.of(AuthorizationRole.USER));

    long v1 = VersionBumper.bump(u).orElseThrow();
    long v2 = VersionBumper.bump(u).orElseThrow();

    assertEquals(1L, v1);
    assertEquals(2L, v2);
  }

  // ── Recording store — counts increment calls, returns sequential values ──

  private static final class RecordingStore implements JCustosVersionStore {
    final java.util.List<JCustosVersionKey> incrementCalls = new java.util.ArrayList<>();
    private final java.util.Map<JCustosVersionKey, Long> values = new java.util.HashMap<>();

    @Override
    public JCustosVersion current(JCustosVersionKey key) {
      return new JCustosVersion(values.getOrDefault(key, 0L));
    }

    @Override
    public JCustosVersion increment(JCustosVersionKey key) {
      incrementCalls.add(key);
      long next = values.getOrDefault(key, 0L) + 1L;
      values.put(key, next);
      return new JCustosVersion(next);
    }

    @Override
    public void reset(JCustosVersionKey key) {
      values.remove(key);
    }
  }
}
