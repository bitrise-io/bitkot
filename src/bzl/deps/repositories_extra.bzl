"""deps of deps here"""

load("//src/bzl/deps:repositories.bzl", "PROTOBUF_VERSION")
load("//src/bzl/utility:func_name.bzl", "func_name")
load("@aspect_bazel_lib//lib:repositories.bzl", "aspect_bazel_lib_dependencies", "aspect_bazel_lib_register_toolchains")
load("@rules_jvm_external//:specs.bzl", "maven")
load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")
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
load("@googleapis//:repository_rules.bzl", "switched_rules_by_language")

NETTY_VERSION = "4.1.100.Final"
KOTLINX_COROUTINES_VERSION = "1.7.3"
KOTLINX_SERIALIZATION_VERSION = "1.6.0"
RSOCKET_VERSION = "1.1.4"
REACTOR_VERSION = "1.1.12"

IO_BITRISE_BITKOT_ARTIFACTS = [
    "com.google.protobuf:protobuf-java:3.%s" % PROTOBUF_VERSION,
    "com.google.protobuf:protobuf-kotlin:3.%s" % PROTOBUF_VERSION,
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:%s" % KOTLINX_SERIALIZATION_VERSION,
    "com.charleskorn.kaml:kaml-jvm:0.55.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:%s" % KOTLINX_COROUTINES_VERSION,
    "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:%s" % KOTLINX_COROUTINES_VERSION,
    "io.netty:netty-transport-native-unix-common:%s" % NETTY_VERSION,
    "io.netty:netty-transport-native-epoll:%s" % NETTY_VERSION,
    maven.artifact(
        "io.netty",
        "netty-transport-native-epoll",
        NETTY_VERSION,
        classifier = "linux-x86_64",
    ),
    maven.artifact(
        "io.netty",
        "netty-transport-native-epoll",
        NETTY_VERSION,
        classifier = "linux-aarch_64",
    ),
    "io.netty:netty-transport-native-kqueue:%s" % NETTY_VERSION,
    maven.artifact(
        "io.netty",
        "netty-transport-native-kqueue",
        NETTY_VERSION,
        classifier = "osx-x86_64",
    ),
    maven.artifact(
        "io.netty",
        "netty-transport-native-kqueue",
        NETTY_VERSION,
        classifier = "osx-aarch_64",
    ),
    "io.netty:netty-resolver-dns-native-macos:%s" % NETTY_VERSION,
    maven.artifact(
        "io.netty",
        "netty-resolver-dns-native-macos",
        NETTY_VERSION,
        classifier = "osx-x86_64",
    ),
    maven.artifact(
        "io.netty",
        "netty-resolver-dns-native-macos",
        NETTY_VERSION,
        classifier = "osx-aarch_64",
    ),
    "io.rsocket:rsocket-core:%s" % RSOCKET_VERSION,
    "io.rsocket:rsocket-transport-netty:%s" % RSOCKET_VERSION,
    "io.projectreactor.netty:reactor-netty:%s" % REACTOR_VERSION,
]

IO_BITRISE_BITKOT_ALL_ARTIFACTS = IO_GRPC_GRPC_JAVA_ARTIFACTS + IO_GRPC_GRPC_KOTLIN_ARTIFACTS + IO_BITRISE_BITKOT_ARTIFACTS

IO_BITRISE_BITKOT_OVERRIDE_TARGETS = {
    "org.jetbrains.kotlin:kotlin-stdlib-common": "@com_github_jetbrains_kotlin//:kotlin-stdlib",
    "org.jetbrains.kotlin:kotlin-stdlib": "@com_github_jetbrains_kotlin//:kotlin-stdlib",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7": "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk7",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8": "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk8",
    "org.jetbrains.kotlin:kotlin-script-runtime": "@com_github_jetbrains_kotlin//:kotlin-script-runtime",
    "org.jetbrains.kotlin:kotlin-reflect": "@com_github_jetbrains_kotlin//:kotlin-reflect",
}

IO_BITRISE_BITKOT_ALL_OVERRIDE_TARGETS = dict(IO_GRPC_GRPC_KOTLIN_OVERRIDE_TARGETS.items() + IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS.items() + IO_BITRISE_BITKOT_OVERRIDE_TARGETS.items())

def initialize_extra_deps(f, registered_repos):
    if func_name(f) in registered_repos:
        f()

def bitkot_repositories_extra(registered_repos):
    """yep it is deps of deps here"""

    initialize_extra_deps(aspect_bazel_lib, registered_repos)
    initialize_extra_deps(io_bazel_rules_kotlin, registered_repos)
    initialize_extra_deps(com_google_protobuf, registered_repos)
    initialize_extra_deps(io_grpc_grpc_java, registered_repos)
    initialize_extra_deps(com_github_grpc_grpc_kotlin, registered_repos)
    initialize_extra_deps(rules_jvm_external, registered_repos)
    initialize_extra_deps(googleapis, registered_repos)

def aspect_bazel_lib():
    aspect_bazel_lib_dependencies()
    aspect_bazel_lib_register_toolchains()

def io_bazel_rules_kotlin():
    kotlin_repositories()

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
