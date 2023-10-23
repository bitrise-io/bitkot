defs = struct(
    GRPC_DEPS = [
        "@io_grpc_grpc_java//api",
        "@io_grpc_grpc_java//netty",
        "@io_grpc_grpc_java//protobuf",
        "@io_grpc_grpc_java//stub",
    ],
    KOTLIN_STD = [
        "@io_bazel_rules_kotlin//kotlin/compiler:kotlin-stdlib-jdk7",
        "@io_bazel_rules_kotlin//kotlin/compiler:kotlin-stdlib-jdk8",
    ],
    TEST_DEPS = [
        "@io_bazel_rules_kotlin//kotlin/compiler:kotlin-test",
        "@junit_junit//jar",
        "@org_jetbrains_kotlinx_kotlinx_coroutines_test//jar",
    ],
)
