function int func;
   // NOTE: LRM grammar is wrong. randomize cannot take a variable_identifier_list,
   // or the example on page 101 of the 2012 LRM is wrong.
   a.randomize(foo.a, bar.b);
   this.randomize(foo.a, bar.b);
endfunction
