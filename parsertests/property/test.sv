property abc(a, b, c);
	disable iff (a==2) @(posedge clk) not (b ##1 c);
endproperty

module foo;

	env_prop: assert property (abc(rst, in1, in2))
		$display("env_prop passed."); else $display("env_prop failed.");

endmodule
