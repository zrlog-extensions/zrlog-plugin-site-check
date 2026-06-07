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
if [ -f "target/${binName}.exe" ];
then
  echo "window"
  sourceFile="target/${binName}.exe"
  targetFile="${basePath}/${binName}-Windows-$(uname -m).exe"
  choco install upx
  mv ${sourceFile} ${targetFile}
  upx --best ${targetFile}
  exit 0;
fi
if [[ "$(uname -s)" == "Linux" ]];
then
  echo "Linux"
  sourceFile="target/${binName}"
  targetFile="${basePath}/${binName}-$(uname -s)-$(dpkg --print-architecture).bin"
  sudo apt install upx -y
  mv ${sourceFile} ${targetFile}
  upx --best ${targetFile}
else
  echo "MacOS"
  sourceFile="target/${binName}"
  targetFile="${basePath}/${binName}-$(uname -s)-$(uname -m).bin"
  brew install upx
  mv ${sourceFile} ${targetFile}
#  upx --best ${targetFile}
fi
