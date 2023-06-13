"""repositories"""
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//src/bzl/googleapis:googleapis.bzl", _googleapis = "googleapis")
load("//src/bzl/utility:func_name.bzl", "func_name")


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
    initialize_dep(io_bazel_rules_kotlin, registered)
    initialize_dep(com_google_protobuf, registered)
    initialize_dep(io_grpc_grpc_java, registered)
    initialize_dep(com_github_grpc_grpc_kotlin, registered)
    initialize_dep(rules_jvm_external, registered)
    initialize_dep(googleapis, registered)
    initialize_dep(com_github_bazelbuild_remote_apis, registered)
    initialize_dep(rules_cc, registered)
    initialize_dep(com_grail_bazel_toolchain, registered)
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


def io_bazel_rules_kotlin():
    http_archive(
        name = "io_bazel_rules_kotlin",
        sha256 = "fd92a98bd8a8f0e1cdcb490b93f5acef1f1727ed992571232d33de42395ca9b3",
        url = "https://github.com/bazelbuild/rules_kotlin/releases/download/v1.7.1/rules_kotlin_release.tgz",
    )


def com_google_protobuf():
    http_archive(
        name = "com_google_protobuf",
        sha256 = "f6ac7f4b735f9b7c50e45cff845e787eeb4acde9a8955542c9f1f7f95ada876f",
        strip_prefix = "protobuf-23.3",
        url = "https://github.com/protocolbuffers/protobuf/archive/v23.3.zip",
    )


def io_grpc_grpc_java():
    http_archive(
        name = "io_grpc_grpc_java",
        sha256 = "b1d2db800d3cce5a219ce75433eff3f195245902fd67b15a59e35f459c2ee90a",
        strip_prefix = "grpc-java-1.55.1",
        url = "https://github.com/grpc/grpc-java/archive/refs/tags/v1.55.1.zip",
    )


def com_github_grpc_grpc_kotlin():
    http_archive(
        name = "com_github_grpc_grpc_kotlin",
        sha256 = "7d06ab8a87d4d6683ce2dea7770f1c816731eb2a172a7cbb92d113ea9f08e5a7",
        strip_prefix = "grpc-kotlin-1.3.0",
        url = "https://github.com/grpc/grpc-kotlin/archive/refs/tags/v1.3.0.zip",
    )


def rules_jvm_external():
    RULES_JVM_EXTERNAL_TAG = "5.2"
    RULES_JVM_EXTERNAL_SHA ="f86fd42a809e1871ca0aabe89db0d440451219c3ce46c58da240c7dcdc00125f"
    http_archive(
        name = "rules_jvm_external",
        strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
        sha256 = RULES_JVM_EXTERNAL_SHA,
        url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (
            RULES_JVM_EXTERNAL_TAG, 
            RULES_JVM_EXTERNAL_TAG,
        )
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
        url = "https://github.com/bazelbuild/rules_cc/releases/download/0.0.6/rules_cc-0.0.6.tar.gz",
        sha256 = "3d9e271e2876ba42e114c9b9bc51454e379cbf0ec9ef9d40e2ae4cec61a31b40",
        strip_prefix = "rules_cc-0.0.6",
    )


def com_grail_bazel_toolchain():
    BAZEL_TOOLCHAIN_COMMIT = "c65ef7a45907016a754e5bf5bfabac76eb702fd3"
    BAZEL_TOOLCHAIN_SHA = "511ff1ccca8873a1edfa9e254755263f78652b00ffce04c66ce675430081036a"
    http_archive(
        name = "com_grail_bazel_toolchain",
        sha256 = BAZEL_TOOLCHAIN_SHA,
        strip_prefix = "bazel-toolchain-{tag}".format(tag = BAZEL_TOOLCHAIN_COMMIT),
        url = "https://github.com/grailbio/bazel-toolchain/archive/{commit}.zip".format(commit = BAZEL_TOOLCHAIN_COMMIT),
    )
    _SYSTROOT_ALL_FILES = """\
filegroup(
    name = "sysroot",
    srcs = glob([ "*/**" ]),
    visibility = [ "//visibility:public" ],
)
"""
    http_archive(
        name = "sysroot_debian11_amd64",
        build_file_content = _SYSTROOT_ALL_FILES,
        sha256 = "bacf1506da1bfc9dbb4e856aa61b2a80af75956d4f812708e29058042c27b444",
        url = "https://commondatastorage.googleapis.com/chrome-linux-sysroot/toolchain/3bdb3503702d35520d101fc5eec9a8ab5353149f/debian_bullseye_amd64_sysroot.tar.xz",
    )
    http_archive(
        name = "sysroot_debian11_arm64",
        build_file_content = _SYSTROOT_ALL_FILES,
        sha256 = "3ad71e52f7052f0a4a75a35bd2a6ce55fa26fb186a0760b971c7d11957d137b7",
        url = "https://commondatastorage.googleapis.com/chrome-linux-sysroot/toolchain/6553f74237ef4a99240a740f0084e1ade71aec6f/debian_bullseye_arm64_sysroot.tar.xz",
    )


