load("@rules_jvm_external//:specs.bzl", "parse")
load("@bazel_skylib//lib:sets.bzl", "sets")


def vc_library(module, version_ref, skip_dep = False):
    return struct(
        module = module,
        version_ref = version_ref,
        skip_dep = skip_dep,
    )


def vc_plugin(id, version_ref):
    return struct(
        id = id,
        version_ref = version_ref,
    )


def _artifact_to_module(artifact):
    return "%s:%s" % (
        artifact["group"],
        artifact["artifact"],
    )


def _to_version_catalog_content(data):
    result = []

    if data.versions:
        result.append("[versions]")
        result += [
            "%s = \"%s\"" % (version_key, version)
            for version_key, version in data.versions.items()
        ]
        result.append("")

    if data.libraries:
        result.append("[libraries]")
        result += [
            "%s = { module = \"%s\", version.ref = \"%s\" }" % (k, v.module, v.version_ref)
            for k, v in data.libraries.items()
        ]
        result.append("")

    if data.plugins:
        result.append("[plugins]")
        result += [
            "%s = { id = \"%s\", version.ref = \"%s\" }" % (k, v.id, v.version_ref)
            for k, v in data.plugins.items()
        ]
        result.append("")

    return result


def _to_plugins(data):
    return "\n    ".join([
        "alias(libs.plugins.%s)" % k.replace("-", ".")
        for k in data.plugins.keys()
    ])


def _to_libraries(data):
    return "\n    ".join([
        "implementation(libs.%s)" % k.replace("-", ".")
        for k, l in data.libraries.items()
        if not l.skip_dep
    ])


def _to_artifacts(data):
    filtered_artifacts = []
    for artifact in data.artifacts:
        if artifact.get("classifier") != None:
            filtered_artifacts.append(artifact)
            continue

        if _artifact_to_module(artifact) in data.registered_modules:
            continue

        filtered_artifacts.append(artifact)

    result = []
    for artifact in filtered_artifacts:
        module = _artifact_to_module(artifact)
        version = artifact["version"]
        for version_key in data.reverse_versions.get(version, default = []):
            if version_key in module:
                version = "$${libs.versions.%s.get()}" % version_key.replace("-", ".")
                break
        result.append("%s:%s%s" % (
            module,
            version,
            ":%s" % artifact["classifier"] if artifact.get("classifier") != None else "",
        ))

    return "\n    ".join([
        "implementation(\"%s\")" % x
        for x in result
    ])


def parse_deps_for_gradle(versions, libraries, plugins, artifacts):
    reverse_versions = {}
    for k, v in versions.items():
        if not k in reverse_versions:
            reverse_versions[v] = [k]
        else:
            reverse_versions[v].append(k)

    registered_modules = {
        l.module: None 
        for l in libraries.values()
    }

    data = struct(
        versions = versions,
        reverse_versions = reverse_versions,
        libraries = libraries,
        registered_modules = registered_modules,
        plugins = plugins,
        artifacts = parse.parse_artifact_spec_list(artifacts),
    )
    return struct(
        to_version_catalog = lambda: _to_version_catalog_content(data),
        to_plugins = lambda: _to_plugins(data),
        to_libraries = lambda: _to_libraries(data),
        to_artifacts = lambda: _to_artifacts(data),
    )