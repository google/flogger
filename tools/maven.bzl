"""Macros to simplify generating maven files.
"""

load("@google_bazel_common//tools/maven:pom_file.bzl", default_pom_file = "pom_file")

def pom_file(name, targets, artifact_name, artifact_id, **kwargs):
  default_pom_file(
      name = name,
      targets = targets,
      preferred_group_ids = [
          "com.google.flogger",
          "com.google",
      ],
      template_file = "//tools:pom-template.xml",
      substitutions = {
          "{artifact_name}": artifact_name,
          "{artifact_id}": artifact_id,
      },
      **kwargs
  )
