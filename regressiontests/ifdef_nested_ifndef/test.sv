`define A
`ifdef A
	this_token_should_be_included_1
	`ifndef A
		`ifndef A
			`ifndef A
				this_token_should_not_be_included_1
			`endif
			this_token_should_not_be_included_2
		`endif
		this_token_should_not_be_included_3
	`endif
	this_token_should_be_included_2
`endif
