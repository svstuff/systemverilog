`define FOO( a= ) a+1
`define BAR( a=, b=2 ) a+b
`FOO()
`FOO(0)
`BAR(,)
`BAR(,1)
`BAR(1,)
`BAR(3,4)
