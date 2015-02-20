#!/usr/bin/env bash
export SVPARSE_EXTRA=`pwd`/svparse_extra_serializer.xml
./build/install/svparse/bin/svparse $1
