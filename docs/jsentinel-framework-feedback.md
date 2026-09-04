# jSentinel V00.74 — Framework-Feedback

Beobachtungen aus der Anwendung von `jsentinel-vaadin` +
`jsentinel-vaadin-persistence` + `jsentinel-vaadin-hardening` auf
`core-vaadin-project-template`. Drei voneinander unabhängige
Punkte; keiner blockierend, zwei UX, einer architektonisch.

---

## 1) `EclipseStoreJSentinelStorage` ist nicht
app-erweiterbar — und das ist ein Problem

### Beobachtung

`EclipseStoreJSentinelStorage` exposed sieben framework-eigene
Sub-Stores:

```
auditEventStore()           — JSentinelAuditService
sessionStore()              — SessionStore
loginAttemptStore()          — LoginAttemptStore
roleAssignmentStore()        — RoleAssignmentStore
bootstrapStateStore()        — BootstrapStateStore
rememberMeTokenStore()       — RememberMeTokenStore
passwordResetTokenStore()    — PasswordResetTokenStore
emailVerificationTokenStore()— EmailVerificationTokenStore
```

Der EclipseStore-Root ist `EclipseStoreJSentinelRoot` (package-private)
und framework-owned. **Keine API zum Anhängen eigener App-Roots.**

### Konsequenz für Apps

App-spezifische Daten (z.B. `UserDirectory`) müssen einen
**zweiten** EclipseStore parallel hochfahren — separater
Storage-Pfad, separater Shutdown-Hook, separater
`PersistenceTypeDictionary.ptd`. Funktioniert (haben wir gemacht,
siehe `EclipseStoreUserDirectoryPersistence`), ist aber Doppelt-
Boilerplate und Doppelt-Lock-Risiko.

Das aktuelle `jsentinel-vaadin-persistence`-Skill umgeht diese
Komplikation, indem es App-Daten gar nicht in EclipseStore steckt,
sondern in `users.ser` per `java.io.ObjectOutputStream` — was sowohl
brüchig als auch inkonsistent ist (siehe Skill-Feedback separat).

### Vorschlag — Option A: App-erweiterbarer Slot

Minimal-invasiv, neuer Method-Pair:

```java
public final class EclipseStoreJSentinelStorage implements AutoCloseable {

  // existing...

  /**
   * Returns the registered app extension or {@code null} when none
   * has been set. The framework persists the extension alongside its
   * own root on every {@code storeRoot()}.
   */
  public <T> T appExtension(Class<T> type) { ... }

  /**
   * Registers an app-level extension that should be persisted next
   * to the framework root. May only be called once per storage
   * instance (typically during app bootstrap). Re-opening a storage
   * with a different extension type is a fatal error.
   */
  public <T> void appExtension(T extension) { ... }
}
```

Implementation: `EclipseStoreJSentinelRoot` bekommt ein
`Object appExtension`-Feld. Der Storage-Manager weiß durch sein
eigenes Type-Mapping, was er da geladen hat. Cast erfolgt beim
Lesen über `Class<T>`.

**Pro:** Ein Storage, ein Shutdown, ein PTD. App-Daten und
Framework-Daten teilen sich denselben atomic-commit-Zyklus.

**Contra:** App-Extension-Type ist persistiert — Header-Änderungen
brauchen ggf. EclipseStore-Legacy-Type-Mapping. Aber das gilt für
alles, was in EclipseStore landet.

### Vorschlag — Option B: separate Factory mit Storage-Sharing

Wenn Option A das jSentinel-Root-Konzept zu sehr aufweicht:

```java
public final class JSentinelStorageFactory {

  /**
   * Opens a jSentinel storage AND a parallel app storage in the
   * same parent directory. Shutdown of one triggers shutdown of the
   * other (linked lifecycle).
   */
  public static StoragePair openAt(Path parent) { ... }

  public record StoragePair(
      EclipseStoreJSentinelStorage framework,
      EmbeddedStorageManager appStorage) {}
}
```

App registriert ihren eigenen Root auf dem `appStorage`. Lifecycle
gemeinsam.

**Pro:** Klare Trennung — kein App-Code im Framework-Root.

**Contra:** Zwei Storages, aber wenigstens mit linked Lifecycle —
kein orphaned Shutdown-Hook.

### Empfehlung

Option A (App-Extension-Slot) ist die kleinere API-Erweiterung und
hat den größeren UX-Gewinn. Eine Zeile in der App reicht:

```java
storage.appExtension(new MyAppRoot());
// ...
MyAppRoot root = storage.appExtension(MyAppRoot.class);
```

---

## 2) `InitialAdminBootstrapService.createInitialAdmin(...)`
schluckt Exceptions

### Beobachtung

```java
try {
  administratorStore.createAdministrator(new NewAdministrator(...));
} catch (RuntimeException e) {
  return new InitialAdminCreationResult.InternalError(
      "could not persist administrator");
}
```

