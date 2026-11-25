#!/usr/bin/env bash
set -eu

table_test_result=0
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || table_test_result=$?

mkdir -p app/build/ui-screenshots
adb exec-out run-as br.com.obracontrol.app tar -C files -cf - screenshots > app/build/ui-screenshots/screenshots.tar
tar -tf app/build/ui-screenshots/screenshots.tar
exit "$table_test_result"
