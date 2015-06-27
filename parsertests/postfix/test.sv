task foo;
   
   bar++;
   mmu_request_trans.req_ptid = next_ptid[mmu_request_trans.req_utlbid]++;

endtask
