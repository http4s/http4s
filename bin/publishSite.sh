#!/usr/bin/env bash
set -e

. $TRAVIS_BUILD_DIR/bin/setup.sh

# Install hugo static site generator from GitHub releases page.
curl -s -L "https://github.com/spf13/hugo/releases/download/v${HUGO_VERSION}/hugo_${HUGO_VERSION}_Linux-64bit.tar.gz" | tar xzf -
mv "./hugo" "$HOME/bin/hugo"

# Disable Making Sure It's the Real Github
echo -e "Host github.com\n\tStrictHostKeyChecking no\n" >> ~/.ssh/config

# Record minimal build information via the Git user ident
git config --global user.name "Travis CI";
git config --global user.email "travis-ci@http4s.org";
SBT_GHPAGES_COMMIT_MESSAGE=$(gh_pages_commit_message)
export SBT_GHPAGES_COMMIT_MESSAGE

# Add secret deploy key to ssh-agent for deploy
eval "$(ssh-agent -s)";
openssl aes-256-cbc -d -K $encrypted_8735ae5b3321_key -iv $encrypted_8735ae5b3321_iv -in project/travis-deploy-key.enc | ssh-add -;


sbt ";makeSite"

checkPublishable "ghPages"

sbt ";ghpagesPushSite"
