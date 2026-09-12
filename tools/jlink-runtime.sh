#!/bin/sh
#
# Builds a Java runtime image for one unpacked distribution.
#
# Usage:  tools/jlink-runtime.sh <distribution-dir> [java-home]
#
# The image lands in <distribution-dir>/runtime, where bin/start.sh picks it up
# automatically - it prefers $APP_HOME/runtime/bin/java over JAVA_HOME.
#
# Why this is a script and not a Maven profile: a jlink image is built for one
# platform. Running jlink on a developer machine produces an image for that
# machine, which is of no use to a Linux server. The image therefore has to be
# built where it will run, or on a build host of the same platform. This script
# works in both places; the Maven build deliberately does not attempt it.
#
# The Temurin distribution SDKMAN installs ships without a jmods directory, so
# cross-building from another OS would mean fetching a second, full JDK. JEP 493
# makes jlink work from an installed runtime image instead, which is why the
# target can build its own.

set -eu

DIST=${1:?usage: jlink-runtime.sh <distribution-dir> [java-home]}
JAVA_HOME_DIR=${2:-${JAVA_HOME:?set JAVA_HOME or pass it as the second argument}}
JLINK="$JAVA_HOME_DIR/bin/jlink"
JDEPS="$JAVA_HOME_DIR/bin/jdeps"

[ -d "$DIST/lib" ] || { echo "jlink-runtime: no lib/ in $DIST" >&2; exit 1; }
[ -x "$JLINK" ] || { echo "jlink-runtime: no jlink in $JAVA_HOME_DIR" >&2; exit 1; }

# What jdeps can see: everything reachable from the application archive.
FOUND=$("$JDEPS" --print-module-deps --ignore-missing-deps --multi-release 26 \
        --class-path "$DIST/lib/*" "$DIST/app/vaadinapp.jar")

# What jdeps cannot see. Every one of these is loaded reflectively or through
# ServiceLoader, so no static analysis reaches them - each was found by running
# the image and reading the failure:
#
#   jdk.zipfs        ProviderNotFoundException at startup. Jetty opens archives
#                    as a file system.
#   jdk.unsupported  NoClassDefFoundError: sun/misc/Unsafe. EclipseStore.
#   java.net.http    No error at all. The image starts, serves pages and signs
#                    users in; the Have-I-Been-Pwned check breaks the moment
#                    somebody sets a password. jdeps follows what the
#                    application archive reaches, and the HIBP provider is never
#                    called - it is loaded.
#
# The third is the reason this list is written down rather than trusted to a
# tool: a start-up test proves the image starts, not that it is complete.
EXTRA="jdk.zipfs,jdk.unsupported,java.net.http"

MODULES="$FOUND,$EXTRA"
echo "jlink-runtime: jdeps found $(echo "$FOUND" | tr ',' '\n' | wc -l | tr -d ' '), adding 3 it cannot see"

rm -rf "$DIST/runtime"

# --strip-java-debug-attributes, not --strip-debug: the latter shells out to
# objcopy from binutils, which a minimal Debian does not have. This one does the
# same work inside the JVM.
#
# --generate-cds-archive is deliberately absent. Measured on the reference
# server it costs 28 MB of image and returns no start-up time; what it does
# return is about 11 MiB of resident memory. Add it when memory matters more
# than disk.
"$JLINK" --add-modules "$MODULES" --output "$DIST/runtime" \
    --strip-java-debug-attributes --no-header-files --no-man-pages --compress zip-6

echo "jlink-runtime: $(du -sh "$DIST/runtime" | cut -f1), $("$DIST/runtime/bin/java" --list-modules | wc -l | tr -d ' ') modules"
