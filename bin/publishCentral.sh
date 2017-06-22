#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

checkPublishable "Sonatype/Maven Central"

# I think we should be able to do this correctly here
sbt $SBT_EXTRA_OPTS ++$TRAVIS_SCALA_VERSION "publish"