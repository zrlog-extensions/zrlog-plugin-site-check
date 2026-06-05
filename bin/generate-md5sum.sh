#!/usr/bin/env bash
set -euo pipefail

artifactDir=${1:?artifact directory is required}

if [ ! -d "${artifactDir}" ]; then
  echo "artifact directory not found: ${artifactDir}" >&2
  exit 1
fi

find "${artifactDir}" -type f ! -name "*.md5" -print0 | while IFS= read -r -d '' file; do
  if command -v md5sum >/dev/null 2>&1; then
    checksum=$(md5sum "${file}" | awk '{print $1}')
  elif command -v md5 >/dev/null 2>&1; then
    checksum=$(md5 -r "${file}" | awk '{print $1}')
  else
    echo "md5sum or md5 command is required" >&2
    exit 1
  fi

  printf '%s  %s\n' "${checksum}" "$(basename "${file}")" > "${file}.md5"
done
