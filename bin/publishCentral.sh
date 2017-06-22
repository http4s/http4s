#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

checkPublishable "Sonatype/Maven Central"

# I think we should be able to do this correctly here
sbt $SBT_EXTRA_OPTS 'set scalazVersion in ThisBuild := System.getenv("SCALAZ_VERSION")' ++$TRAVIS_SCALA_VERSION "publish"