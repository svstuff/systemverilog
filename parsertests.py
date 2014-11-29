#!/usr/bin/env python

import sys
import os
import os.path as path
import glob
import subprocess as sp
from multiprocessing import Pool

# TODO: remove this silly script and write the tests in scala/gradle.

def run_test(test):
  cmd = ['./build/install/svparse/bin/svparse', os.path.join(test, "project.xml")]
  testenv = os.environ.copy()
  testenv['SVPARSE_EXTRA'] = 'svparse_extra_test.xml'
  pid = sp.Popen(cmd, stdout=sp.PIPE, stderr=sp.STDOUT, env=testenv)
  rawout, _ = pid.communicate()
  testdir, testname = os.path.split(test)
  output = os.path.join(testdir, '{}.log'.format(testname))
  with open(output, 'w') as f:
    f.write(rawout)
  if pid.returncode != 0:
    return "ERROR: {} - see {}".format(test, output)
  if detected_ambiguous(rawout):
    return "WARNING: ambiguity detected for test {}".format(test)
  return " - {}".format(test)

def detected_ambiguous(testout):
  return "reportAmbiguity" in testout

def main():
  p = Pool(4)
  for result in p.imap_unordered(run_test, [f for f in glob.glob("parsertests/*") if os.path.isdir(f)]):
    print result

if __name__ == "__main__":
  sys.exit(main())
