#!/usr/bin/env python

"""

Simulator shim.

Can be used as a standin for a popular system verilog simulator.
Creates a project XML file based on the command line parameters.

TODO:
- use the "-o blah/build-xyz/tb" option to decide where to write the xml file

"""

import os
import sys

def parse(xmlfile, arglist):
  args = iter(arglist)
  for arg in args:
    arg = arg.strip()
    if not arg:
      continue
    if arg.startswith('+incdir+'):
      incdir = p(arg[len('+incdir+'):])
      xmlfile.write('  <incdir>{}</incdir>\n'.format(incdir))
    elif arg.startswith('+define+'):
      name, _, value = arg[len('+define+'):].partition('=')
      xmlfile.write('  <define><name>{}</name><value>{}</value></define>\n'.format(name, value))
    elif arg == '-y':
      path = p(next(args))
      xmlfile.write('  <moddir>{}</moddir>\n'.format(path))
    elif arg == '-f':
      filelist = p(next(args))
      xmlfile.write('<!-- start: {} -->\n'.format(filelist))
      with open(filelist) as f:
        parse(xmlfile, f)
      xmlfile.write('<!-- end: {} -->\n'.format(filelist))
    elif arg.endswith('.sv') or arg.endswith('.v'):
      # yes, this is fragile, since someone might not call their verilog files .v. Meh.
      arg = p(arg)
      if os.path.isfile(arg):
        xmlfile.write('  <source>{}</source>\n'.format(arg))
      else:
        print "Ignoring non-file option: {}".format(arg)
    else:
      print "Ignoring unknown option: {}".format(arg)

def p(path):
  return os.path.abspath(os.path.expandvars(path.strip()))

with open('stylecheck.xml', 'w') as f:
  f.write('<project>\n')
  parse(f, sys.argv[1:])
  f.write('</project>\n')
