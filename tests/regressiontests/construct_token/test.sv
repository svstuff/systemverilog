
// Note that the empty arglist is needed here because use of formal parameters in the macro
// text (such as ``V here) will be substituted first, resulting in `BAR64 in this case (if you
// do not use parentheses that is). Using parentheses it becomes `BAR()64, which is something
// else entirely (`BAR64 is the name of a macro which doesn't exist; `BAR()64 is a call to BAR
// followed by the string "64").
`define FOO(V) `BAR()``V
`define BAR 64

`FOO('d0)
