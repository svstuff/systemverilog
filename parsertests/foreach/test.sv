
`define YO foreach(foo[j]) blah

task clazz::func(string name);
   `YO;
  foreach (env[i])
    if (env[i] == name)
      return ;
endtask
