#!/bin/sh
set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

bazel_output_file() {
  library=$1
  library_output=bazel-bin/$library
  if [[ ! -e $library_output ]]; then
     library_output=bazel-genfiles/$library
  fi
  if [[ ! -e $library_output ]]; then
    echo "Could not find bazel output file for $library"
    exit 1
  fi
  echo -n $library_output
}

deploy_library() {
  library=$1
  srcjar=$2
  javadoc=$3
  pomfile=$4
  bazel build --define=pom_version="$VERSION_NAME" \
    $library $srcjar $javadoc $pomfile

  mvn $MVN_GOAL \
    -Dfile=$(bazel_output_file $library) \
    -Djavadoc=$(bazel_output_file $javadoc) \
    -DpomFile=$(bazel_output_file $pomfile) \
    -Dsources=$(bazel_output_file $srcjar) \
    "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

deploy_library \
  api/merged_api.jar \
  api/merged_api_src.jar \
  api/api_javadoc.jar \
  api/api_pom.xml

deploy_library \
  api/libsystem_backend.jar \
  api/libsystem_backend-src.jar \
  api/system_backend_javadoc.jar \
  api/system_backend_pom.xml

deploy_library \
  api/libtesting.jar \
  api/libtesting-src.jar \
  api/testing_javadoc.jar \
  api/testing_pom.xml

deploy_library \
  google/libflogger.jar \
  google/libflogger-src.jar \
  google/flogger_javadoc.jar \
  google/google_logger_pom.xml

deploy_library \
  log4j/liblog4j_backend.jar \
  log4j/liblog4j_backend-src.jar \
  log4j/javadoc.jar \
  log4j/pom.xml
