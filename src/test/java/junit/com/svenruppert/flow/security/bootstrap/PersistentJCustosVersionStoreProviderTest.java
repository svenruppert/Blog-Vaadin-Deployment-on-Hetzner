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

package junit.com.svenruppert.flow.security.bootstrap;

import com.svenruppert.flow.security.bootstrap.PersistentJCustosVersionStoreProvider;
import eu.jsentinel.jcustos.authorization.api.tenant.TenantId;
import eu.jsentinel.jcustos.logout.SubjectId;
import eu.jsentinel.jcustos.session.JCustosVersion;
import eu.jsentinel.jcustos.session.JCustosVersionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("PersistentJCustosVersionStoreProvider — delegates to Eclipse-Store-backed store")
class PersistentJCustosVersionStoreProviderTest {

  private JCustosVersionKey freshKey(String prefix) {
    return new JCustosVersionKey(
        TenantId.DEFAULT,
        SubjectId.of(prefix + "-" + UUID.randomUUID()));
  }

  @Test
  @DisplayName("current(key) returns a non-null version from the delegate")
  void currentReturnsNonNull() {
    PersistentJCustosVersionStoreProvider provider =
        new PersistentJCustosVersionStoreProvider();
    JCustosVersion v = provider.current(freshKey("current"));
    assertNotNull(v, "delegate must produce a JCustosVersion for an unseen key");
  }

  @Test
  @DisplayName("increment(key) raises the version above its previous value")
  void incrementRaisesVersion() {
    PersistentJCustosVersionStoreProvider provider =
        new PersistentJCustosVersionStoreProvider();
    JCustosVersionKey key = freshKey("increment");
    JCustosVersion before = provider.current(key);
    JCustosVersion after = provider.increment(key);
    assertNotEquals(before.value(), after.value(),
        "increment must change the version value");
    assertEquals(before.value() + 1, after.value(),
        "increment must add exactly 1 to the previous version");
  }

  @Test
  @DisplayName("reset(key) restores the version to the initial state")
  void resetRestoresInitial() {
    PersistentJCustosVersionStoreProvider provider =
        new PersistentJCustosVersionStoreProvider();
    JCustosVersionKey key = freshKey("reset");
    JCustosVersion initial = provider.current(key);
    provider.increment(key);
    provider.increment(key);
    provider.reset(key);
    assertEquals(initial.value(), provider.current(key).value(),
        "reset must revert the version to the unseen-key value");
  }

  @Test
  @DisplayName("two providers share the same underlying delegate state")
  void providersShareDelegate() {
    JCustosVersionKey key = freshKey("shared");
    PersistentJCustosVersionStoreProvider a =
        new PersistentJCustosVersionStoreProvider();
    PersistentJCustosVersionStoreProvider b =
        new PersistentJCustosVersionStoreProvider();
    JCustosVersion afterA = a.increment(key);
    JCustosVersion seenByB = b.current(key);
    assertEquals(afterA.value(), seenByB.value(),
        "both providers must resolve to the same Eclipse-Store-backed delegate");
  }
}
