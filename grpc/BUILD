# Copyright 2019 Google Inc. All Rights Reserved.
# Author: dbeaumont@google.com (David Beaumont)
#
# Description:
#   Flogger grpc context implementation.

load("//tools:maven.bzl", "pom_file")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")

package(default_visibility = ["//:internal"])

GRPC_CONTEXT_SRCS = glob(["src/main/java/**/*.java"])

java_library(
    name = "grpc_context",
    srcs = GRPC_CONTEXT_SRCS,
    tags = ["maven_coordinates=com.google.flogger:flogger-grpc-context:${project.version}"],
    deps = [
        "//api",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/grpc:context",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "flogger-grpc-context",
    artifact_name = "Flogger GRPC Context",
    targets = [":grpc_context"],
)

javadoc_library(
    name = "javadoc",
    srcs = GRPC_CONTEXT_SRCS,
    root_packages = ["com.google.common.flogger.grpc"],
    deps = [":grpc_context"],
)

# ---- Unit Tests ----

gen_java_tests(
    name = "grpc_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":grpc_context",
        "//api",
        "//api:testing",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/guava:testlib",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/truth",
    ],
)
