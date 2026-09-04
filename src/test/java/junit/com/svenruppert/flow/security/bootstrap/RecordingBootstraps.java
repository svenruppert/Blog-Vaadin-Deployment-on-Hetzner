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

import eu.jsentinel.jcustos.audit.AuditEventStore;
import eu.jsentinel.jcustos.audit.JCustosAuditService;
import eu.jsentinel.jcustos.authentication.PasswordHasher;
import eu.jsentinel.jcustos.authorization.api.SubjectIdResolver;
import eu.jsentinel.jcustos.credential.password.PasswordHashingService;
import eu.jsentinel.jcustos.credential.password.pepper.PepperService;
import eu.jsentinel.jcustos.credential.store.CredentialStore;
import eu.jsentinel.jcustos.dx.bootstrap.AuditBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.CredentialBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.SessionBootstrap;
import eu.jsentinel.jcustos.session.JCustosVersionStore;
import eu.jsentinel.jcustos.session.SessionPolicy;
import eu.jsentinel.jcustos.session.SessionStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only recording stubs for the three fluent sub-builders the
 * {@code BootstrapExtension} contract operates on. Each call records
 * its argument and returns {@code this} so chained calls work.
 */
final class RecordingBootstraps {

  private RecordingBootstraps() {
  }

  static final class RecAudit implements AuditBootstrap {
    final List<JCustosAuditService> serviceCalls = new ArrayList<>();
    final List<AuditEventStore> storeBackedCalls = new ArrayList<>();
    int loggingCalls;
    final List<Integer> ringBufferSizes = new ArrayList<>();
    final List<Boolean> credentialEventsCalls = new ArrayList<>();

    @Override public AuditBootstrap securityAuditService(JCustosAuditService s) {
      serviceCalls.add(s); return this;
    }
    @Override public AuditBootstrap storeBacked(AuditEventStore store) {
      storeBackedCalls.add(store); return this;
    }
    @Override public AuditBootstrap logging() {
      loggingCalls++; return this;
    }
    @Override public AuditBootstrap ringBuffer(int size) {
      ringBufferSizes.add(size); return this;
    }
    @Override public AuditBootstrap credentialEvents(boolean v) {
      credentialEventsCalls.add(v); return this;
    }
  }

  static final class RecSession implements SessionBootstrap {
    final List<SessionStore> storeBackedCalls = new ArrayList<>();
    final List<JCustosVersionStore> versionStoreCalls = new ArrayList<>();
    final List<SubjectIdResolver<?>> resolverCalls = new ArrayList<>();
    final List<Duration> timeoutCalls = new ArrayList<>();
    final List<Duration> lifetimeCalls = new ArrayList<>();
    final List<SessionPolicy<?>> policyCalls = new ArrayList<>();

    @Override public SessionBootstrap storeBacked(SessionStore s) {
      storeBackedCalls.add(s); return this;
    }
    @Override public SessionBootstrap securityVersion(JCustosVersionStore s) {
      versionStoreCalls.add(s); return this;
    }
    @Override public SessionBootstrap subjectIdResolver(SubjectIdResolver<?> r) {
      resolverCalls.add(r); return this;
    }
    @Override public SessionBootstrap timeout(Duration d) {
      timeoutCalls.add(d); return this;
    }
    @Override public SessionBootstrap absoluteLifetime(Duration d) {
      lifetimeCalls.add(d); return this;
    }
    @Override public SessionBootstrap policy(SessionPolicy<?> p) {
      policyCalls.add(p); return this;
    }
  }

  static final class RecCredentials implements CredentialBootstrap {
    final List<PasswordHashingService> hashingCalls = new ArrayList<>();
    final List<PasswordHasher> hasherCalls = new ArrayList<>();
    final List<PepperService> pepperCalls = new ArrayList<>();
    final List<CredentialStore> storeCalls = new ArrayList<>();
    int pbkdf2Calls;
    int modernCalls;

    @Override public CredentialBootstrap passwordHasher(PasswordHasher h) {
      hasherCalls.add(h); return this;
    }
    @Override public CredentialBootstrap hashing(PasswordHashingService s) {
      hashingCalls.add(s); return this;
    }
    @Override public CredentialBootstrap pbkdf2Defaults() {
      pbkdf2Calls++; return this;
    }
    @Override public CredentialBootstrap modern() {
      modernCalls++; return this;
    }
    @Override public CredentialBootstrap pepper(PepperService p) {
      pepperCalls.add(p); return this;
    }
    @Override public CredentialBootstrap credentialStore(CredentialStore s) {
      storeCalls.add(s); return this;
    }
    @Override public CredentialBootstrap passwordChange(
        eu.jsentinel.jcustos.credential.change.PasswordChangeService s) {
      return this;
    }
    @Override public CredentialBootstrap passwordReset(
        eu.jsentinel.jcustos.credential.reset.PasswordResetService s) {
      return this;
    }
  }
}
