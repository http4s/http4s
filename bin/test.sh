#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

# Add jstack watchdog timer in case #774 shows itself again.
WATCHDOG_PID=""
function find_sbt_pid() {
    pgrep -f sbt-launch
}

function start_watchdog() {
    echo "Watchdog will wake up in $1"
    (sleep "$1" && echo "Watchdog launched jstack." && jstack $(find_sbt_pid)) &
    WATCHDOG_PID=$(pgrep -n sleep)
}

function stop_watchdog() {
    ([ -n "$WATCHDOG_PID" ] && kill "$WATCHDOG_PID") || true
}

start_watchdog 13m
sbt $SBT_EXTRA_OPTS 'set scalazVersion in ThisBuild := System.getenv("SCALAZ_VERSION")' ++$TRAVIS_SCALA_VERSION "test"
stop_watchdog