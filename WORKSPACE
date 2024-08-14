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

# If this needs updating the hash value in the "strip_prefix" and "urls" lines
# should be the hash of the latest Github commit for bazel-common. The "sha256"
# value should be the SHA-256 of the downloaded zip file, but if you just try
# and commit with the old value then GitHub Actions should report the expected
# value in the most recent failure in
# https://github.com/google/flogger/actions?query=is%3Afailure
BAZEL_COMMON_VERSION = "985e8ef206f8beab133646b091605b77423a6598"
http_archive(
    name = "google_bazel_common",
    sha256 = "7190e40930005476a8928e3d7516b89ab8ad0eedd657fe65e6d3ad2d30aba780",
    strip_prefix = "bazel-common-%s" % BAZEL_COMMON_VERSION,
    url = "https://github.com/google/bazel-common/archive/%s.zip" % BAZEL_COMMON_VERSION,
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

http_archive(
    name = "openjdk8",
    build_file_content = """
java_runtime(name = 'runtime', srcs =  glob(['**']), visibility = ['//visibility:public'])
exports_files(["WORKSPACE"], visibility = ["//visibility:public"])
""",
    sha256 = "568e7578f1b20b1e62a8ed2c374bad4eb0e75d221323ccfa6ba8d7bc56cf33cf",
    strip_prefix = "zulu8.38.0.13-ca-jdk8.0.212-linux_x64",
    urls = ["https://mirror.bazel.build/openjdk/azul-zulu8.38.0.13-ca-jdk8.0.212/zulu8.38.0.13-ca-jdk8.0.212-linux_x64.tar.gz"],
)

http_archive(
    name = "openjdk11",
    build_file_content = """
java_runtime(name = 'runtime', srcs =  glob(['**']), visibility = ['//visibility:public'])
exports_files(["WORKSPACE"], visibility = ["//visibility:public"])
""",
    sha256 = "ddb0fd4526089cf1ce2db36282c282263f587a9e8be373fa02f511a12923cc48",
    strip_prefix = "zulu11.31.11-ca-jdk11.0.3-linux_x64",
    urls = ["https://mirror.bazel.build/openjdk/azul-zulu11.31.11-ca-jdk11.0.3/zulu11.31.11-ca-jdk11.0.3-linux_x64.tar.gz"],
)
