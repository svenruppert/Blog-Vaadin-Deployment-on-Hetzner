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

package junit.com.svenruppert.flow.views.ui;

import com.svenruppert.flow.views.ui.MetricTile;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MetricTile is pure component assembly — no UI scope required, so this
 * runs as a plain JUnit test rather than a BrowserlessTest.
 *
 * <p>The assertions target the tile's actual contract: the value is
 * rendered, and the hint is a genuinely optional third row (absent for
 * both {@code null} and blank input).
 */
@DisplayName("MetricTile — value row plus optional hint")
class MetricTileTest {

  private static final String LABEL = "Active sessions";
  private static final String VALUE = "42";
  private static final String HINT = "+6 in the last hour";

  private static String textOf(Component component) {
    assertTrue(component instanceof HasText,
        "expected a text-bearing component, got: " + component.getClass());
    return ((HasText) component).getText();
  }

  @Test
  @DisplayName("without a hint the tile has exactly the icon row and the value")
  void withoutHintTwoRows() {
    List<Component> children =
        new MetricTile(VaadinIcon.USERS, LABEL, VALUE).getChildren().toList();

    assertEquals(2, children.size(),
        "icon row + value only — a null hint must not add a row");
  }

  @Test
  @DisplayName("the value is rendered into the tile")
  void valueIsRendered() {
    List<Component> children =
        new MetricTile(VaadinIcon.USERS, LABEL, VALUE).getChildren().toList();

    assertEquals(VALUE, textOf(children.get(1)),
        "second row must carry the metric value");
  }

  @Test
  @DisplayName("a hint adds a third row carrying the hint text")
  void hintAddsThirdRow() {
    List<Component> children =
        new MetricTile(VaadinIcon.USERS, LABEL, VALUE, HINT).getChildren().toList();

    assertEquals(3, children.size(), "icon row + value + hint");
    assertEquals(HINT, textOf(children.get(2)), "third row must carry the hint");
  }

  @Test
  @DisplayName("a blank hint counts as no hint")
  void blankHintIsDropped() {
    List<Component> children =
        new MetricTile(VaadinIcon.USERS, LABEL, VALUE, "   ").getChildren().toList();

    assertEquals(2, children.size(),
        "whitespace-only hint must be treated the same as null");
  }

  @Test
  @DisplayName("an empty hint counts as no hint")
  void emptyHintIsDropped() {
    List<Component> children =
        new MetricTile(VaadinIcon.USERS, LABEL, VALUE, "").getChildren().toList();

    assertEquals(2, children.size(), "empty hint must not add a row");
  }
}
