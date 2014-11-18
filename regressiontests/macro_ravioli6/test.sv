`define DEF1(A,B) `DEF2(B,`DEF3(A))
`define DEF2(C,D) (C+D)
`define DEF3(E) (E)

`DEF1(a,b)
