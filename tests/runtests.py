#!/usr/bin/env python3.5

import platform
import sys
import glob
import os
from os import path
from multiprocessing import Pool
import subprocess as sp
import re
from functools import partial
import argparse


# all valid 'checks' - comparisons against expected output
require_choices = [
    'tokens'
]


# compare tokens against a reference file
def _detect_token_mismatch(info, errors, verbose, output, tokensfile):
    # Parse observed
    observed = []
    for line in output.splitlines():
        match = re.search(r'DEBUG_TOKEN:\s+(.*)', line)
        if match:
            tok = match.group(1)
            if not tok.startswith('EOF('):
                observed.append(match.group(1))

    # Parse expected
    expected = [line.strip() for line in tokensfile.readlines() if line.strip()]

    # Compare tokens
    if len(observed) != len(expected):
        errors.add("Not the same number of tokens ({} observed vs {} expected)".format(len(observed), len(expected)))
        return # cop-out
    for a, e in zip(observed, expected):
        if a != e:
            if verbose:
                errors.add("Token mismatch, expected <{}> but got <{}>".format(e, a))
                return # cop-out
    if verbose:
        info.add("Tokens were as expected ({} tokens)".format(len(observed)))
    # done.


# check for antlr warnings
def _detect_antlr_warnings(warnings, output):
    if "reportAmbiguity" in output:
        warnings.add("reportAmbiguity")


# the actual running of the test
def run_inner(verbose, runner, test):
    cmd = [runner, path.join(test, "project.xml")]
    testenv = os.environ.copy()
    testenv['SVPARSE_EXTRA'] = 'svparse_extra_test.xml'
    timeout = 60 # timeout in seconds
    shell = (platform.system() == "Windows") # trial and error got me here..
    try:
        ran = sp.run(cmd, stderr=sp.STDOUT, stdout=sp.PIPE, env=testenv, timeout=timeout, universal_newlines=True, shell=shell)
        return {
            'output': ran.stdout,
            'returncode': ran.returncode
        }
    except sp.TimeoutExpired as e:
        return {
            'output': e.output,
            'returncode': None,
            'timeout': True
        }


# Run the test, collect errors and warnings
# Note: verbose_reset passed here because globals malfunction when using processes
def run_and_check(verbose, runner, require, test):
    if require:
        assert set(require).issubset(require_choices)

    ret = run_inner(verbose, runner, test)

    # Write output to a logfile
    logfile = path.join(test, 'output.log')
    with open(logfile, 'w') as f:
        f.write(ret['output'])

    # Prepare to analyze results
    info = set()
    errors = set()
    warnings = set()

    # Check for timeout
    if 'timeout' in ret:
        errors.add('timeout')

    # Check for non-zero return code
    if ret['returncode'] and ret['returncode'] != 0:
        errors.add('returncode={}'.format(ret['returncode']))

    # Check for ANTLR warnings
    _detect_antlr_warnings(warnings, ret['output'])

    # Check all 'check's (comparison against expected results)
    for check in require_choices:
        filename = path.join(test, check)
        if not path.isfile(filename):
            if check in require:
                warnings.add("Checking-file not found: {}".format(filename))
        elif check == 'tokens':
            _detect_token_mismatch(info, errors, verbose, ret['output'], open(filename, 'r'))
        else:
            assert False

    # return the info about the test
    return {
        'test': test,
        'logfile': logfile,
        'info': info,
        'warnings': warnings,
        'errors': errors
    }


# glob for directories taking a list of glob-patterns
def glob_for_tests(verbose, patterns):
    tests = []
    for pattern in patterns:
        glob_pattern = path.join(pattern, "project.xml")
        pattern_tests = [path.split(f)[0] for f in glob.glob(glob_pattern)]
        print("Found {} tests in '{}'".format(len(pattern_tests), pattern))
        if verbose:
            print("  with glob-pattern '{}'".format(glob_pattern))
            # for test in pattern_tests:
            #     print(" - {}".format(test))
        tests += pattern_tests
    print("Found {} tests in total.".format(len(tests)))
    return tests


# colors for pretty printing
class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'


# glob for tests, run them, print summary, return success or failure
def glob_run_check_report(verbose, runner, patterns, require, should=None):
    # prepare arguments for running all tests
    test_list = glob_for_tests(verbose, patterns)

    # prepare partial function
    func = partial(run_and_check, verbose, runner, require)

    # run tests and count results
    n_total = len(test_list)
    n_warn = 0
    n_pass = 0
    n_fail = 0
    for result in Pool().imap_unordered(func, test_list):
        printme = None
        if result['errors']:
            n_fail += 1
            printme = (bcolors.FAIL, 'FAIL')
        elif result['warnings']:
            n_warn += 1
            printme = (bcolors.WARNING, 'WARN')
        else:
            n_pass += 1
            printme = (bcolors.OKGREEN, 'PASS')

        # vary print depending on whether a 'should' is specified
        if not should:
            print("{}{}{}: {}".format(printme[0], printme[1], bcolors.ENDC, result['test']))
        else:
            print("{}{}{} (should {}): {}".format(printme[0], printme[1], bcolors.ENDC, should, result['test']))

        for line in result['errors']:
            print('- Error:', line)
        for line in result['warnings']:
            print('- Warning:', line)
        for line in result['info']:
            print('- Info:', line)

    # Print summary
    print("\nSummary:")
    print("- PASS: {}".format(n_pass))
    print("- FAIL: {}".format(n_fail))
    print("- WARN: {}".format(n_warn))
    if should:
        print("(should {})".format(should))

    # Decide if we passed overall
    passed = None
    if not should:
        passed = (n_fail == 0)
    elif should == "pass":
        passed = (n_pass == n_total)
    elif should == "warn":
        passed = (n_warn == n_total)
    elif should == "fail":
        passed = (n_fail == n_total)
    else:
        assert False

    # Conclusion
    if passed:
        print("{}PASS{}".format(bcolors.OKGREEN, bcolors.ENDC))
    else:
        print("{}FAIL{}".format(bcolors.FAIL, bcolors.ENDC))
    return passed

# command line runner
def main():
    # Parse arguments
    parser = argparse.ArgumentParser(description='Run tests and report results')
    parser.add_argument("--verbose", help="increase output verbosity",
                        action="store_true")
    parser.add_argument('runner',
                        help='Command to run the parser')
    parser.add_argument('pattern', nargs='+',
                        help='Glob-pattern for tests to run')
    parser.add_argument('--should', choices=['pass', 'warn', 'fail'], nargs='?',
                        help='Specify what tests should do to pass. By default both pass and warn are accepted.')
    parser.add_argument('--require', choices=require_choices, nargs='*',
                        help="Verify that the given type of 'expected results' file is present, otherwise warn")
    args = parser.parse_args()

    # Run tests and grab result
    ret = glob_run_check_report(verbose=args.verbose, runner=args.runner, patterns=args.pattern, require=args.require or [], should=args.should or None)

    # Exit-code
    return 0 if ret else 1


# entry point
if __name__ == "__main__":
    sys.exit(main())
