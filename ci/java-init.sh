#!/bin/bash

####################################################################
# Utility to install Java and Maven into the build container image #
####################################################################

source $HOME/.sdkman/bin/sdkman-init.sh
JAVA_TOOLS_PROPERTIES=java-tools.properties

if [ ! -f ${JAVA_TOOLS_PROPERTIES} ]
then
    echo "File does not exist: ${JAVA_TOOLS_PROPERTIES}"
    exit 1
fi

while IFS='=' read -r key value
do
    key=$(echo $key | tr '.' '_')
    eval ${key}=\${value}
done < "${JAVA_TOOLS_PROPERTIES}"

IFS=', ' read -r -a jdk_versions <<< "$jdks"

for to_install in "${jdk_versions[@]}"
do
  dist="${to_install}-tem"
  echo "Installing JDK ${dist}"
  yes | sdk install java "${dist}"
done

echo "Installing Maven ${maven}"
yes | sdk install maven ${maven}
