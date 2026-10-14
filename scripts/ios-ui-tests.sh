#!/bin/sh
# Runs the app's UI tests on the iOS simulator against the local API (docs/architecture/mobile-app.md section 9).
# The API must be running with the profile e2e. The password of e2e-admin comes from .env and reaches the test
# runner through Xcode's TEST_RUNNER_ prefix, so it never appears on the command line.
# Usage: scripts/ios-ui-tests.sh [simulator name] [xcodebuild options]   (default: iPhone 17e)
# Example: scripts/ios-ui-tests.sh "iPhone 17e" -only-testing:iosAppUITests/LookupUITests
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
device=${1:-iPhone 17e}
[ $# -gt 0 ] && shift
password=$(sed -n 's/^E2E_ADMIN_PASSWORD=//p' "$root/.env")
[ -n "$password" ] || { echo "E2E_ADMIN_PASSWORD is not set in .env (see .env.example)" >&2; exit 1; }
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
TEST_RUNNER_E2E_ADMIN_PASSWORD=$password exec xcodebuild test \
  -project "$root/app/iosApp/iosApp.xcodeproj" -scheme iosApp \
  -destination "platform=iOS Simulator,name=$device" \
  -derivedDataPath "$root/app/build/ios-ui-tests" "$@"
