# Copyright (C) 2024 The Flogger Authors.
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

"""Utilities for building Flogger."""

def genjar(name, srcs = [], deps = [], **kwargs):
    """Compiles Java source files to a jar file using a genrule.

    Args:
        name: The name of the target. The output jar file will be named <name>.jar.
        srcs: The Java source files to compile.
        deps: The Java libraries to depend on.
        **kwargs: Additional arguments to pass to genrule.
    """
    native.genrule(
        name = name,
        srcs = srcs + deps,
        outs = [name + ".jar"],
        cmd =
            """
                set -eu
                TMPDIR="$$(mktemp -d)"
                "$(JAVABASE)/bin/javac" -d "$$TMPDIR" \
                    -source 8 -target 8 -implicit:none \
                    -cp "{classpath}" {srcs}
                "$(JAVABASE)/bin/jar" cf "$@" -C "$$TMPDIR" .
            """.format(
                srcs = " ".join(["$(location %s)" % src for src in srcs]),
                classpath = ":".join(["$(location %s)" % dep for dep in deps]),
            ),
        **kwargs
    )
