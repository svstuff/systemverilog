#!/usr/bin/env bash
export SVPARSE_EXTRA=`pwd`/svparse_extra.xml
./build/install/svparse/bin/svparse $1 2>&1 | ./fixtrace.py
