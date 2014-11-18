`define YO 1

`ifdef NONEXISTING_DEFINE_1

disabledcodetoken_1
`include "disabled.svh"

`elsif YO

someconditionalcodetoken
`include "test.svh"

`else

disabledcodetoken_2
`include "disabled.svh"

`endif
