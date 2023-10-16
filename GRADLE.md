#### Generate gradle project

To generate gradle project, just run:

    bazel run //:write_gradle_configuration

And you can call gradle build in this dir right after:

    ./gradlew build
