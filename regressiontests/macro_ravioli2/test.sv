`define DEF1(A) `DEF2(A)
`define DEF2(B) `DEF3(B,Z)
`define DEF3(C,D) C D

`DEF1(a)
