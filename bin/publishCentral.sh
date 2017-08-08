#!/usr/bin/env bash
set -e

. $TRAVIS_BUILD_DIR/bin/setup.sh

checkPublishable "Sonatype/Maven Central"

sbt "publish"

