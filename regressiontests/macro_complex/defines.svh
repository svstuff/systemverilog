`define YO__FILE__ "pretendfile"
`define YO__LINE__ 42

`define info(msg)    ovm_top.ovm_report_info($psprintf("%m"),msg);
`define warning(msg) ovm_top.ovm_report_warning($psprintf("%m"),msg);
`define error(msg)   ovm_top.ovm_report_error($psprintf("%m"),msg,,`YO__FILE__, `YO__LINE__);
`define fatal(msg)   ovm_top.ovm_report_fatal($psprintf("%m"),msg,,`YO__FILE__, `YO__LINE__);
`define debug(msg)   ovm_top.ovm_report_info($psprintf("%m"),msg,500,`YO__FILE__, `YO__LINE__);