# The GoogleLogger fluent logging library.
#
# This package contains the extended API and classes to implement GoogleLogger
# on top of the core Flogger libraries. The core library is in the 'api'
# directory (which also defines classes in the "com.google.common.flogger"
# package).
#

package(default_visibility = ["//:internal"])

GOOGLE_LOGGER_SRCS = glob(["src/main/java/com/google/common/flogger/*.java"])

java_library(
    name = "flogger",
    srcs = GOOGLE_LOGGER_SRCS,
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//api:system_backend",
    ],
    deps = [
        "@google_bazel_common//third_party/java/error_prone:annotations",
        # TODO(dbeaumont): Split this so truly public things can be exported
        # without needing implementation to be visible to the world.
        "@google_bazel_common//third_party/java/jsr305_annotations",
        "//api",
        "//api:checks",
    ],
)

load("//tools:maven.bzl", "pom_file")

pom_file(
    name = "google_logger_pom",
    artifact_id = "google-extensions",
    artifact_name = "Google Logger",
    targets = [":flogger"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "flogger_javadoc",
    srcs = GOOGLE_LOGGER_SRCS,
    root_packages = ["com.google.common.flogger"],
    deps = [":flogger"],
)

filegroup(
    name = "javadoc_sources",
    srcs = GOOGLE_LOGGER_SRCS,
)

# ---- Tests ----

load("@google_bazel_common//testing:test_defs.bzl", "gen_java_tests")

gen_java_tests(
    name = "google_logger_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        # Use this for tests which call otherwise prohibited APIs (bypasses errorprone checks).
        ":flogger",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/guava:testlib",
        "//api:config",
        "//api:testing",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
        "//api",
    ],
)
