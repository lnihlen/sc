(
var cell_rows = 8, cell_history = 2 /* at least 2 */;
var cell_state = Array.fill(cell_history, { Array.fill(cell_rows, { |i| i % 2; }) });
var current_row = 0;
var rule = 110.asBinaryDigits(numDigits: 8);
var update_state = {
	var next_row = (current_row + 1) % cell_history;
	cell_state[next_row] = Array.fill(cell_rows, { | index |
		var rule_index = 0;
		// LSb gets less significant cell value, or down in array toroidally
		rule_index.setBit(0, cell_state[current_row][(index - 1).wrap(0, cell_rows - 1)]);
		// Middle bit gets current cell value
		rule_index.setBit(1, cell_state[current_row][index]);
		// MSb gets more significant cell value, or up in array toroidally
		rule_index.setBit(2, cell_state[current_row][(index + 1) % cell_rows]);
		rule_index.postln;
		^rule[rule_index];
	});
	current_row = next_row;
};

cell_state.postln;
update_state.value;
cell_state.postln;
update_state.value;

)
