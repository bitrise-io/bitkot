"""repositories"""
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//src/bzl/googleapis:googleapis.bzl", _googleapis = "googleapis")
load("//src/bzl/utility:func_name.bzl", "func_name")

PROTOBUF_VERSION = "24.4"
GRPC_JAVA_VERSION = "1.58.0"
GRPC_KOTLIN_VERSION = "1.4.0"


def initialize_dep(f, registered):
    fstr = func_name(f)
    if native.existing_rule(fstr):
        return
    registered.append(fstr)
    f()


def bitkot_repositories():
    """yep it is repositories.

    Returns:
        registered dependenncies tags tyo reuse in repositories_extra,bzl
    """
    registered = []
    initialize_dep(bazel_skylib, registered)
    initialize_dep(aspect_bazel_lib, registered)
    initialize_dep(io_bazel_rules_kotlin, registered)
    initialize_dep(com_google_protobuf, registered)
    initialize_dep(io_grpc_grpc_java, registered)
    initialize_dep(com_github_grpc_grpc_kotlin, registered)
    initialize_dep(rules_jvm_external, registered)
    initialize_dep(googleapis, registered)
    initialize_dep(com_github_bazelbuild_remote_apis, registered)
    initialize_dep(rules_cc, registered)
    return registered


def bazel_skylib():
    http_archive(
        name = "bazel_skylib",
        sha256 = "66ffd9315665bfaafc96b52278f57c7e2dd09f5ede279ea6d39b2be471e7e3aa",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.4.2/bazel-skylib-1.4.2.tar.gz",
            "https://github.com/bazelbuild/bazel-skylib/releases/download/1.4.2/bazel-skylib-1.4.2.tar.gz",
        ],
    )


def aspect_bazel_lib():
    http_archive(
        name = "aspect_bazel_lib",
        sha256 = "91acfc0ef798d3c87639cbfdb6274845ad70edbddfd92e49ac70944f08f97f58",
        strip_prefix = "bazel-lib-2.0.0-rc0",
        url = "https://github.com/aspect-build/bazel-lib/releases/download/v2.0.0-rc0/bazel-lib-v2.0.0-rc0.tar.gz",
    )


def io_bazel_rules_kotlin():
    io_bazel_rules_kotlin_version = "1.8"
    io_bazel_rules_kotlin_sha = "01293740a16e474669aba5b5a1fe3d368de5832442f164e4fbfc566815a8bc3a"
    http_archive(
        name = "io_bazel_rules_kotlin",
        url = "https://github.com/bazelbuild/rules_kotlin/releases/download/v%s/rules_kotlin_release.tgz" % io_bazel_rules_kotlin_version,
        sha256 = io_bazel_rules_kotlin_sha,
    )


def com_google_protobuf():
    http_archive(
        name = "com_google_protobuf",
        sha256 = "1b086ae1a01817482eed5bce04b631b7e3b38e43ade4ea32a8419b02b3f84f56",
        strip_prefix = "protobuf-%s" % PROTOBUF_VERSION,
        url = "https://github.com/protocolbuffers/protobuf/archive/v%s.zip" % PROTOBUF_VERSION,
    )


def io_grpc_grpc_java():
    http_archive(
        name = "io_grpc_grpc_java",
        sha256 = "20132d94cd9cc2dbffb1b34d684b00aaa4c0451ecea7f7e8be91eccc9259071f",
        strip_prefix = "grpc-java-%s" % GRPC_JAVA_VERSION,
        url = "https://github.com/grpc/grpc-java/archive/refs/tags/v%s.zip" % GRPC_JAVA_VERSION,
    )


def com_github_grpc_grpc_kotlin():
    http_archive(
        name = "com_github_grpc_grpc_kotlin",
        sha256 = "548e45a050b1f24be9556e70050806a54ae0863e2bd391f0e7b13a2123492b58",
        strip_prefix = "grpc-kotlin-%s" % GRPC_KOTLIN_VERSION,
        url = "https://github.com/grpc/grpc-kotlin/archive/refs/tags/v%s.zip" % GRPC_KOTLIN_VERSION,
    )


def rules_jvm_external():
    RULES_JVM_EXTERNAL_TAG = "5.3"
    RULES_JVM_EXTERNAL_SHA ="d31e369b854322ca5098ea12c69d7175ded971435e55c18dd9dd5f29cc5249ac"
    http_archive(
        name = "rules_jvm_external",
        strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
        sha256 = RULES_JVM_EXTERNAL_SHA,
        url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG)
    )


def googleapis():
    _googleapis(
        name = "googleapis",
        version = "cc2da762b6d540b62bfc738469b2f520c341bc57",
        sha256 = "cbcf47997a32b2fd71845b4726b2397c7eff19c47669a6c4a7aa591b9bf632e3",
    )


def com_github_bazelbuild_remote_apis():
    REMOTE_APIS_VERSION = "eafa1b0d7883ffef6520cfd5fd5ff7731b7d55d7"
    http_archive(
        name = "com_github_bazelbuild_remote_apis",
        sha256 = "9e9f448980bc9ccf9d9e7980e17504d1aa8a495721ad0183a362152ce0e725a4",
        strip_prefix = "remote-apis-%s" % REMOTE_APIS_VERSION,
        url = "https://github.com/bazelbuild/remote-apis/archive/%s.zip" % REMOTE_APIS_VERSION,
    )


def rules_cc():
    http_archive(
        name = "rules_cc",
        urls = ["https://github.com/bazelbuild/rules_cc/releases/download/0.0.9/rules_cc-0.0.9.tar.gz"],
        sha256 = "2037875b9a4456dce4a79d112a8ae885bbc4aad968e6587dca6e64f3a0900cdf",
        strip_prefix = "rules_cc-0.0.9",
    )
