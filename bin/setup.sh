#!/usr/bin/env bash
set -e

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
    elif [[ $TRAVIS_BRANCH != "master" && $TRAVIS_BRANCH != "release-"* ]]; then
        echo ""
        echo "As set in bin/setup this is not a publishing branch to $publishLocation"
        exit 0
    else
        echo "This Build Will Be Published to $publishLocation"
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

mkdir -p $HOME/bin
PATH=$HOME/bin:$PATH

SBT_OPTS+=" 'set scalazVersion in ThisBuild := System.getenv("SCALAZ_VERSION")'"
SBT_OPTS+=" ++$TRAVIS_SCALA_VERSION"

export PATH
export SBT_OPTS
