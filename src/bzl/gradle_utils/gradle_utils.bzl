load("@rules_jvm_external//:specs.bzl", "parse")


def _artifact_to_coord(artifact):
    return "%s:%s:%s%s" % (
        artifact["group"],
        artifact["artifact"],
        artifact["version"],
        (":%s" % artifact["classifier"]) if artifact.get("classifier") != None else "",
    )


def map_artifacts_to_maven_coords(artifacts):
    return [
        _artifact_to_coord(artifact)
        for artifact in 
        parse.parse_artifact_spec_list(artifacts)
    ]
