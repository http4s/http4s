#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

start_watchdog 13m
sbt $SBT_EXTRA_OPTS 'set scalazVersion in ThisBuild := System.getenv("SCALAZ_VERSION")' ++$TRAVIS_SCALA_VERSION ";coverage; clean; test; coverageReport; coverageOff"
stop_watchdog

echo "Uploading codecov"
bash <(curl -s https://codecov.io/bash)