#!/usr/bin/env python

import sys
import os
import os.path as path
import subprocess as sp
import re

def run_test(test, check_tokens=True):
	cmd = ['./build/install/svparse/bin/svparse', test]
	pid = sp.Popen(cmd, stdout=sp.PIPE, stderr=sp.PIPE)
	_, rawout = pid.communicate()
	if pid.returncode != 0:
		print rawout
		return 1

	if not check_tokens:
		return 0

	actual = []
	for line in rawout.split(os.linesep):
		match = re.search(r'DEBUG_TOKEN:\s+(.*)', line)
		if match:
			tok = match.group(1)
			if not tok.startswith('EOF('):
				actual.append(match.group(1))

	with open(path.join(path.dirname(test), 'tokens'), 'r') as tokensfile:
		expected = [line.strip() for line in tokensfile.readlines() if line.strip()]

	mismatch = False

	if len(actual) != len(expected):
		print "ERROR, not the same number of tokens ({} actual vs {} expected)".format(len(actual), len(expected))
		mismatch = True
	else:
		for a, e in zip(actual, expected):
			if a != e:
				print "ERROR, expected <{}> but got <{}>".format(e, a)
				mismatch = True

	if mismatch:
		print "ACTUAL: -------------------------"
		print "\n".join(actual)
		print "\nEXPECTED: -----------------------"
		print "\n".join(expected)
		return 1

	return 0


def main():
	test = sys.argv[1]

	if os.path.isdir(test):
		test = os.path.join(test, "project.xml")
	if not os.path.isfile(test):
		raise Exception("Test not found: {}".format(test))

	print "Running test: {}".format(test)
	if run_test(test) == 0:
		print "OK test passed"
	else:
		print "ERROR test failed"

if __name__ == "__main__":
	sys.exit(main())