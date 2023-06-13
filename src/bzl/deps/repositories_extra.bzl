"""deps of deps here"""

load("@rules_jvm_external//:specs.bzl", "maven")
load("//src/bzl/utility:func_name.bzl", "func_name")
load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories", "kotlinc_version")
load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")
load(
    "@io_grpc_grpc_java//:repositories.bzl",
    "IO_GRPC_GRPC_JAVA_ARTIFACTS",
    "IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS",
    "grpc_java_repositories",
)
load(
    "@com_github_grpc_grpc_kotlin//:repositories.bzl",
    "IO_GRPC_GRPC_KOTLIN_ARTIFACTS",
    "IO_GRPC_GRPC_KOTLIN_OVERRIDE_TARGETS",
    "grpc_kt_repositories",
)
load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
load("@googleapis//:repository_rules.bzl", "switched_rules_by_language")
load("@com_grail_bazel_toolchain//toolchain:rules.bzl", "llvm_toolchain")

_NETTY_VERSION = "4.1.77.Final"
_KOTLINX_COROUTINES_VERSION = "1.6.4"

IO_BITRISE_BITKOT_ARTIFACTS = IO_GRPC_GRPC_JAVA_ARTIFACTS + IO_GRPC_GRPC_KOTLIN_ARTIFACTS + [
    "com.google.protobuf:protobuf-kotlin:3.23.2",
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.4.1",
    "com.charleskorn.kaml:kaml-jvm:0.49.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:%s" % _KOTLINX_COROUTINES_VERSION,
    "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:%s" % _KOTLINX_COROUTINES_VERSION,
    "io.netty:netty-transport-native-unix-common:%s" % _NETTY_VERSION,
    "io.netty:netty-transport-native-epoll:%s" % _NETTY_VERSION,
    maven.artifact(
        "io.netty",
        "netty-transport-native-epoll",
        _NETTY_VERSION,
        classifier = "linux-x86_64",
    ),
    maven.artifact(
        "io.netty",
        "netty-transport-native-epoll",
        _NETTY_VERSION,
        classifier = "linux-aarch_64",
    ),
    "io.netty:netty-transport-native-kqueue:%s" % _NETTY_VERSION,
    maven.artifact(
        "io.netty",
        "netty-transport-native-kqueue",
        _NETTY_VERSION,
        classifier = "osx-x86_64",
    ),
    maven.artifact(
        "io.netty",
        "netty-transport-native-kqueue",
        _NETTY_VERSION,
        classifier = "osx-aarch_64",
    ),
]

IO_BITRISE_BITKOT_OVERRIDE_TARGETS = dict(IO_GRPC_GRPC_KOTLIN_OVERRIDE_TARGETS.items() + IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS.items() + {
    "org.jetbrains.kotlin:kotlin-stdlib-common": "@com_github_jetbrains_kotlin//:kotlin-stdlib",
    "org.jetbrains.kotlin:kotlin-stdlib": "@com_github_jetbrains_kotlin//:kotlin-stdlib",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7": "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk7",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8": "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk8",
    "org.jetbrains.kotlin:kotlin-script-runtime": "@com_github_jetbrains_kotlin//:kotlin-script-runtime",
    "org.jetbrains.kotlin:kotlin-reflect": "@com_github_jetbrains_kotlin//:kotlin-reflect",
}.items())

def initialize_extra_deps(f, registered_repos):
    if func_name(f) in registered_repos:
        f()

def bitkot_repositories_extra(registered_repos):
    """yep it is deps of deps here"""

    initialize_extra_deps(io_bazel_rules_kotlin, registered_repos)
    initialize_extra_deps(com_google_protobuf, registered_repos)
    initialize_extra_deps(io_grpc_grpc_java, registered_repos)
    initialize_extra_deps(com_github_grpc_grpc_kotlin, registered_repos)
    initialize_extra_deps(rules_jvm_external, registered_repos)
    initialize_extra_deps(googleapis, registered_repos)
    initialize_extra_deps(com_grail_bazel_toolchain, registered_repos)

def io_bazel_rules_kotlin():
    kotlin_repositories(
        compiler_release = kotlinc_version(
            release = "1.7.20",
            sha256 = "5e3c8d0f965410ff12e90d6f8dc5df2fc09fd595a684d514616851ce7e94ae7d",
        ),
    )

def com_google_protobuf():
    protobuf_deps()

def io_grpc_grpc_java():
    grpc_java_repositories()

def com_github_grpc_grpc_kotlin():
    grpc_kt_repositories()

def rules_jvm_external():
    rules_jvm_external_deps()

def googleapis():
    switched_rules_by_language(name = "com_google_googleapis_imports")

def com_grail_bazel_toolchain():
    llvm_toolchain(
        name = "llvm_toolchain",
        llvm_versions = {
            "": "15.0.6",
            "darwin-x86_64": "15.0.7",
            "darwin-aarch64": "15.0.7",
        },
        sysroot = {
            "linux-x86_64": "@sysroot_debian11_amd64//:sysroot",
            "linux-aarch64": "@sysroot_debian11_arm64//:sysroot",
        },
    )
