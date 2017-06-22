#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

start_watchdog 13m
sbt $SBT_EXTRA_OPTS ++$TRAVIS_SCALA_VERSION "test"
stop_watchdog