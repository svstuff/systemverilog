#!/usr/bin/env python

import sys
import os
import os.path as path
import glob
import subprocess as sp
import runtest

sp.check_call('./gradlew installApp', shell=True)

failures = []

print "Lexer tests:"
for test in glob.glob("regressiontests/*"):
	print "- Running test: {}".format(test)
        try:
	        if runtest.run_test(path.join(test, "project.xml")) != 0:
		        failures.append(test)
        except:
                print "- ERROR: exception."
		failures.append(test)

if failures:
	print "There are {} failures:".format(len(failures))
	for f in failures:
		print "- {}".format(f)
	sys.exit(1)

print "All tests passed."
sys.exit(0)
