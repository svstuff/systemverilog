#!/usr/bin/env python
from __future__ import print_function
import sys

nesting = 0

for line in sys.stdin:
    line = line.strip()
    words = line.split()
    if not len(words) > 1:
        print(line)
        continue

    if words[0] not in ('enter', 'exit', 'consume'):
        print(line)
        continue

    if words[0] == 'exit':
        nesting -= 1
        continue

    if words[0] == 'enter':
        print('|  ' * nesting, ' '.join(words[1:]), sep='')
        nesting += 1
    else:
        print('|  ' * (nesting - 1), '+  ', ' '.join(words[0:]), sep='')
