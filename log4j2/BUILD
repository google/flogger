# Copyright (C) 2019 The Flogger Authors.
#
# Description:
#   Flogger log4j2 backend (google.github.io/flogger).

package(default_visibility = ["//:internal"])

LOG4J2_BACKEND_SRCS = glob(["src/main/java/**/*.java"])

java_library(
    name = "log4j2_backend",
    srcs = LOG4J2_BACKEND_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    tags = ["maven_coordinates=com.google.flogger:flogger-log4j2-backend:${project.version}"],
    deps = [
        "//api",
        "//api:system_backend",
        "@google_bazel_common//third_party/java/jsr305_annotations",
        "@log4j_api//jar",
        "@log4j_core//jar",
    ],
)

load("//tools:maven.bzl", "pom_file")

pom_file(
    name = "pom",
    artifact_id = "flogger-log4j2-backend",
    artifact_name = "Flogger Log4j2 Backend",
    targets = [":log4j2_backend"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "javadoc",
    srcs = LOG4J2_BACKEND_SRCS,
    root_packages = ["com.google.common.flogger.backend.log4j2"],
    deps = [":log4j2_backend"],
)

# ---- Unit Tests ----

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")

gen_java_tests(
    name = "log4j2_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":log4j2_backend",
        "//api",
        "//api:testing",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/guava:testlib",
        "@google_bazel_common//third_party/java/jsr305_annotations",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/truth",
        "@log4j_api//jar",
        "@log4j_core//jar",
    ],
)
