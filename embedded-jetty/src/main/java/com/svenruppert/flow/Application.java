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

package com.svenruppert.flow;

import com.svenruppert.dependencies.core.logger.HasLogger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.ee11.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee11.webapp.WebAppContext;
import org.eclipse.jetty.ee11.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Starts the application on an embedded Jetty.
 *
 * <p>There is no installed server here, no {@code JETTY_HOME} and no module
 * mechanism. Everything the external Jetty was configured to do through
 * {@code start.d/*.ini} files is assembled in {@link #server(String, int)}
 * instead — which makes visible what those files were actually for.
 *
 * <p>Three of those settings fail <em>silently</em> when they are forgotten:
 *
 * <ul>
 *   <li>{@link ForwardedRequestCustomizer} — behind a reverse proxy the request
 *       reaches Jetty from {@code 127.0.0.1} over plain HTTP. Without the
 *       customizer the application concludes it is not on HTTPS, drops the
 *       {@code Secure} flag from the session cookie and builds redirects on
 *       {@code http://}. Nothing fails, nothing is logged.</li>
 *   <li>The websocket container — Vaadin Push needs it. Without it the
 *       connection quietly degrades instead of erroring out.</li>
 *   <li>The {@code i18n.provider} init parameter, which the WAR gets from
 *       {@code web.xml}. Without it Vaadin falls back to
 *       {@code DefaultI18NProvider} and the locale switcher stops working.</li>
 * </ul>
 *
 * <p>Evaluating forwarding headers is only safe because the connector binds to
 * the loopback address: a header may be trusted when the only party that can
 * set it is the proxy on the same host.
 *
 * <p>Host and port come from {@code app.host} / {@code app.port} or the
 * matching {@code APP_HOST} / {@code APP_PORT} environment variables; defaults
 * are {@code 127.0.0.1:8080}.
 */
public final class Application implements HasLogger {

  private static final String DEFAULT_HOST = "127.0.0.1";
  private static final int DEFAULT_PORT = 8080;

  /**
   * Where the servlet specification says a jar contributes web resources:
   * files under {@code META-INF/resources} of a jar are served as if they sat
   * in the web application root. The WAR gets this for free from the
   * container; embedded, the base resource has to be assembled by hand.
   */
  private static final String WEB_RESOURCES = "META-INF/resources/";

  /**
   * The marker the Vaadin production build leaves behind. Checked before the
   * server starts, because its absence is otherwise invisible: Jetty comes up,
   * answers 200 and serves a blank page — the error appears only in the
   * browser console.
   */
  private static final String BUNDLE_MARKER = "META-INF/VAADIN/webapp/index.html";

  /**
   * Which archives the annotation scanner opens. Jetty walks every match with
   * ASM to find {@code ServletContainerInitializer}s — that is how Vaadin
   * discovers its {@code @Route} classes. The default reads all of them;
   * {@code -Dapp.scan.pattern} narrows it, which measurably shortens startup
   * at the price of having to maintain the list.
   */
  private static final String DEFAULT_SCAN_PATTERN = ".*\\.jar$";

  private Application() {
  }

  public static void main(String[] args) {
    new Application().launch();
  }

  private void launch() {
    String host = resolve("app.host", "APP_HOST", DEFAULT_HOST);
    int port = resolvePort();

    if (Application.class.getClassLoader().getResource(BUNDLE_MARKER) == null) {
      abort("Vaadin production bundle missing from the classpath (expected " + BUNDLE_MARKER
            + "). Build with `mvn -Pproduction package` before starting; without it the "
            + "server starts, answers 200 and serves a blank page.");
      return;
    }

    Server server = null;
    try {
      server = server(host, port);
      server.start();
      Runtime.getRuntime().addShutdownHook(new Thread(this::noop, "jetty-shutdown-marker"));
      logger().info("Embedded Jetty serving http://{}:{}/", host, port);
      server.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger().warn("Server thread interrupted while joining", e);
    } catch (Exception e) {
      logger().error("Failed to start embedded Jetty on {}:{}", host, port, e);
      stop(server);
      abort(null);
    }
  }

  /**
   * Assembles the server. Reads top to bottom as the list of decisions the
   * module files used to hold.
   */
  private Server server(String host, int port) throws IOException {
    Server server = new Server();

    // What `--add-modules=forwarded` did on the external Jetty.
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.addCustomizer(new ForwardedRequestCustomizer());
    httpConfig.setSendServerVersion(false);

    // What `start.d/http.ini` did: bind to loopback only, never to 0.0.0.0.
    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    connector.setHost(host);
    connector.setPort(port);
    server.addConnector(connector);

    WebAppContext webapp = new WebAppContext();
    webapp.setContextPath("/");
    webapp.setBaseResource(webResources(webapp));

    String scanPattern = System.getProperty("app.scan.pattern", DEFAULT_SCAN_PATTERN);
    webapp.setAttribute(MetaInfConfiguration.CONTAINER_JAR_PATTERN, scanPattern);
    webapp.setAttribute(MetaInfConfiguration.WEBINF_JAR_PATTERN, scanPattern);
    webapp.setParentLoaderPriority(true);

    // What `--add-modules=ee11-websocket-jakarta` did. Must be configured
    // before the context starts, or Push falls back and no one notices.
    JakartaWebSocketServletContainerInitializer.configure(webapp, null);

    // What WEB-INF/web.xml did in the WAR. Production mode is deliberately not
    // set here: Vaadin derives it from META-INF/VAADIN/config/flow-build-info.json,
    // which build-frontend writes. Forcing it would claim a production bundle
    // that may not exist.
    ServletHolder holder = new ServletHolder(new AppServlet());
    holder.setInitParameter("i18n.provider", "com.svenruppert.flow.i18n.AppI18NProvider");
    holder.setAsyncSupported(true);
    holder.setInitOrder(1);
    webapp.addServlet(holder, "/*");

    server.setHandler(webapp);
    server.setStopAtShutdown(true);
    return server;
  }

  /**
   * Collects every {@code META-INF/resources} root on the classpath into one
   * base resource.
   *
   * <p>A single {@code newClassLoaderResource} lookup is not enough: several
   * jars contribute such a root — the core module with the images and icons,
   * and the Vaadin jars with their themes and the client engine. Combining
   * them is what makes {@code ServletContext.getResource(...)} — the only
   * lookup Vaadin's static file server uses — find all of them.
   */
  private Resource webResources(WebAppContext webapp) throws IOException {
    ResourceFactory factory = ResourceFactory.of(webapp);
    List<Resource> roots = Collections
        .list(Application.class.getClassLoader().getResources(WEB_RESOURCES))
        .stream()
        .map(url -> factory.newResource(url.toString()))
        .filter(java.util.Objects::nonNull)
        .toList();
    logger().info("Serving static web resources from {} classpath root(s)", roots.size());
    return ResourceFactory.combine(roots);
  }

  private void noop() {
    // The shutdown hook exists only so setStopAtShutdown(true) has a peer that
    // keeps the JVM from exiting before Jetty's own hook has run.
  }

  private void stop(Server server) {
    if (server == null) return;
    try {
      server.stop();
    } catch (Exception e) {
      logger().warn("Error during Jetty shutdown", e);
    }
  }

  @SuppressFBWarnings(value = "DM_EXIT",
      justification = "intentional — main-class launcher exits non-zero so a process supervisor sees the failure")
  private void abort(String message) {
    if (message != null) {
      logger().error(message);
    }
    System.exit(1);
  }

  private static String resolve(String systemProperty, String envVariable, String fallback) {
    String fromSystem = System.getProperty(systemProperty);
    if (fromSystem != null && !fromSystem.isBlank()) return fromSystem;
    String fromEnv = System.getenv(envVariable);
    if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
    return fallback;
  }

  private static int resolvePort() {
    String raw = resolve("app.port", "APP_PORT", Integer.toString(DEFAULT_PORT));
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return DEFAULT_PORT;
    }
  }
}
