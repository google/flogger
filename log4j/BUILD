# Copyright 2018 Google Inc. All Rights Reserved.
# Author: ekempin@google.com (Edwin Kempin)
#
# Description:
#   Flogger log4j backend (https://google.github.io/flogger).

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools:maven.bzl", "pom_file")

LOG4J_BACKEND_SRCS = glob(["src/main/java/**/*.java"])

LOG4J_BACKEND_RESOURCES = glob(["src/main/resources/**"])

java_library(
    name = "log4j_backend",
    srcs = LOG4J_BACKEND_SRCS,
    javacopts = ["-source 8 -target 8"],
    resources = LOG4J_BACKEND_RESOURCES,
    tags = ["maven_coordinates=com.google.flogger:flogger-log4j-backend:${project.version}"],
    deps = [
        "//api",
        "//api:system_backend",
        "@google_bazel_common//third_party/java/log4j",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "flogger-log4j-backend",
    artifact_name = "Flogger Log4j Backend",
    targets = [":log4j_backend"],
)

javadoc_library(
    name = "javadoc",
    srcs = LOG4J_BACKEND_SRCS,
    root_packages = ["com.google.common.flogger.backend.log4j"],
    deps = [":log4j_backend"],
)

# ---- Unit Tests ----

gen_java_tests(
    name = "log4j_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":log4j_backend",
        "//api",
        "//api:system_backend",
        "//api:testing",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/log4j",
        "@google_bazel_common//third_party/java/truth",
    ],
)
