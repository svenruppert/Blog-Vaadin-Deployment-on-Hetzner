#!/bin/sh
#
# Starts the packaged application.
#
# The split this script embodies is the point of it, not an accident:
#
#   Everything that belongs to the APPLICATION lives here and is versioned with
#   the release - the classpath, the JVM flags this particular build needs, and
#   the main class. They change together with the code, so they travel with it.
#
#   Everything that belongs to the MACHINE arrives through the environment -
#   data directory, temporary directory, memory ceiling, credentials. They
#   change with the host, not with the release, so the systemd unit owns them.
#
# Three details are not stylistic:
#
#   1. exec. Without it this shell stays alive as the parent process and systemd
#      supervises the shell rather than the JVM. SIGTERM on restart would reach
#      the wrong process, and SuccessExitStatus=143 would no longer describe
#      what the service actually does.
#
#   2. The classpath wildcard reaches java as a string. If the shell expanded
#      it, java would receive a space-separated list of file names instead of a
#      classpath and would refuse all but the first.
#
#   3. The script writes nothing next to itself - no PID file, no log
#      redirection. Under ProtectSystem=strict the release directory is
#      read-only for the service, and a write here would abort the start.

set -eu

# Resolve the release root from the script's own location, so the distribution
# can be unpacked anywhere and still start. CDPATH is cleared because a CDPATH
# set in the caller's environment would make cd print and pick a wrong directory.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

# A runtime shipped inside the release wins over everything else. That is the
# last thing this series moves inside the dashed line: first the server, then
# the packaging, now the Java runtime itself. A release that carries one no
# longer cares what is installed on the host - and the same unit still starts a
# release that does not, because the check is for a file, not a setting.
#
# Order: bundled runtime, then JAVA_HOME, then whatever is on PATH.
if [ -x "$APP_HOME/runtime/bin/java" ]; then
  JAVA_BIN="$APP_HOME/runtime/bin/java"
else
  JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/java}
  JAVA_BIN=${JAVA_BIN:-java}
fi

# Properties of THIS build, not of the host:
#   --add-exports          EclipseStore reaches into jdk.internal.misc
#   --enable-native-access silences the restricted-method warning for the same
# A release that stops needing these drops them here - without touching the host.
APP_OPTS="--add-exports java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"

# Properties of the HOST. The unit (or the caller) fills this in; empty by default.
JAVA_OPTS=${JAVA_OPTS:-}

CLASSPATH="$APP_HOME/app/*:$APP_HOME/lib/*"

# APP_OPTS and JAVA_OPTS are deliberately unquoted: both carry several
# whitespace-separated flags that have to reach java as separate arguments.
# shellcheck disable=SC2086
exec "$JAVA_BIN" \
    $APP_OPTS \
    $JAVA_OPTS \
    -cp "$CLASSPATH" \
    com.svenruppert.flow.Application "$@"
