(
MIDIClient.init;
~midi_out = MIDIOut.newByName("Moog Minitaur", "Moog Minitaur MIDI 1").connect;
~midi_in = MIDIIn.findPort("USB MIDI Interface", "USB MIDI Interface MIDI 1");

~pattern = 	Pbind(
	\scale, Scale.chromatic,
	\octave, 3,
	\root, 0,  // key of C
	\amp, 1.0,
	\degree, Pseq([-1,   0,    0,   -1,   0,    0,   -1,   0,    0,   1,   0,   -1 ], inf),
	\dur,    Pseq([0.25, 0.75, 1.0, 0.25, 0.75, 1.0, 0.25, 0.75, 0.5, 1.0, 0.5, 1.0], inf)
);
~stream = ~pattern.asStream;
~player = TapStreamPlayer.new(~stream);

~fc_300_midi_func = MIDIFunc.cc({ | value, number, channel, source_id |
	"foot pedal".postln;
	switch (number,
		80, { },    // CTL 1
		81, { },    // CTL 2
		65, { },    // 1/6
		66, { },    // 2/7
		67, { },    // 3/8
		68, { },    // 4/9
		69, { ~player.tap(); },  // 5/10
		7, { /* set cue volume */ },
		1, { /* set mains volume */ }).value;
}, srcID: ~midi_in.uid);

~win = Window("Tap Stream Player", Rect(128, 128, 34, 34)).front;
~button = Button.new(~win, Rect(2, 2, 32, 32));
~button.string = "tap";
~button.action = { ~player.tap(); };

~playing = false;
~player.period_changed = { | p, s |
	if (~playing.not, {
		~player.start((
			\type: \midi,
			\midiout: ~midi_out,
			\midicmd: \noteOn,
			\chan: 16));
		~playing = true;
		"started".postln;
	});
};
)

~midi_out.allNotesOff(0);

(
Pbind(
	\type, \midi,
	\midiout, ~midi_out,
	\midicmd, \noteOn,
	\chan, 0,  // note that these seem to come out on channel 1
	\scale, Scale.chromatic,
	\octave, 3,
	\root, 0,  // key of C
	\amp, 1.0,
	\degree, Pseq([-1,   0,    0,   -1,   0,    0,   -1,   0,    0,   1,   0,   -1 ], inf),
	\dur,    Pseq([0.25, 0.75, 1.0, 0.25, 0.75, 1.0, 0.25, 0.75, 0.5, 1.0, 0.5, 1.0], inf)
).trace.play();
)