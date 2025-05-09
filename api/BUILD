# Copyright 2012 Google Inc. All Rights Reserved.
# Author: cgruber@google.com (Christian Edward Gruber)
#
# Description:
#   Flogger core libraries (https://google.github.io/flogger).

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")
load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")
load("//tools:maven.bzl", "pom_file")
load(":build_defs.bzl", "genjar")

SYSTEM_BACKEND_SRCS = glob(["src/main/java/com/google/common/flogger/backend/system/**/*.java"])

STACK_GETTER_STACK_WALKER_IMPL_SRCS = [
    "src/main/java/com/google/common/flogger/util/StackWalkerStackGetter.java",
]

STACK_GETTER_JAVA_LANG_ACCESS_IMPL_SRCS = [
    "src/main/java/com/google/common/flogger/util/JavaLangAccessStackGetter.java",
]

STACK_GETTER_COMMON_SRCS = [
    "src/main/java/com/google/common/flogger/util/StackGetter.java",
    "src/main/java/com/google/common/flogger/util/ThrowableStackGetter.java",
]

CHECKS = [
    "src/main/java/com/google/common/flogger/util/Checks.java",
]

# The core 'frontend' Flogger library.
java_library(
    name = "api",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = SYSTEM_BACKEND_SRCS +
                  STACK_GETTER_STACK_WALKER_IMPL_SRCS +
                  STACK_GETTER_JAVA_LANG_ACCESS_IMPL_SRCS +
                  STACK_GETTER_COMMON_SRCS + CHECKS,
    ),
    tags = ["maven_coordinates=com.google.flogger:flogger:${project.version}"],
    visibility = ["//visibility:public"],
    exports = [":checks"],
    deps = [
        ":checks",
        ":platform_provider",
        ":stack_getter_common",
        ":stack_getter_impl",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/jspecify_annotations",
    ],
)

java_import(
    name = "stack_getter_impl",
    jars = [
        "stack_getter_java_lang_access_impl.jar",
        "stack_getter_stack_walker_impl.jar",
    ],
    deps = [":stack_getter_common"],
)

genjar(
    name = "stack_getter_java_lang_access_impl",
    srcs = STACK_GETTER_JAVA_LANG_ACCESS_IMPL_SRCS,
    toolchains = ["@rules_java//toolchains:jdk_8"],
    deps = [
        ":checks",
        ":stack_getter_common",
    ],
)

genjar(
    name = "stack_getter_stack_walker_impl",
    srcs = STACK_GETTER_STACK_WALKER_IMPL_SRCS,
    toolchains = ["@rules_java//toolchains:remote_jdk11"],
    deps = [
        ":checks",
        ":stack_getter_common",
        "@google_bazel_common//third_party/java/jspecify_annotations",
    ],
)

java_library(
    name = "stack_getter_common",
    srcs = STACK_GETTER_COMMON_SRCS,
    deps = [
        ":checks",
        "@google_bazel_common//third_party/java/jspecify_annotations",
    ],
)

java_library(
    name = "checks",
    srcs = CHECKS,
    deps = [
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

pom_file(
    name = "api_pom",
    artifact_id = "flogger",
    artifact_name = "Flogger",
    targets = [":api"],
)

jarjar_library(
    name = "merged_api",
    jars = [
        ":api",
        ":checks",
        ":platform_provider",
        ":stack_getter_common",
        ":stack_getter_impl",
    ],
)

javadoc_library(
    name = "api_javadoc",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = SYSTEM_BACKEND_SRCS +
                  STACK_GETTER_STACK_WALKER_IMPL_SRCS +
                  STACK_GETTER_JAVA_LANG_ACCESS_IMPL_SRCS,
    ),
    root_packages = ["com.google.common.flogger"],
    deps = [":api"],
)

# Core library to implement a JDK compatible backend for Flogger.
#
java_library(
    name = "system_backend",
    srcs = SYSTEM_BACKEND_SRCS,
    tags = ["maven_coordinates=com.google.flogger:flogger-system-backend:${project.version}"],
    visibility = ["//visibility:public"],
    deps = [
        ":api",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/jspecify_annotations",
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
# about log statements. This should only be needed by anyone writing their own
# fluent logger.

TESTING_SRCS = glob(["src/test/java/com/google/common/flogger/testing/**/*.java"])

java_library(
    name = "testing",
    testonly = 1,
    srcs = TESTING_SRCS,
    tags = ["maven_coordinates=com.google.flogger:flogger-testing:${project.version}"],
    visibility = ["//visibility:public"],
    deps = [
        ":api",
        ":checks",
        ":system_backend",  # For AbstractLogRecord needed for FakeLogRecord
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/jspecify_annotations",
        "@google_bazel_common//third_party/java/junit",
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

# TODO(ronshapiro): consider generating PlatformProvider as a source file and
# including it in the public Maven-published source jar?

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
)

filegroup(
    name = "javadoc_sources",
    testonly = 1,
    srcs = glob(
        ["src/main/java/**"],
        exclude = STACK_GETTER_STACK_WALKER_IMPL_SRCS +
                  STACK_GETTER_JAVA_LANG_ACCESS_IMPL_SRCS,
    ) + TESTING_SRCS,
    visibility = ["//visibility:public"],
)

# ---- Unit Tests ----

EXCLUDED_TESTS = TESTING_SRCS

gen_java_tests(
    name = "api_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = EXCLUDED_TESTS,
    ),
    deps = [
        ":api",
        ":stack_getter_common",
        ":stack_getter_impl",
        ":system_backend",
        ":testing",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/guava:testlib",
        "@google_bazel_common//third_party/java/jspecify_annotations",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/truth",
    ],
)
