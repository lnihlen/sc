(
MIDIIn.connectAll;
~midi_port = MIDIIn.findPort("USB MIDI Interface", "USB MIDI Interface");
~cc_midi_func = MIDIFunc.cc({ | value, number, channel, source_id |
	"* cc value: %, number: %, channel: %, source_id: %\n".postf(value, number, channel, source_id);
}, srcID: ~midi_port.uid);

~program_midi_func = MIDIFunc.program({ | value, number, channel, source_id |
	"* program value: %, number: %, channel: %, source_id: %\n".postf(value, number, channel, source_id);
}, srcID: ~midi_port.uid);
)

(
~cc_midi_func.free;
~program_midi_func.free;
)

// We use the FC-300 in CONTROL mode, the second on the list of modes on the
// device after STANDARD, selected by the MODE button on the right. In this
// mode the factory default map of the controller is:
//
// |   Label     |  number  |
// +-------------+----------+
// | MODE DOWN   | not sent |
// | MODE UP     | not sent |
// | CTL 1       | 80       |
// | CTL 2       | 81       |
// | 1/6         | 65       |
// | 2/7         | 66       |
// | 3/8         | 67       |
// | 4/9         | 68       |
// | 5/10        | 69       |
// | EXP PEDAL 1 | 7        |
// | EXP PEDAL 2 | 1        |
// +-------------+----------+

