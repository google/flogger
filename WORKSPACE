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

http_archive(
    name = "google_bazel_common",
    strip_prefix = "bazel-common-55049dacf10255c91a934797ca2f1310066f0d38",
    urls = ["https://github.com/google/bazel-common/archive/55049dacf10255c91a934797ca2f1310066f0d38.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

maven_jar(
    name = "org_slf4j_slf4j_api",
    artifact= "org.slf4j:slf4j-api:1.7.25",
)
