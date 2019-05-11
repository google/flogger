# Copyright (C) 2018 The Flogger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "google_bazel_common",
    strip_prefix = "bazel-common-f3dc1a775d21f74fc6f4bbcf076b8af2f6261a69",
    urls = ["https://github.com/google/bazel-common/archive/f3dc1a775d21f74fc6f4bbcf076b8af2f6261a69.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

LOG4J_VERS = "2.11.2"

maven_jar(
    name = "log4j_api",
    artifact = "org.apache.logging.log4j:log4j-api:" + LOG4J_VERS,
    sha1 = "f5e9a2ffca496057d6891a3de65128efc636e26e",
)

maven_jar(
    name = "log4j_core",
    artifact = "org.apache.logging.log4j:log4j-core:" + LOG4J_VERS,
    sha1 = "6c2fb3f5b7cd27504726aef1b674b542a0c9cf53",
)
