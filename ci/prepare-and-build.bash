#!/bin/bash -x

set -euo pipefail

VERSION=$1
echo "You want me to build and deploy ${VERSION} ??"

export MAVEN_HOME="$HOME/.sdkman/candidates/maven/current"
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

export JENKINS_HOME=/tmp/jenkins-home
export RELEASE_TOOLS_CACHE=${JENKINS_HOME}/.m2/spring-data-release-tools
export LOGS_DIR=$(pwd)/logs
export SETTINGS_XML=${JENKINS_HOME}/settings.xml

mkdir -p ${RELEASE_TOOLS_CACHE}
mkdir -p ${LOGS_DIR}

export GNUPGHOME=~/.gnupg/
mkdir -p ${GNUPGHOME}
chmod 700 ${GNUPGHOME}

cp ci/settings.xml ${JENKINS_HOME}

if test -f application-local.properties; then
    echo "You are running from dev environment! Using application-local.properties."

    GIT_BRANCH=""

    function spring-data-release-shell {
        java \
            -jar target/spring-data-release-cli.jar \
            --cmdfile target/prepare-and-build.shell
    }
else
    echo "You are running inside Jenkins! Using parameters fed from the agent."

    mkdir -p ~/.gnupg
    echo "${GIT_SIGNING_KEY_PASSWORD}" | /usr/bin/gpg --batch --yes --passphrase-fd 0 --import "${GIT_SIGNING_KEY}"
    echo "${MAVEN_SIGNING_KEY_PASSWORD}" | /usr/bin/gpg --batch --yes --passphrase-fd 0 --import "${MAVEN_SIGNING_KEY}"
    /usr/bin/gpg -k

    function spring-data-release-shell {
        java \
            -Dspring.profiles.active=jenkins \
            -jar target/spring-data-release-cli.jar \
            --cmdfile target/prepare-and-build.shell
    }
fi

echo "About to prepare and build ${VERSION}."

sed "s|\${VERSION}|${VERSION}|g" < ci/prepare-and-build.template > target/prepare-and-build.shell

spring-data-release-shell
