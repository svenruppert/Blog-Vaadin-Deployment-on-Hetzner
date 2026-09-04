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

package junit.com.svenruppert.flow.i18n;

import com.svenruppert.flow.i18n.I18n;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link I18n#tr(String, String, Object...)} on the no-Vaadin-context
 * path — the branch the facade exists for. With no {@code VaadinService}
 * current, the provider lookup throws, is swallowed, and the inline
 * fallback must win.
 *
 * <p>The current service and UI are cleared per test: a BrowserlessTest
 * running earlier in the same JVM would otherwise leave one installed and
 * make these assertions depend on execution order.
 */
@DisplayName("I18n facade — fallback behavior without a Vaadin context")
class I18nFacadeTest {

  private static final String KEY = "some.unknown.key";

  @BeforeEach
  void clearVaadinContext() {
    VaadinService.setCurrent(null);
    UI.setCurrent(null);
  }

  @Test
  @DisplayName("the inline fallback is returned when no provider is reachable")
  void fallbackWins() {
    assertEquals("Sign in", I18n.tr(KEY, "Sign in"),
        "without a VaadinService the fallback must be returned verbatim");
  }

  @Test
  @DisplayName("a null fallback degrades to the key, never to null")
  void nullFallbackDegradesToKey() {
    assertEquals(KEY, I18n.tr(KEY, null),
        "contract says never null and never blank — the key is the last resort");
  }

  @Test
  @DisplayName("placeholders are substituted when params are supplied")
  void placeholdersAreFormatted() {
    assertEquals("Hello World", I18n.tr(KEY, "Hello {0}", "World"),
        "MessageFormat must be applied to the fallback pattern");
  }

  @Test
  @DisplayName("several placeholders are substituted positionally")
  void multiplePlaceholders() {
    assertEquals("a then b", I18n.tr(KEY, "{0} then {1}", "a", "b"));
  }

  @Test
  @DisplayName("without params the pattern is returned unformatted")
  void noParamsLeavesPatternIntact() {
    assertEquals("Hello {0}", I18n.tr(KEY, "Hello {0}"),
        "no params means no MessageFormat pass — braces must survive");
  }

  @Test
  @DisplayName("an explicitly null param array is treated as no params")
  void nullParamArrayIsNoParams() {
    assertEquals("Hello {0}", I18n.tr(KEY, "Hello {0}", (Object[]) null),
        "a null varargs array must not blow up and must skip formatting");
  }

  @Test
  @DisplayName("an empty param array is treated as no params")
  void emptyParamArrayIsNoParams() {
    assertEquals("Hello {0}", I18n.tr(KEY, "Hello {0}", new Object[0]),
        "an empty varargs array must skip formatting");
  }
}
