#!/usr/bin/env python3

import sys
import os
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
    cmd = ['./build/install/bin/svparse', os.path.join(test, "project.xml")]
    testenv = os.environ.copy()
    testenv['SVPARSE_EXTRA'] = 'svparse_extra_test.xml'
    pid = sp.Popen(cmd, stdout=sp.PIPE, stderr=sp.STDOUT, env=testenv)
    rawout, _ = pid.communicate()
    testdir, testname = os.path.split(test)
    testout = os.path.join(testdir, '{}.log'.format(testname))
    with open(testout, 'w') as f:
        f.write(rawout.decode())
    if pid.returncode != 0:
        return Result(test, testout, 'FAIL')
    if detected_antlr_warnings(rawout.decode()):
        return Result(test, testout, 'WARN')
    return Result(test, testout, 'PASS')


def detected_antlr_warnings(testout):
    return "reportAmbiguity" in testout


def main():
    n_total = 0
    n_pass = 0
    n_fail = 0
    p = Pool(4)
    test_list = [f for f in glob.glob("parsertests/*") if os.path.isdir(f)]
    for result in p.imap_unordered(run_test, test_list):
        n_total += 1
        status = statuscolor[result.status] + result.status + bcolors.ENDC
        if result.status != 'PASS':
            if result.status == 'FAIL':
                n_fail += 1
            print("{}: {} - {}".format(status, result.testname, result.testout))
        else:
            n_pass += 1
            print("{}: {}".format(status, result.testname))

    print("Summary:")
    print("- PASS: {}".format(n_pass))
    print("- FAIL: {}".format(n_fail))
    print("- WARN: {}".format(n_total - n_fail - n_pass))
    if n_fail == 0:
        return 0
    return 1

if __name__ == "__main__":
    sys.exit(main())
