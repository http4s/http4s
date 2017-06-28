#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

checkPublishable "Sonatype/Maven Central"

sbt "publish"

