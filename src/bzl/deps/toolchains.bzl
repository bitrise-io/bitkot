"""different toolchains setup stuff"""
load("//src/bzl/utility:func_name.bzl", "func_name")
load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")
load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")


def inittialize_toolchains(f, registered_repos):
    if func_name(f) in registered_repos:
        f()


def bitkot_toolchains(registered_repos):
    """yep it is different toolchains setup stuff"""
    inittialize_toolchains(io_bazel_rules_kotlin, registered_repos)
    inittialize_toolchains(rules_jvm_external, registered_repos)
    

def io_bazel_rules_kotlin():
    kt_register_toolchains()


def rules_jvm_external():
    rules_jvm_external_setup()
