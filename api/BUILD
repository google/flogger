# Copyright 2012 Google Inc. All Rights Reserved.
# Author: cgruber@google.com (Christian Edward Gruber)
#
# Description:
#   Flogger core libraries (google.github.io/flogger).

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")
load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")
load("//tools:maven.bzl", "pom_file")

package(default_visibility = ["//:internal"])

CHECKS_SRCS = ["src/main/java/com/google/common/flogger/util/Checks.java"]

METHOD_CALLER_SRCS = ["src/main/java/com/google/common/flogger/util/StaticMethodCaller.java"]

TAGS_SRCS = [
    "src/main/java/com/google/common/flogger/backend/KeyValueFormatter.java",
    "src/main/java/com/google/common/flogger/backend/KeyValueHandler.java",
    "src/main/java/com/google/common/flogger/backend/Tags.java",
]

CONTEXT_SRCS = glob(["src/main/java/com/google/common/flogger/context/*.java"])

LAZY_ARGS_SRCS = [
    "src/main/java/com/google/common/flogger/LazyArg.java",
    "src/main/java/com/google/common/flogger/LazyArgs.java",
]

STACK_SRCS = [
    "src/main/java/com/google/common/flogger/LogSiteStackTrace.java",
    "src/main/java/com/google/common/flogger/StackSize.java",
]

LOG_SITE_SRCS = [
    "src/main/java/com/google/common/flogger/LogSite.java",
    "src/main/java/com/google/common/flogger/LogSiteKey.java",
]

LOG_SITE_HELPER_SRCS = ["src/main/java/com/google/common/flogger/LogSites.java"]

SYSTEM_BACKEND_SRCS = glob(["src/main/java/com/google/common/flogger/backend/system/**/*.java"])

# The core 'frontend' Flogger library.
#
java_library(
    name = "api",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = CHECKS_SRCS +
                  METHOD_CALLER_SRCS +
                  TAGS_SRCS +
                  CONTEXT_SRCS +
                  LAZY_ARGS_SRCS +
                  LOG_SITE_SRCS +
                  LOG_SITE_HELPER_SRCS +
                  STACK_SRCS +
                  SYSTEM_BACKEND_SRCS,
    ),
    javacopts = ["-source 1.6 -target 1.6"],
    tags = ["maven_coordinates=com.google.flogger:flogger:${project.version}"],
    visibility = ["//visibility:public"],
    exports = [
        ":lazy_args",
        ":log_site",
        ":stack",
        ":tags",
    ],
    deps = [
        ":checks",
        ":lazy_args",
        ":log_site",
        ":platform_provider",
        ":stack",
        ":tags",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

# This pom file and the two jarjar libraries below must contain the targets/jars for anything
# that's released in open-source. If new targets are refactored out in the main codebase, they
# must be included here.
pom_file(
    name = "api_pom",
    artifact_id = "flogger",
    artifact_name = "Flogger",
    targets = [
        ":api",
        ":checks",
        ":context",
        ":lazy_args",
        ":log_site",
        ":log_sites",
        ":platform_provider",
        ":reflection_utils",
        ":stack",
        ":tags",
    ],
)

jarjar_library(
    name = "merged_api",
    jars = [
        ":api",
        ":checks",
        ":context",
        ":lazy_args",
        ":log_site",
        ":log_sites",
        ":platform_provider",
        ":reflection_utils",
        ":stack",
        ":tags",
    ],
)

jarjar_library(
    name = "merged_api_src",
    jars = [
        ":libapi-src.jar",
        ":libchecks-src.jar",
        ":libcontext-src.jar",
        ":liblazy_args-src.jar",
        ":liblog_site-src.jar",
        ":liblog_sites-src.jar",
        ":libreflection_utils-src.jar",
        ":libstack-src.jar",
        ":libtags-src.jar",
        # TODO(ronshapiro): consider generating PlatformProvider as a source file so it can be
        # included here?
    ],
)

javadoc_library(
    name = "api_javadoc",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = SYSTEM_BACKEND_SRCS,
    ),
    root_packages = ["com.google.common.flogger"],
    deps = [":api"],
)

java_library(
    name = "checks",
    srcs = CHECKS_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
)

java_library(
    name = "reflection_utils",
    srcs = METHOD_CALLER_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = [
        ":checks",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
    ],
)

