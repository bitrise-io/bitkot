defs = struct(
    GRPC_DEPS = [
        "@io_grpc_grpc_java//api",
        "@io_grpc_grpc_java//netty",
        "@io_grpc_grpc_java//protobuf",
        "@io_grpc_grpc_java//stub",
    ],
    KOTLIN_STD = [
        "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk7",
        "@com_github_jetbrains_kotlin//:kotlin-stdlib-jdk8",
    ],
    TEST_DEPS = [
        "@com_github_jetbrains_kotlin//:kotlin-test",
        "@junit_junit//jar",
        "@org_jetbrains_kotlinx_kotlinx_coroutines_test//jar",
    ],
)
