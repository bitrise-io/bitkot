rootProject.name = "bitkot"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradlegen/libs.versions.toml"))
        }
    }
}