# A separate library for exposing just the 'Tags' class for use with the
# TraceLoggingContext.
#
java_library(
    name = "tags",
    srcs = TAGS_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = [
        ":checks",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

java_library(
    name = "context",
    srcs = CONTEXT_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = [
        ":checks",
        ":reflection_utils",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

# A separate library for exposing just the 'LazyArgs' classes, since these are
# support classes which can be imported and used freely in general code.
java_library(
    name = "lazy_args",
    srcs = LAZY_ARGS_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = [
        ":checks",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
    ],
)

# A separate library for exposing just the 'Stack' classes. This is exported as
# part of the core library.
java_library(
    name = "stack",
    srcs = STACK_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = ["@google_bazel_common//third_party/java/checker_framework_annotations"],
)

# A separate library for exposing just the 'LogSite' class. This is exported as
# part of the core library.
java_library(
    name = "log_site",
    srcs = LOG_SITE_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    deps = [
        ":checks",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

# A separate helper for manual log site injection. To avoid circular
# dependencies in the build graph, this is a separate target from the
# LogSite class, since the log_site target cannot depend on platform
# classes (but this target does).
java_library(
    name = "log_sites",
    srcs = LOG_SITE_HELPER_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    exports = [":log_site"],
    deps = [":api"],
)

# Core library to implement a JDK compatible backend for Flogger.
#
java_library(
    name = "system_backend",
    srcs = SYSTEM_BACKEND_SRCS,
    javacopts = ["-source 1.6 -target 1.6"],
    tags = ["maven_coordinates=com.google.flogger:flogger-system-backend:${project.version}"],
    visibility = ["//visibility:public"],
    deps = [
        ":api",
        ":reflection_utils",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
    ],
)

pom_file(
    name = "system_backend_pom",
    artifact_id = "flogger-system-backend",
    artifact_name = "Flogger System Backend",
    targets = [":system_backend"],
)

javadoc_library(
    name = "system_backend_javadoc",
    srcs = SYSTEM_BACKEND_SRCS,
    root_packages = ["com.google.common.flogger.backend.system"],
    deps = [":system_backend"],
)

# Utilities to help test Fluent Loggers. This target includes simple fakes for
# creating injectable test data as well as Truth subjects for asserting things
# about log statements. This is exported via @google_bazel_common//third_party/java/flogger:testing
# but should only be needed by anyone writing their own fluent logger.

TESTING_SRCS = glob(["src/test/java/com/google/common/flogger/testing/*.java"])

java_library(
    name = "testing",
    testonly = 1,
    srcs = TESTING_SRCS,
    tags = ["maven_coordinates=com.google.flogger:flogger-testing:${project.version}"],
    visibility = ["//visibility:public"],
    deps = [
        ":api",
        ":checks",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/truth",
    ],
)

pom_file(
    name = "testing_pom",
    testonly = 1,
    artifact_id = "flogger-testing",
    artifact_name = "Flogger Testing",
    targets = [":testing"],
)

javadoc_library(
    name = "testing_javadoc",
    testonly = 1,
    srcs = TESTING_SRCS,
    root_packages = ["com.google.common.flogger.testing"],
    deps = [":testing"],
)

java_binary(
    name = "platform_provider_generator",
    srcs = ["platformprovider/main/java/com/google/common/flogger/backend/PlatformProviderGenerator.java"],
    main_class = "com.google.common.flogger.backend.PlatformProviderGenerator",
    deps = ["@google_bazel_common//third_party/java/asm"],
)

genrule(
    name = "gen_platform_provider",
    outs = ["platform_provider.jar"],
    cmd = "$(location :platform_provider_generator) $@",
    tools = [
        ":platform_provider_generator",
    ],
)

java_import(
    name = "platform_provider",
    jars = ["platform_provider.jar"],
    proguard_specs = ["proguard.cfg"],
)

filegroup(
    name = "proguard_cfg",
    srcs = ["proguard.cfg"],
    visibility = [
        "//visibility:public",
    ],
)

filegroup(
    name = "javadoc_sources",
    testonly = 1,
    srcs = glob(["src/main/java/**"]) + TESTING_SRCS,
)

# ---- Unit Tests ----

gen_java_tests(
    name = "api_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = [
            "src/test/java/com/google/common/flogger/testing/**/*.java",
        ],
    ),
    deps = [
        ":api",
        ":context",
        ":log_sites",
        ":system_backend",
        ":testing",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/guava:testlib",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/truth",
    ],
)
