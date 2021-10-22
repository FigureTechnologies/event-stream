#!/bin/sh
set -e

exec java $JAVA_OPTIONS -jar $1 $JAVA_ARGS
