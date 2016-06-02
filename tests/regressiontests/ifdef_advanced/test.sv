`define OUTER 1

`ifdef OUTER

`define INNER 1

`ifdef BLAH

æøå

`elsif INNER

inner_id

`endif // INNER

after_inner_id

`endif // OUTER

outermost
