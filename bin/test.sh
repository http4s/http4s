#!/usr/bin/env bash

. $TRAVIS_BUILD_DIR/bin/setup.sh

sbt "test"
