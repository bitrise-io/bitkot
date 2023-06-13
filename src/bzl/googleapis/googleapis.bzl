"""googleapis definitions"""

_BUILD_CONTENT = """\
package(default_visibility = ["//visibility:public"])

alias(
    name = "google_api_annotations_proto",
    actual = "//google/api:annotations_proto",
)

alias(
    name = "google_api_http_proto",
    actual = "//google/api:http_proto",
)

alias(
    name = "google_longrunning_operations_proto",
    actual = "//google/longrunning:operations_proto",
)

alias(
    name = "google_rpc_status_proto",
    actual = "//google/rpc:status_proto",
)
"""

_BYTESTREAM_BUILD_CONTENT = """\
package(default_visibility = ["//visibility:public"])

proto_library(
    name = "bytestream_proto",
    srcs = ["bytestream.proto"],
    deps = [
        "@googleapis//google/api:annotations_proto",
        "@com_google_protobuf//:wrappers_proto",
    ],
)

java_proto_library(
    name = "bytestream_java_proto",
    deps = [":bytestream_proto"],
)
"""


def _googleapis_impl(rctx):
    rctx.download_and_extract(
        "https://github.com/googleapis/googleapis/archive/%s.zip" % rctx.attr.version,
        sha256 = rctx.attr.sha256,
        stripPrefix = "googleapis-%s" % rctx.attr.version,
    )
    rctx.delete("BUILD.bazel")
    rctx.file("BUILD.bazel", _BUILD_CONTENT)
    rctx.file("google/bytestream/BUILD.bazel", _BYTESTREAM_BUILD_CONTENT)


googleapis = repository_rule(
    implementation = _googleapis_impl,
    attrs = {
        "version": attr.string(mandatory = True),
        "sha256": attr.string(mandatory = True),
    },
)
