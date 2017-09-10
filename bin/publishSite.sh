#!/usr/bin/env bash
set -e
shopt -s nullglob

REPO=https://github.com/$TRAVIS_REPO_SLUG.git
REPO_SSH=git@github.com:$TRAVIS_REPO_SLUG
VERSION=v0.16

. $TRAVIS_BUILD_DIR/bin/setup.sh

# Install hugo static site generator from GitHub releases page.
curl -s -L "https://github.com/spf13/hugo/releases/download/v${HUGO_VERSION}/hugo_${HUGO_VERSION}_Linux-64bit.tar.gz" | tar xzf -
mv "./hugo" "$HOME/bin/hugo"

# Disable Making Sure It's the Real Github
echo -e "Host github.com\n\tStrictHostKeyChecking no\n" >> ~/.ssh/config

# Record minimal build information via the Git user ident
git config user.name "Travis CI";
git config user.email "travis-ci@http4s.org";

sbt ";makeSite"

checkPublishable "ghPages"

# Add secret deploy key to ssh-agent for deploy
eval "$(ssh-agent -s)";
openssl aes-256-cbc -d -K $encrypted_8735ae5b3321_key -iv $encrypted_8735ae5b3321_iv -in project/travis-deploy-key.enc | ssh-add -;

DIR=$(dirname "$0")

cd $DIR/../docs/target

echo "Cloning http4s.org"
git clone git@github.com:http4s/http4s.org.git http4s.org
cd http4s.org
git checkout gh-pages

echo "Cleaning old $VERSION in http4s.org repo"
rm -rf $VERSION

echo "Moving old site into http4s.org repo"
cp -a ../site/$VERSION .

echo "Updating gh-pages branch"
git add --all && git commit -m "$(gh_pages_commit_message)"

echo "Pushing to origin/gh-pages"
echo git push origin gh-pages
