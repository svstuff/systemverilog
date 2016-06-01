#0
#42ns
1step
123
123+456 // should of course become three tokens
0.1
0.1e-42
' b10  // note: this should become two tokens, one ' and one ID
'sb0000_1111
8'sb0000_1111
'0
'1
'z
id
_id
id$
$id
"string"
"string with \"escaped\" quotes"
"string with quote\""
"string with \a bell"
"string\twith\ttab"
"string\fwith\fform feed"
"string with\nnewline"
"string with\vvertical tab"
64'(123)
64' (123)
1.2e-42'(123)
123ns'(123)
