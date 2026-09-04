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

package junit.com.svenruppert.flow.security.storage;

import com.svenruppert.flow.security.storage.AppStoragePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the single-source-of-truth storage paths. Each accessor is
 * called directly here (rather than only via the lazily-cached storage
 * pair / bootstrap singleton) so the derivation logic stays covered and
 * cannot silently return {@code null} or drop a path segment.
 */
@DisplayName("AppStoragePaths — base + derived path derivation")
class AppStoragePathsTest {

  @Test
  @DisplayName("baseDir() is non-null and honours the configured storage dir")
  void baseDirHonoursProperty() {
    Path base = AppStoragePaths.baseDir();
    assertNotNull(base, "baseDir() must never be null");
    Path expected = Path.of(
        System.getProperty(AppStoragePaths.PROPERTY, AppStoragePaths.DEFAULT));
    assertEquals(expected, base,
        "baseDir() must resolve from the " + AppStoragePaths.PROPERTY + " property");
  }

  @Test
  @DisplayName("frameworkStorageDir() = <base>/jcustos")
  void frameworkStorageDirUnderBase() {
    Path fw = AppStoragePaths.frameworkStorageDir();
    assertNotNull(fw, "frameworkStorageDir() must never be null");
    assertEquals("jcustos", fw.getFileName().toString());
    assertEquals(AppStoragePaths.baseDir(), fw.getParent(),
        "framework dir must sit directly under baseDir()");
  }

  @Test
  @DisplayName("bootstrapTokenFile() = <base>/jcustos/bootstrap.token")
  void bootstrapTokenFileUnderFrameworkDir() {
    Path token = AppStoragePaths.bootstrapTokenFile();
    assertNotNull(token, "bootstrapTokenFile() must never be null");
    assertEquals("bootstrap.token", token.getFileName().toString());
    assertEquals(AppStoragePaths.frameworkStorageDir(), token.getParent(),
        "token file must sit inside the framework storage dir");
  }
}
