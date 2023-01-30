#!/bin/bash -x

set -euo pipefail

VERSION=$1
echo "You want me to build and distribute ${VERSION} ?"

export MAVEN_HOME="$HOME/.sdkman/candidates/maven/current"
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

export JENKINS_HOME=/tmp/jenkins-home
export RELEASE_TOOLS_MAVEN_REPOSITORY=$(pwd)/maven-repository
export LOGS_DIR=$(pwd)/logs
export SETTINGS_XML=$(pwd)/ci/settings.xml

mkdir -p ${RELEASE_TOOLS_MAVEN_REPOSITORY}
mkdir -p ${LOGS_DIR}

export GNUPGHOME=~/.gnupg/
mkdir -p ${GNUPGHOME}
chmod 700 ${GNUPGHOME}

if test -f application-local.properties; then
    echo "You are running from dev environment! Using application-local.properties."

    GIT_BRANCH=""

    function spring-data-release-shell {
        java \
            -jar target/spring-data-release-cli.jar \
            --cmdfile target/build-and-distribute.shell
    }
else
    echo "You are running inside Jenkins! Using parameters fed from the agent."

    echo "${GIT_SIGNING_KEY_PASSWORD}" | /usr/bin/gpg1 --batch --yes --passphrase-fd 0 --import "${GIT_SIGNING_KEY}"
    echo "${MAVEN_SIGNING_KEY_PASSWORD}" | /usr/bin/gpg1 --batch --yes --passphrase-fd 0 --import "${MAVEN_SIGNING_KEY}"
    /usr/bin/gpg1 -k

    function spring-data-release-shell {
        java \
            -Dspring.profiles.active=jenkins \
            -jar target/spring-data-release-cli.jar \
            --cmdfile target/build-and-distribute.shell
    }
fi

echo "About to push and distribute ${VERSION}."

sed "s|\${VERSION}|${VERSION}|g" < ci/build-and-distribute.template > target/build-and-distribute.shell

spring-data-release-shell
