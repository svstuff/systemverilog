`ifdef NONEXISTING_DEFINE_1

disabledcodetoken_1
`include "disabled.svh"

`elsif NONEXISTING_DEFINE_2

disabledcodetoken_2
`include "disabled.svh"

`else

someconditionalcodetoken
`include "test.svh"

`endif
