virtual class lower extends uvm_component implements yoyo_uvm_sucks;
  function new (string name, uvm_component parent);
    super.new(name, parent);
  endfunction
  function option (string tulle);
    a = b;
    foo();
    a = b;
  endfunction
  function foo (string bar);

  endfunction
endclass

module top;
  initial begin
  end
endmodule
