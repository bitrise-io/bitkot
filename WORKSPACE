workspace(name = "com_github_bitrise_io_bitkot")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

####### BitKot exported deps

load("//src/bzl/deps:repositories.bzl", "bitkot_repositories")

registered_repos = bitkot_repositories()

load(
    "//src/bzl/deps:repositories_extra.bzl",
    "IO_BITRISE_BITKOT_ARTIFACTS",
    "IO_BITRISE_BITKOT_OVERRIDE_TARGETS",
    "bitkot_repositories_extra",
)

bitkot_repositories_extra(registered_repos)

load("//src/bzl/deps:toolchains.bzl", "bitkot_toolchains")

bitkot_toolchains(registered_repos)

####### Maven deps

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = IO_BITRISE_BITKOT_ARTIFACTS,
    fetch_sources = True,
    generate_compat_repositories = True,
    override_targets = IO_BITRISE_BITKOT_OVERRIDE_TARGETS,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
        "https://jcenter.bintray.com/",
    ],
)

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()

####### Bazel Distribution

http_archive(
    name = "com_github_vaticle_bazel_distribution",
    sha256 = "0d0c3128fae3c9a9349e38c0de1b82cef9c8b7f2d31b1aac77e0e111bf546a4b",
    strip_prefix = "bazel-distribution-3da5196dfc18fc2e235a3ccc146ea6a90a20702f",
    url = "https://github.com/vaticle/bazel-distribution/archive/3da5196dfc18fc2e235a3ccc146ea6a90a20702f.zip",
)

####### Bazel Remote

_BAZEL_REMOTE_VERSION = "2.4.0"

http_file(
    name = "bazel_remote_darwin_x86_64",
    executable = True,
    sha256 = "95aab5bc413f85f5b3700623e3f216221155ae20f950291a32812a12a42e8009",
    url = "https://github.com/buchgr/bazel-remote/releases/download/v{version}/bazel-remote-{version}-darwin-x86_64".format(
        version = _BAZEL_REMOTE_VERSION,
    ),
)

http_file(
    name = "bazel_remote_darwin_arm64",
    executable = True,
    sha256 = "c140ea611543d260df2ee44114324c31f8a4e6227303b44ed8090256a8e0f1d5",
    url = "https://github.com/buchgr/bazel-remote/releases/download/v{version}/bazel-remote-{version}-darwin-arm64".format(
        version = _BAZEL_REMOTE_VERSION,
    ),
)

http_file(
    name = "bazel_remote_linux_x86_64",
    executable = True,
    sha256 = "717a44dd526c574b0a0edda1159f5795cc1b2257db1d519280a3d7a9c5addde5",
    url = "https://github.com/buchgr/bazel-remote/releases/download/v{version}/bazel-remote-{version}-linux-x86_64".format(
        version = _BAZEL_REMOTE_VERSION,
    ),
)

http_file(
    name = "bazel_remote_linux_arm64",
    executable = True,
    sha256 = "02830205323881605ed94e4c10031025f2d0f619b3f00c81257d4145dc97b330",
    url = "https://github.com/buchgr/bazel-remote/releases/download/v{version}/bazel-remote-{version}-linux-arm64".format(
        version = _BAZEL_REMOTE_VERSION,
    ),
)
