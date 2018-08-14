// ============== Global Init
MIDIIn.connectAll;

~mf = MIDIFunc.cc({|value, number, channel, source_id|
	"value: %, number: %, channel: %, source_id: %\n".postf(value, number, channel, source_id);
});
~mf.free;

// ============== PC44 Init
(

// Indices 1-68 represent values of knobs numbered 1-68 respectively,
// maped from zero to one.
~pc44_knob_values = Array.fill(69, {0});
// Value of rotary encoder at top of unit, mapped from zero to one.
~pc44_rotary_value = 0;
// Toggle buttons across the bottom of the unit numbered 1-8, either zero or one.
~pc44_button_values = Array.fill(9, {0});

~pc44_midi_func = MIDIFunc.cc({|value, number, channel, source_id|
	"value: %, number: %, channel: %, source_id: %\n".postf(value, number, channel, source_id);
	if (number <= 68, {  // knobs 1-68
		~pc44_knob_values[number] = value / 127.0;
	}, { if (number == 69, {  // rotary knob at 69
		~pc44_rotary_value = value;
	}, { // buttons 71-78
		~pc44_button_values[number - 70] = value / 127;
	})});
}, srcID: -1157629796);

)

// ============== Cleanup
~pc44_midi_func.free;