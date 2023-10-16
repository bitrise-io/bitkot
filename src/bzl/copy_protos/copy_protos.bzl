load("@aspect_bazel_lib//lib:copy_to_directory.bzl", "copy_to_directory_bin_action")

_AllProtoInfos = provider(
    fields = [
        "infos",
    ],
)


def _collect_all_infos_from_deps(deps):
    result = {}
    for dep in deps:
        if not _AllProtoInfos in dep:
            continue
        result.update(dep[_AllProtoInfos].infos)
    return result


def _collect_all_proto_infos_aspect_impl(target, ctx):
    if not ProtoInfo in target:
        fail("no ProtoInfo on target: " + target.label)

    infos = {target.label: target[ProtoInfo]}
    infos.update(_collect_all_infos_from_deps(ctx.rule.attr.deps))
    return _AllProtoInfos(infos = infos)


_collect_all_proto_infos_aspect = aspect(
    attr_aspects = [
        "deps",
    ],
    implementation = _collect_all_proto_infos_aspect_impl,
)


def _copy_protos_impl(ctx):
    infos = _collect_all_infos_from_deps(ctx.attr.deps)
    files = []
    for proto_info in infos.values():
        files += proto_info.direct_sources

    out = ctx.actions.declare_directory(ctx.attr.name)
    copy_to_directory_bin_action(
        ctx,
        ctx.attr.name,
        out,
        ctx.toolchains["@aspect_bazel_lib//lib:copy_to_directory_toolchain_type"].copy_to_directory_info.bin,
        files = files,
        include_external_repositories = ctx.attr.include_external_repositories,
    )
    return DefaultInfo(files = depset([out]))


copy_protos = rule(
    implementation = _copy_protos_impl,
    attrs = {
        "deps": attr.label_list(
            mandatory = True,
            aspects = [
                _collect_all_proto_infos_aspect,
            ],
            providers = [
                ProtoInfo,
            ],
        ),
        "include_external_repositories": attr.string_list(
            mandatory = True,
        ),
    },
    toolchains = ["@aspect_bazel_lib//lib:copy_to_directory_toolchain_type"],
)