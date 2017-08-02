#!/usr/bin/env bash
set -e

. $TRAVIS_BUILD_DIR/bin/setup.sh

sbt "test"
