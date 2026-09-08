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

import com.svenruppert.flow.i18n.I18nSupport;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the miss-detection in {@link I18nSupport}. The mixin treats null,
 * blank, and key-echo answers from Vaadin's {@code getTranslation} as
 * "not translated" — DefaultI18NProvider echoes the key on a miss, so
 * that third case is the one that actually fires in production.
 *
 * <p>Uses a stub component rather than a Vaadin context so each case can
 * be driven deterministically.
 */
@DisplayName("I18nSupport — treats null, blank and key-echo as a miss")
class I18nSupportTest {

  private static final String KEY = "nav.home";
  private static final String FALLBACK = "Home";

  /** Component stub returning a canned answer for every translation call. */
  private static final class StubComponent extends Div implements I18nSupport {

    private final String canned;

    private StubComponent(String canned) {
      this.canned = canned;
    }

    @Override
    public String getTranslation(String key, Object... params) {
      return canned;
    }
  }

  private static I18nSupport answering(String canned) {
    return new StubComponent(canned);
  }

  // ── tr(key, fallback) ──────────────────────────────────────────

  @Test
  @DisplayName("a real translation is returned as-is")
  void hitWins() {
    assertEquals("Start", answering("Start").tr(KEY, FALLBACK));
  }

  @Test
  @DisplayName("a null translation falls back")
  void nullIsMiss() {
    assertEquals(FALLBACK, answering(null).tr(KEY, FALLBACK));
  }

  @Test
  @DisplayName("a blank translation falls back")
  void blankIsMiss() {
    assertEquals(FALLBACK, answering("   ").tr(KEY, FALLBACK));
  }

  @Test
  @DisplayName("an echoed key falls back — DefaultI18NProvider's miss signal")
  void keyEchoIsMiss() {
    assertEquals(FALLBACK, answering(KEY).tr(KEY, FALLBACK));
  }

  // ── tr(key, fallback, params) ──────────────────────────────────

  @Test
  @DisplayName("a real translation wins over the formatted fallback")
  void hitWinsWithParams() {
    assertEquals("Hallo", answering("Hallo").tr(KEY, "Hello {0}", "World"));
  }

  @Test
  @DisplayName("a miss formats the fallback with the params")
  void missFormatsFallback() {
    assertEquals("Hello World",
        answering(null).tr(KEY, "Hello {0}", "World"));
  }

  @Test
  @DisplayName("a blank translation formats the fallback")
  void blankMissFormatsFallback() {
    assertEquals("Hello World",
        answering("  ").tr(KEY, "Hello {0}", "World"));
  }

  @Test
  @DisplayName("an echoed key formats the fallback")
  void keyEchoFormatsFallback() {
    assertEquals("Hello World",
        answering(KEY).tr(KEY, "Hello {0}", "World"));
  }

  // ── tr(key) ────────────────────────────────────────────────────

  @Test
  @DisplayName("the no-fallback overload passes the provider answer straight through")
  void noFallbackOverloadIsTransparent() {
    assertEquals("Start", answering("Start").tr(KEY));
  }
}
