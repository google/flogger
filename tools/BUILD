# Copyright (C) 2018 The Flogger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

package(default_visibility = ["//:internal"])

exports_files(["pom-template.xml"])

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "all_javadoc",
    testonly = 1,
    srcs = [
        "//api:javadoc_sources",
        "//google:javadoc_sources",
    ],
    bottom_text = "Copyright &copy; 2018&ndash; The Flogger Authors. All rights reserved.",
    doctitle = "Flogger: A Fluent Logging API for Java",
    exclude_packages = ["com.google.common.flogger.util"],
    external_javadoc_links = [
        "https://google.github.io/guava/releases/24.1-jre/api/docs/",
        "https://google.github.io/truth/api/0.40/",
        "http://errorprone.info/api/latest/",
        "https://junit.org/junit4/javadoc/4.11/",
    ],
    root_packages = ["com.google.common.flogger"],
    deps = [
        "//api",
        "//api:testing",
        "//google:flogger",
    ],
)
