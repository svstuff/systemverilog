/*             Macro Example
 
 This design demonstrates how to use macros to reduce typing for
 messages.  It includes the macro definitions and writes several
 messages to the screen using ovm_top.
 
 */

`include "defines.svh"
import ovm_pkg::*;

module top;

   initial begin
      `info ("This is my first info message");
      `warning ("This is a warning.  Oooh.  Scary.");
      `error ("This is an error, we are getting close to fatal");
      `debug ("This is an info message with a verbosity of 500");
      `fatal ("We are done.");
   end
endmodule // messages
