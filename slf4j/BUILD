# Copyright 2018 Google Inc. All Rights Reserved.
#
# Description:
#   Flogger slf4j backend (google.github.io/flogger).

package(default_visibility = ["//:internal"])

SLF4J_BACKEND_SRCS = glob(["src/main/java/**/*.java"])

java_library(
    name = "slf4j_backend",
    srcs = SLF4J_BACKEND_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    tags = ["maven_coordinates=com.google.flogger:flogger-slf4j-backend:${project.version}"],
    deps = [
        "//api",
        "//api:system_backend",
        "@google_bazel_common//third_party/java/slf4j_api",
    ],
)

load("//tools:maven.bzl", "pom_file")

pom_file(
    name = "pom",
    artifact_id = "flogger-slf4j-backend",
    artifact_name = "Flogger SLF4J Backend",
    targets = [":slf4j_backend"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "javadoc",
    srcs = SLF4J_BACKEND_SRCS,
    root_packages = ["com.google.common.flogger.backend.slf4j"],
    deps = [":slf4j_backend"],
)

# ---- Unit Tests ----

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")

gen_java_tests(
    name = "slf4j_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":slf4j_backend",
        "//api",
        "//api:testing",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/slf4j_api",
    ],
)
