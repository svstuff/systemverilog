#!/usr/bin/env python

import sys

nesting = 0

for line in sys.stdin:
  line = line.strip()
  words = line.split()
  if not len(words) > 1:
    print line
    continue

  if words[0] not in ('enter', 'exit', 'consume'):
    print line
    continue

  if words[0] == 'exit':
    nesting -= 1
    continue

  if words[0] == 'enter':
    nesting += 1
    print '| ' * nesting, ' '.join(words[1:])
  else:
    print '| ' * nesting, '+', ' '.join(words[0:])
