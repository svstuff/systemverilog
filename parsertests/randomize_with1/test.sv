task test();
  if (!testarray[i].randomize() with {
    testarray[i].something == local::something;
    testarray[i].something2  == ((local::something2) && (i == testarray.size() - 1));
    testarray[i].something3 == local::something3;
  }) begin
  end
endtask : test
