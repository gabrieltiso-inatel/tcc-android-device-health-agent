#!/bin/sh

app_path=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -classpath "$app_path/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
