#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

sbt $SBT_EXTRA_OPTS 'set scalazVersion in ThisBuild := System.getenv("SCALAZ_VERSION")' ++$TRAVIS_SCALA_VERSION "mimaReportBinaryIssues"