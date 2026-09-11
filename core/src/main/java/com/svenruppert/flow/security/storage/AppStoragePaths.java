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

package com.svenruppert.flow.security.storage;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single source of truth for the application's on-disk storage paths.
 *
 * <p>Production runs use {@code ./data/} as the base. Tests, CI and
 * any deployment that wants to redirect persistence elsewhere
 * override the base via the JVM system property
 * {@code -Dapp.storage.dir=/some/path}.
 *
 * <p>The single {@code JCustosStoragePair} is opened at
 * {@link #baseDir()} and derives its own {@code jcustos-store}
 * (framework) and {@code app-store} (application) subdirectories.
 * Two further derived locations live here:
 * <ul>
 *   <li>{@link #frameworkStorageDir()} — anchors the one-time
 *       bootstrap token file (see {@link #bootstrapTokenFile()}).</li>
 *   <li>{@link #bootstrapTokenFile()} — the one-time bootstrap token
 *       written by {@code BootstrapStartup} on first start.</li>
 * </ul>
 *
 * <p>This file is deliberately tiny — keeping path policy in one
 * place avoids drift when redirecting storage at runtime. Surefire
 * sets {@code app.storage.dir = ${project.build.directory}/test-data}
 * via {@code <systemPropertyVariables>}, so test runs never touch
 * the repository-rooted {@code ./data/} directory.
 */
public final class AppStoragePaths {

  /** System-property name for the storage base directory. */
  public static final String PROPERTY = "app.storage.dir";

  /** Environment variable read when the system property is not set. */
  public static final String ENV = "APP_STORAGE_DIR";

  /** Built-in default when nothing was configured. */
  public static final String DEFAULT = "./data";

  private static final Logger LOG = LoggerFactory.getLogger(AppStoragePaths.class);

  private AppStoragePaths() {
  }

  /**
   * Base directory for all app-owned storage.
   *
   * <p>Resolved from the system property first, then the environment variable,
   * then the built-in default. Reading only the property was the earlier
   * behaviour and it failed quietly: in containers and most orchestrators the
   * environment variable is the usual way to configure a path, and an ignored
   * setting does not produce an error - it produces {@code ./data} relative to
   * whatever the working directory happens to be. The application then finds an
   * empty store where its data should have been, which looks like data loss.
   *
   * <p>The resolved origin is logged once, because that is the case somebody
   * needs to see before they start wondering.
   */
  public static Path baseDir() {
    String fromProperty = System.getProperty(PROPERTY);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return logged(fromProperty, "system property " + PROPERTY);
    }
    String fromEnv = System.getenv(ENV);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return logged(fromEnv, "environment " + ENV);
    }
    return logged(DEFAULT, "default - no property, no environment");
  }

  private static Path logged(String value, String origin) {
    Path path = Path.of(value);
    if (ORIGIN_LOGGED.compareAndSet(false, true)) {
      LOG.info("storage base dir: {} ({})", path.toAbsolutePath(), origin);
    }
    return path;
  }

  private static final AtomicBoolean ORIGIN_LOGGED = new AtomicBoolean();

  /**
   * Anchor directory for the bootstrap token file. Independent of the
   * storage pair's own {@code jcustos-store} data directory.
   */
  public static Path frameworkStorageDir() {
    return baseDir().resolve("jcustos");
  }

  /** Bootstrap token file written on first start. */
  public static Path bootstrapTokenFile() {
    return frameworkStorageDir().resolve("bootstrap.token");
  }
}
