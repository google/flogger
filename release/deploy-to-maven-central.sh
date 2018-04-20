#!/bin/bash

set -eu

if [ $# -lt 2 ]; then
  echo "usage $0 <ssl-key> <version-name> [<param> ...]"
  exit 1;
fi
key=$1
version_name=$2
shift 2

if [[ ! "$version_name" =~ ^2\. ]]; then
  echo 'Version name must begin with "2."'
  exit 2
fi

if [[ "$version_name" =~ " " ]]; then
  echo "Version name must not have any spaces"
  exit 3
fi

#validate key
keystatus=$(gpg --list-keys | grep ${key} | awk '{print $1}')
if [ "${keystatus}" != "pub" ]; then
  echo "Could not find public key with label ${key}"
  echo -n "Available keys from: "
  gpg --list-keys | grep --invert-match '^sub'

  exit 64
fi

bazel test //...

bash $(dirname $0)/execute-deploy.sh \
  "gpg:sign-and-deploy-file" \
  "$version_name" \
  "-DrepositoryId=sonatype-nexus-staging" \
  "-Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/" \
  "-Dgpg.keyname=${key}"

# TODO(b/27549364): add a tag and publish Javadoc to the docs site
