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

import com.svenruppert.flow.security.bootstrap.JCustosStorageProvider;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStorageFactory;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStoragePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("JCustosStorageProvider — single storage pair + test seam")
class JCustosStorageProviderTest {

  private JCustosStoragePair saved;

  @BeforeEach
  void capturePrevious() {
    saved = JCustosStorageProvider.pair();
  }

  @AfterEach
  void restorePrevious() {
    JCustosStorageProvider.setPair(saved);
  }

  @Test
  @DisplayName("pair() exposes a non-null framework + app store")
  void pairExposesBothStores() {
    JCustosStoragePair pair = JCustosStorageProvider.pair();
    assertNotNull(pair);
    assertNotNull(pair.framework(), "framework store");
    assertNotNull(pair.app(), "app store");
    assertNotNull(JCustosStorageProvider.framework().auditEventStore());
    assertNotNull(JCustosStorageProvider.framework().sessionStore());
    assertNotNull(JCustosStorageProvider.app());
  }

  @Test
  @DisplayName("pair() returns the same instance across calls")
  void pairIsCached() {
    JCustosStoragePair a = JCustosStorageProvider.pair();
    JCustosStoragePair b = JCustosStorageProvider.pair();
    assertSame(a, b, "subsequent calls must return the cached pair");
  }

  @Test
  @DisplayName("framework() and app() route through the single cached pair")
  void accessorsRouteThroughPair() {
    JCustosStoragePair pair = JCustosStorageProvider.pair();
    assertSame(pair.framework(), JCustosStorageProvider.framework());
    assertSame(pair.app(), JCustosStorageProvider.app());
  }

  @Test
  @DisplayName("setPair(replacement) overrides the singleton")
  void setPairReplaces(@TempDir Path tempDir) {
    JCustosStoragePair replacement =
        JCustosStorageFactory.openAt(tempDir.resolve("override"));
    try {
      JCustosStorageProvider.setPair(replacement);
      assertSame(replacement, JCustosStorageProvider.pair(),
          "after setPair, pair() must return the replacement instance");
      assertSame(replacement.framework(), JCustosStorageProvider.framework());
      assertSame(replacement.app(), JCustosStorageProvider.app());
    } finally {
      replacement.close();
    }
  }
}
