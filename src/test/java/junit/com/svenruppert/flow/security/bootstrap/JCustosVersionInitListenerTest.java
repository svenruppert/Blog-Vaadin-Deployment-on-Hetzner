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

import com.svenruppert.flow.security.bootstrap.JCustosVersionInitListener;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.session.JCustosVersionStore;
import com.vaadin.flow.server.ServiceInitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JCustosVersionInitListener — serviceInit() wires the drift enforcer")
class JCustosVersionInitListenerTest {

  private JCustosVersionStore savedStore;

  @BeforeEach
  void capturePrior() {
    savedStore = JCustosServiceResolver.findJCustosVersionStore().orElse(null);
  }

  @AfterEach
  void restorePrior() {
    if (savedStore != null) {
      JCustosServiceResolver.setJCustosVersionStore(savedStore);
    }
  }

  @Test
  @DisplayName("SPI lookup must succeed in this project — sanity precondition")
  void spiPresent() {
    Optional<JCustosVersionStore> store =
        JCustosServiceResolver.findJCustosVersionStore();
    assertTrue(store.isPresent(),
        "META-INF/services must register a JCustosVersionStore — drift "
            + "detection would silently no-op without it");
    assertNotNull(store.get());
  }

  @Test
  @DisplayName("with SPI present → exactly one UIInitListener registered")
  void presentSpiRegistersOneListener() {
    RecordingVaadinService service = new RecordingVaadinService();

    new JCustosVersionInitListener().serviceInit(new ServiceInitEvent(service));

    assertEquals(1, service.captured.size(),
        "with the version store SPI present, exactly one UIInitListener "
            + "must register");
  }

  @Test
  @DisplayName("listener instance is constructable without arguments — Vaadin SPI contract")
  void constructorIsNoArg() {
    JCustosVersionInitListener instance = new JCustosVersionInitListener();
    assertNotNull(instance,
        "Vaadin requires a no-arg constructor for VaadinServiceInitListener "
            + "implementations registered via META-INF/services");
  }
}
