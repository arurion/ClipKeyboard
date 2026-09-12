#!/bin/sh
# Minimal wrapper launcher stub. Run `gradle wrapper` locally (or in CI) once to
# generate the real gradle-wrapper.jar + properties before relying on this script,
# or simply open the project in Android Studio which will offer to do it for you.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec gradle "$@" -p "$DIR"