Die ursprüngliche `RuntimeException` (samt Stacktrace) wird komplett
verschluckt. Aufrufer bekommen `InternalError("could not persist
administrator")` ohne Kontext.

Konkret hat uns das eine `java.io.NotSerializableException`
gekostet — sichtbar geworden erst nachdem wir App-side selber
geloggt haben.

### Vorschlag

`InitialAdminBootstrapService` sollte die geworfene Exception
mindestens als `WARN` mit Stacktrace loggen — oder im
`InitialAdminCreationResult.InternalError`-Record einen
optionalen `Throwable cause` führen.

```java
} catch (RuntimeException e) {
  LOG.warn("Initial-admin creation failed during persist", e);
  return new InitialAdminCreationResult.InternalError(
      "could not persist administrator", e);  // ggf. mit cause
}
```

`InternalError`-Variante:

```java
record InternalError(String reason, Throwable cause)
    implements InitialAdminCreationResult { ... }
```

Apps können dann selber entscheiden, wie sie mit der Cause umgehen
(Log, Sentry, etc.) — ohne den Service umgehen zu müssen.

Dasselbe Pattern (Catch + Generic-Error + Cause weg) findet sich
auch in `RoleAssignmentService`, `PasswordResetTokenService` etc.
Konsistente Behandlung wäre wünschenswert.

### Empfehlung

Mindestens das `WARN`-Log einbauen — kostet nichts, hilft Apps
massiv beim Debugging. Cause-Throwable im Result-Record ist
optional, aber sauber.

---

## 3) `MinimumLengthPasswordPolicy` ist hart vorkonfiguriert
im App-Code — sollte aus dem Hashing-Service ableitbar sein

### Beobachtung

`BouncyCastleHashingServices.modern()` gibt einen
`PasswordHashingService` zurück, der intern eine
`PasswordHashPolicy` (für KDF-Parameter) hält — diese kennt aber
**keine Klartext-Min-Länge**. Die Klartext-Min-Länge wird separat
via `MinimumLengthPasswordPolicy` an `InitialAdminBootstrapService`
übergeben.

Effekt: App-Entwickler muss die Min-Länge an **drei** Stellen
synchron halten:

1. `new MinimumLengthPasswordPolicy(N)` in der App-side
   `BootstrapWiring`
2. Helper-Text in der `SetupView` ("Minimum N characters.")
3. Optional: Client-Side Pre-Check für UX vor Backend-Roundtrip

Wenn die drei auseinander driften, sieht der User "Minimum 8" und
bekommt eine "Password must be at least 12 characters"-Fehlermeldung
(passiert uns in dieser Session beim ersten Smoketest).

### Vorschlag

Ein `PasswordPolicy.minLength()`-Getter würde helfen — Apps können
dann pull-style den Wert für UI-Hints lesen statt ihn als Konstante
zu duplizieren:

```java
public interface PasswordPolicy {
  PasswordPolicyResult validate(char[] password);

  /**
   * Hint for UI surfaces — returns the lower bound this policy
   * enforces, or {@link OptionalInt#empty()} when the policy is
   * not length-based.
   */
  default OptionalInt minLength() { return OptionalInt.empty(); }
}

public final class MinimumLengthPasswordPolicy implements PasswordPolicy {
  // ...
  @Override
  public OptionalInt minLength() { return OptionalInt.of(minLength); }
}
```

Im SetupView dann:

```java
int min = BootstrapWiring.instance().policy().minLength().orElse(1);
passwordField.setHelperText("Minimum " + min + " characters.");
```

Eine Quelle der Wahrheit; UI-Hint und Server-Policy können nicht
mehr divergieren.

### Empfehlung

Niedrig priorisiert, aber klein. Default-Implementierung im
Interface ist abwärtskompatibel.

---

## Zusammenfassung

| # | Punkt | Aufwand | Wirkung |
|---|---|---|---|
| 1 | App-Extension-Slot im EclipseStore-Root | mittel (API + Impl) | massiv — App-Daten konsistent in Framework-Storage |
| 2 | Exception-Logging im `InitialAdminBootstrapService` | trivial (1–2 Zeilen) | massiv — versteckte Bugs werden sichtbar |
| 3 | `PasswordPolicy.minLength()` als Hint-API | trivial (Interface-Default) | klein — verhindert UI-Server-Drift |

Punkt 2 würde ich als allererstes angehen — es kostet nichts und
schützt jeden App-Entwickler vor genau der Sackgasse, in der wir
heute gelandet sind.

Punkt 1 ist der größere Architektur-Step. Solange er aussteht,
müssen App-spezifische Persistenz-Skills (wie
`jsentinel-vaadin-persistence`) entweder schmuddel-Workarounds
nutzen (aktueller Zustand: `users.ser` per JDK-Ser) oder einen
zweiten EclipseStore parallel hochfahren (Lösung im jetzigen
Projekt).
