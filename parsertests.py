#!/usr/bin/env python

import sys
import os
import os.path as path
import glob
import subprocess as sp
from collections import namedtuple
from multiprocessing import Pool

# TODO: remove this silly script and write the tests in scala/gradle.

class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'

statuscolor = {
  'PASS': bcolors.OKGREEN,
  'WARN': bcolors.WARNING,
  'FAIL': bcolors.FAIL,
}

Result = namedtuple('Result', ['testname', 'testout', 'status'])

def run_test(test):
  cmd = ['./build/install/svparse/bin/svparse', os.path.join(test, "project.xml")]
  testenv = os.environ.copy()
  testenv['SVPARSE_EXTRA'] = 'svparse_extra_test.xml'
  pid = sp.Popen(cmd, stdout=sp.PIPE, stderr=sp.STDOUT, env=testenv)
  rawout, _ = pid.communicate()
  testdir, testname = os.path.split(test)
  testout = os.path.join(testdir, '{}.log'.format(testname))
  with open(testout, 'w') as f:
    f.write(rawout)
  if pid.returncode != 0:
    return Result(test, testout, 'FAIL')
  if detected_antlr_warnings(rawout):
    return Result(test, testout, 'WARN')
  return Result(test, testout, 'PASS')

def detected_antlr_warnings(testout):
  return "reportAmbiguity" in testout

def main():
  p = Pool(4)
  for result in p.imap_unordered(run_test, [f for f in glob.glob("parsertests/*") if os.path.isdir(f)]):
    status = statuscolor[result.status] + result.status + bcolors.ENDC
    if result.status != 'PASS':
      print "{}: {} - {}".format(status, result.testname, result.testout)
    else:
      print "{}: {}".format(status, result.testname)

if __name__ == "__main__":
  sys.exit(main())
