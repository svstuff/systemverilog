function void build_phase();
  uvm_config_db#(apb_vif)::get();
  uvm_config_db#(std::foo)::get();
  uvm_config_db#(string)::get();
endfunction
