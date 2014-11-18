

module adder(a, b, cin, cout, s);
	input a, b, cin;
	output cout, s;
	assign s = a ^ b ^ cin;
	assign cout = (a & b) | (a & cin) | (b & cin);
endmodule : adder

