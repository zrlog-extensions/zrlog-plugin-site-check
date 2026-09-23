#!/usr/bin/env bash
basePath=${1}
mkdir -p "${basePath}"
echo "real target folder ${basePath}"

java -version
mvnArgs=()
if [ $# -ge 2 ] && [ -n "${2}" ]; then
  mvnArgs=("${2}")
fi
./mvnw "${mvnArgs[@]}" clean
bash -e bin/build-info.sh "${basePath}"
./mvnw "${mvnArgs[@]}" -PnodeBuild package
./mvnw "${mvnArgs[@]}" -Pnative -DskipNativeTests -Dagent exec:exec@java-agent -U
./mvnw "${mvnArgs[@]}" -Pnative -DskipNativeTests package
binName="site-check"
targetFile=""
sourceFile=""
artifactArchitecture=""
if [ -f "target/${binName}.exe" ];
then
  echo "window"
  sourceFile="target/${binName}.exe"
  artifactArchitecture="Windows-$(uname -m)"
  targetFile="${basePath}/${binName}-${artifactArchitecture}.exe"
elif [[ "$(uname -s)" == "Linux" ]];
then
  echo "Linux"
  sourceFile="target/${binName}"
  artifactArchitecture="$(uname -s)-$(dpkg --print-architecture)"
  targetFile="${basePath}/${binName}-${artifactArchitecture}.bin"
else
  echo "MacOS"
  sourceFile="target/${binName}"
  artifactArchitecture="$(uname -s)-$(uname -m)"
  targetFile="${basePath}/${binName}-${artifactArchitecture}.bin"
fi

mv "${sourceFile}" "${targetFile}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  generatedProperties="target/generated-resources/plugin-info/plugin.properties"
  artifactVersion=$(sed -n 's/^version=//p' "${generatedProperties}" | tr -d '\r')
  if [[ -z "${artifactVersion}" ]]; then
    echo "Unable to resolve ${binName} version from ${generatedProperties}" >&2
    exit 1
  fi
  {
    echo "artifact_file=${targetFile}"
    echo "artifact_name=${binName}"
    echo "artifact_version=${artifactVersion}"
    echo "artifact_architecture=${artifactArchitecture}"
  } >> "${GITHUB_OUTPUT}"
fi
