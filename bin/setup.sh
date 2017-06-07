#!/usr/bin/env bash

# Publishing to gh-pages and sonatype only done from select branches and
# never from pull requests.
function checkPublishable() {
    local publishLocation="${1:-PUBLISH LOCATION NOT SET}"
    if [[ $TRAVIS_PULL_REQUEST != "false" ]]; then
        echo ""
        echo "Pull Requests are not published to $publishLocation"
        exit 0
    elif [[ $TRAVIS_REPO_SLUG != "http4s/http4s" ]]; then
        echo ""
        echo "Builds in Repositories other than http4s/http4s are not published to $publishLocation"
        exit 0
    elif [[ $TRAVIS_BRANCH != "master" || $TRAVIS_BRANCH != "release-"* ]]; then
        echo ""
        echo "As set in bin/setup this is not a publishing branch to $publishLocation"
        exit 0
    else
        echo "This Build Will Be Published To $publishLocation"
    fi
}

# Build git commit message to be used when Travis updates the
# gh-pages branch to publish a new version of the website.
function gh_pages_commit_message() {
    local SHORT_COMMIT=$(printf "%.8s" "$TRAVIS_COMMIT")
    cat <<EOM
updated site

   Job: $TRAVIS_JOB_NUMBER
Commit: $SHORT_COMMIT
Detail: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID
EOM
}

# Add jstack watchdog timer in case #774 shows itself again.
WATCHDOG_PID=""
function find_sbt_pid() {
    pgrep -f sbt-launch
}

function start_watchdog() {
    echo "Watchdog will wake up in $1"
    (sleep "$1" && echo "Watchdog launched jstack." && jstack $(find_sbt_pid)) &
    WATCHDOG_PID=$(pgrep -n sleep)
}

function stop_watchdog() {
    ([ -n "$WATCHDOG_PID" ] && kill "$WATCHDOG_PID") || true
}

mkdir -p $HOME/.sbt/launchers/$SBT_VERSION/
curl -L -o $HOME/.sbt/launchers/$SBT_VERSION/sbt-launch.jar http://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch.jar
mkdir $HOME/bin
PATH=$HOME/bin:$PATH

SBT_EXTRA_OPTS=""
if [[ "$TRAVIS" = "true" ]] && [[ -r "/dev/urandom" ]]; then
    echo
    echo "Using /dev/urandom in Travis CI to avoid hanging on /dev/random"
    echo "when VM or container entropy entropy is low.  Additional detail at"
    echo "https://github.com/http4s/http4s/issues/774#issuecomment-273981456 ."

    SBT_EXTRA_OPTS="$SBT_EXTRA_OPTS -Djava.security.egd=file:/dev/./urandom"
fi

export PATH
export SBT_EXTRA_OPTS