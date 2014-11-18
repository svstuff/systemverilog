`define DEF1(A) `DEF2(1+A)
`define DEF2(B) `DEF3(Z,B)
`define DEF3(C,D) `DEF4(`DEF5(C)),D
`define DEF4(E) E
`define DEF5(F) F

`DEF1(a)
