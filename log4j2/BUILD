# Copyright (C) 2019 The Flogger Authors.
#
# Description:
#   Flogger log4j2 backend (google.github.io/flogger).

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools:maven.bzl", "pom_file")

LOG4J2_BACKEND_SRCS = glob(["src/main/java/**/*.java"])

LOG4J2_BACKEND_RESOURCES = glob(["src/main/resources/**"])

java_library(
    name = "log4j2_backend",
    srcs = LOG4J2_BACKEND_SRCS,
    javacopts = ["-source 8 -target 8"],
    resources = LOG4J2_BACKEND_RESOURCES,
    tags = ["maven_coordinates=com.google.flogger:flogger-log4j2-backend:${project.version}"],
    deps = [
        "//api",
        "//api:system_backend",
        # TODO(cpovirk): Why do we use a mix of google_bazel_common and "direct" deps?
        "@com_google_errorprone_error_prone_annotations",
        "@google_bazel_common//third_party/java/log4j2",
        "@google_bazel_common//third_party/java/jspecify_annotations",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "flogger-log4j2-backend",
    artifact_name = "Flogger Log4j2 Backend",
    targets = [":log4j2_backend"],
)

javadoc_library(
    name = "javadoc",
    srcs = LOG4J2_BACKEND_SRCS,
    root_packages = ["com.google.common.flogger.backend.log4j2"],
    deps = [":log4j2_backend"],
)

# ---- Unit Tests ----

gen_java_tests(
    name = "log4j2_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":log4j2_backend",
        "//api",
        "//api:system_backend",
        "//api:testing",
        "//google:flogger",
        "//grpc:grpc_context",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/jspecify_annotations",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/log4j2",
        "@google_bazel_common//third_party/java/truth",
    ],
)
