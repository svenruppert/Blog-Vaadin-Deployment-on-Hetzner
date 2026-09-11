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

package com.svenruppert.flow.security.credentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the API token, preferring systemd credentials.
 *
 * <p>The reference server hands the token to the service through
 * {@code LoadCredentialEncrypted=api-token:/etc/vaadinapp/api-token.cred}. systemd
 * decrypts it into a private tmpfs and points {@code CREDENTIALS_DIRECTORY} at
 * it. The point of that mechanism is where the secret is <em>not</em>: not in
 * the unit file, not in the environment, not in {@code ps aux}, not in
 * {@code /proc/PID/environ}. Reading it from a file under that directory is the
 * only supported way to get at it.
 *
 * <p>Resolution order, so the application still starts on a developer machine
 * without systemd:
 *
 * <ol>
 *   <li>{@code $CREDENTIALS_DIRECTORY/api-token}</li>
 *   <li>system property {@code app.api.token}</li>
 *   <li>environment variable {@code APP_API_TOKEN}</li>
 *   <li>absent</li>
 * </ol>
 *
 * <p><strong>The value is never logged and never rendered.</strong> What can be
 * shown is that it arrived: where from, how long it is, and a short fingerprint.
 * A screenshot of the value in an article would demonstrate exactly the mistake
 * the credentials mechanism exists to prevent.
 */
public final class ApiTokenSource {

  /** Directory systemd points at when credentials are configured. */
  public static final String CREDENTIALS_DIRECTORY = "CREDENTIALS_DIRECTORY";

  /** File name of the credential, matching the unit's LoadCredentialEncrypted. */
  public static final String CREDENTIAL_NAME = "api-token";

  /** System property consulted when no credential is present. */
  public static final String PROPERTY = "app.api.token";

  /** Environment variable consulted when neither credential nor property is set. */
  public static final String ENV = "APP_API_TOKEN";

  private static final Logger LOG = LoggerFactory.getLogger(ApiTokenSource.class);

  /** Where the token came from. Part of the diagnostic view, so it is public. */
  public enum Origin {
    SYSTEMD_CREDENTIAL, SYSTEM_PROPERTY, ENVIRONMENT, ABSENT
  }

  /**
   * What may be shown about the token. Deliberately does not carry the value:
   * anything handed to the UI layer can end up in a screenshot.
   *
   * @param origin      where the token was found
   * @param length      number of characters, 0 when absent
   * @param fingerprint first eight hex characters of the SHA-256 digest, empty when absent
   */
  public record Status(Origin origin, int length, String fingerprint) {

    public boolean present() {
      return origin != Origin.ABSENT;
    }
  }

  private final String token;
  private final Status status;

  private ApiTokenSource(String token, Origin origin) {
    this.token = token;
    this.status = token == null
        ? new Status(Origin.ABSENT, 0, "")
        : new Status(origin, token.length(), fingerprint(token));
  }

  /** Resolves the token once, in the documented order. */
  public static ApiTokenSource resolve() {
    String fromCredential = readCredential();
    if (fromCredential != null) {
      return logged(new ApiTokenSource(fromCredential, Origin.SYSTEMD_CREDENTIAL));
    }
    String fromProperty = System.getProperty(PROPERTY);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return logged(new ApiTokenSource(fromProperty.trim(), Origin.SYSTEM_PROPERTY));
    }
    String fromEnv = System.getenv(ENV);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return logged(new ApiTokenSource(fromEnv.trim(), Origin.ENVIRONMENT));
    }
    return logged(new ApiTokenSource(null, Origin.ABSENT));
  }

  private static ApiTokenSource logged(ApiTokenSource source) {
    Status s = source.status();
    // Origin, length and fingerprint only. Logging the value would put it into
    // journalctl, which is precisely what the credentials mechanism avoids.
    LOG.info("api token: {} (length {}, fingerprint {})",
        s.origin(), s.length(), s.fingerprint().isEmpty() ? "-" : s.fingerprint());
    return source;
  }

  private static String readCredential() {
    String dir = System.getenv(CREDENTIALS_DIRECTORY);
    if (dir == null || dir.isBlank()) {
      return null;
    }
    Path file = Path.of(dir).resolve(CREDENTIAL_NAME);
    try {
      if (!Files.isReadable(file)) {
        return null;
      }
      String value = Files.readString(file, StandardCharsets.UTF_8).trim();
      return value.isEmpty() ? null : value;
    } catch (IOException failure) {
      // The path is logged, the content is not.
      LOG.warn("api token: credential at {} could not be read", file, failure);
      return null;
    }
  }

  private static String fingerprint(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 4);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by the platform", impossible);
    }
  }

  /** What may be displayed. Never contains the value. */
  public Status status() {
    return status;
  }

  /**
   * The token itself, for callers that actually need it.
   *
   * @return the token, or {@code null} when none was configured
   */
  public String value() {
    return token;
  }
}
