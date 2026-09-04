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

import com.svenruppert.flow.views.ui.EmptyState;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmptyState is pure component assembly — no UI scope required, so this
 * runs as a plain JUnit test rather than a BrowserlessTest.
 *
 * <p>Covers the two decisions the class actually makes: whether the body
 * paragraph is rendered at all, and that {@code withAction} is chainable.
 */
@DisplayName("EmptyState — heading, optional body, chainable action")
class EmptyStateTest {

  private static final String HEADING = "No audit events yet";
  private static final String BODY =
      "Events appear here as soon as someone signs in.";

  private static String textOf(Component component) {
    assertTrue(component instanceof HasText,
        "expected a text-bearing component, got: " + component.getClass());
    return ((HasText) component).getText();
  }

  @Test
  @DisplayName("heading is rendered as the second child, after the icon")
  void headingIsRendered() {
    List<Component> children =
        new EmptyState(VaadinIcon.RECORDS, HEADING, BODY).getChildren().toList();

    assertEquals(HEADING, textOf(children.get(1)),
        "heading must follow the icon");
  }

  @Test
  @DisplayName("a body adds a third child carrying the body text")
  void bodyAddsParagraph() {
    List<Component> children =
        new EmptyState(VaadinIcon.RECORDS, HEADING, BODY).getChildren().toList();

    assertEquals(3, children.size(), "icon + heading + body");
    assertEquals(BODY, textOf(children.get(2)), "third child must carry the body");
  }

  @Test
  @DisplayName("a null body renders icon and heading only")
  void nullBodyIsDropped() {
    List<Component> children =
        new EmptyState(VaadinIcon.RECORDS, HEADING, null).getChildren().toList();

    assertEquals(2, children.size(), "null body must not add a paragraph");
  }

  @Test
  @DisplayName("a blank body counts as no body")
  void blankBodyIsDropped() {
    List<Component> children =
        new EmptyState(VaadinIcon.RECORDS, HEADING, "   ").getChildren().toList();

    assertEquals(2, children.size(),
        "whitespace-only body must be treated the same as null");
  }

  @Test
  @DisplayName("withAction appends the action and returns the same instance")
  void withActionIsChainable() {
    EmptyState state = new EmptyState(VaadinIcon.RECORDS, HEADING, BODY);
    Button action = new Button("Refresh");

    EmptyState returned = state.withAction(action);

    assertSame(state, returned, "withAction must return this for chaining");
    List<Component> children = state.getChildren().toList();
    assertEquals(4, children.size(), "icon + heading + body + action");
    assertSame(action, children.get(3), "action must be appended last");
  }
}
