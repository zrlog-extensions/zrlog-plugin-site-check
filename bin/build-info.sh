#!/usr/bin/env bash
set -euo pipefail

artifactDir="${1:-}"
sourceProperties="src/main/resources/plugin.properties"
generatedDir="target/generated-resources/plugin-info"
generatedProperties="${generatedDir}/plugin.properties"

if [ ! -f "${sourceProperties}" ]; then
  echo "plugin properties not found: ${sourceProperties}" >&2
  exit 1
fi

projectVersion=$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' pom.xml | head -n 1)
projectVersion="${projectVersion%-SNAPSHOT}"
if [ -z "${projectVersion}" ]; then
  echo "project version is required" >&2
  exit 1
fi

majorMinor=$(printf '%s\n' "${PLUGIN_BASE_VERSION:-${projectVersion}}" | awk -F. 'NF >= 2 {print $1 "." $2; exit}')
if [ -z "${majorMinor}" ]; then
  echo "major.minor version is required" >&2
  exit 1
fi

buildNumber="${BUILD_NUMBER:-}"
if [ -z "${buildNumber}" ]; then
  buildNumber=$(git rev-list --all --count)
fi
if [ -z "${buildNumber}" ]; then
  echo "build number is required" >&2
  exit 1
fi

pluginVersion="${majorMinor}.${buildNumber}"
mkdir -p "${generatedDir}"
awk -v pluginVersion="${pluginVersion}" '
  BEGIN { replaced = 0 }
  /^version=/ {
    print "version=" pluginVersion
    replaced = 1
    next
  }
  { print }
  END {
    if (replaced == 0) {
      print "version=" pluginVersion
    }
  }
' "${sourceProperties}" > "${generatedProperties}"

shortName=$(sed -n 's/^shortName=//p' "${generatedProperties}" | head -n 1 | tr -d '\r')
if [ -z "${shortName}" ]; then
  echo "shortName is required in ${generatedProperties}" >&2
  exit 1
fi

if [ -n "${artifactDir}" ]; then
  mkdir -p "${artifactDir}"
  cp "${generatedProperties}" "${artifactDir}/${shortName}.properties"
fi

echo "pluginVersion=${pluginVersion}"
