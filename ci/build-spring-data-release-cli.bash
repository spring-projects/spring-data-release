#!/bin/bash -x

set -euo pipefail

export MAVEN_HOME="$HOME/.sdkman/candidates/maven/current"
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

export JENKINS_HOME=/tmp/jenkins-home
export RELEASE_TOOLS_MAVEN_REPOSITORY=$(pwd)/maven-repository
export SETTINGS_XML=$(pwd)/ci/settings.xml

mkdir -p ${RELEASE_TOOLS_MAVEN_REPOSITORY}

mvn -Dmaven.repo.local=${RELEASE_TOOLS_MAVEN_REPOSITORY} -s ${SETTINGS_XML} clean package -B -DskipTests
