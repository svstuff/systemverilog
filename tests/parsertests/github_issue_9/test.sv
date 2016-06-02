module MyMod(input op1, input op2, input cin, output sum, output cout);
   assign {cout, sum} = op1 + op2 + cin;
endmodule
