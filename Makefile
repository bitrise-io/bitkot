SHELL=/usr/bin/env bash -eux -o pipefail

.PHONY: init_gradle
init_gradle:
	bazel run //:write_gradle_configuration