#!/bin/bash

# Run the in-game dev tests (development only; the test mod is never part of the mod jar)
#
#   meta/scripts/run_devtest.sh                    every feature
#   meta/scripts/run_devtest.sh legacy_vehicles    one or more features (ids separated by spaces)
#   TESTS="Splash" meta/scripts/run_devtest.sh legacy_vehicles
#                                                  only the tests whose name contains the text
#   SKIP=legacy_vehicles meta/scripts/run_devtest.sh
#                                                  every feature except these (ids separated by spaces or commas)
#   FAST=true meta/scripts/run_devtest.sh          leave out the slow tests (quick check while working;
#                                                  run everything before committing)
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

SKIP_LIST=$(echo "${SKIP:-}" | tr ' ' ',')

./gradlew devTest -Pfeatures="$FEATURES" -Ptests="${TESTS:-}" -Pskip="$SKIP_LIST" -Pfast="${FAST:-false}" \
	-PupdateBaselines="${UPDATE_BASELINES:-false}"
