#!/usr/bin/env bash
set -e

. $TRAVIS_BUILD_DIR/bin/setup.sh

sbt ";scalafmt::test ;test:scalafmt::test ;mimaReportBinaryIssues ;coverage ;clean ;test ;coverageReport ;coverageOff"
exitCode=$?

echo "Uploading codecov"
bash <(curl -s https://codecov.io/bash)

exit ${exitCode}

