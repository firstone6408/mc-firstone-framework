#!/bin/bash

# Run the in-game dev tests (development only; the test mod is never part of the mod jar)
#
#   meta/scripts/run_devtest.sh                    every feature
#   meta/scripts/run_devtest.sh legacy_vehicles    one or more features (ids separated by spaces)
#   TESTS="Splash" meta/scripts/run_devtest.sh legacy_vehicles
#                                                  only the tests whose name contains the text
#   UPDATE_BASELINES=true meta/scripts/run_devtest.sh legacy_vehicles
#                                                  save the screenshots as the new baselines
#
# The game opens, creates a new flat world per feature, runs the tests and closes.
# Results: run/devtest/results/<feature>.md, screenshots in run/devtest/results/screenshots/

cd "$(dirname "$0")/../.." || exit 1

FEATURES="all"
if [ $# -gt 0 ]; then
	FEATURES=$(IFS=,; echo "$*")
fi

./gradlew devTest -Pfeatures="$FEATURES" -Ptests="${TESTS:-}" -PupdateBaselines="${UPDATE_BASELINES:-false}"
