(
SynthDef("drum", { | amp = 0.1, out = 0, freq = 220, attack = 0.01, release = 0.1 |
	var audio, env;
	audio = WhiteNoise.ar();
	audio = Klank.ar(`[
		[1, 2, 3, 4, 5],
		Array.fill(5, {|i| 1.0 / ((i + 1).cubed)}),
		Array.fill(5, {|i| 1.0 / ((i + 1).squared)})
		], audio, freq);
	env = Env.perc(attackTime: attack, releaseTime: release);
	audio = audio * EnvGen.kr(env, doneAction: Done.freeSelf);
	audio = Pan2.ar(audio, 0, amp);
	Out.ar(out, audio);
}).add;

MIDIClient.init;
MIDIIn.connectAll();

~midi_out = MIDIOut(0);

~pattern = Pbind(
	\type, \midi,
	\midiout, ~midi_out,
	\midicmd, \noteOn,
	\chan, 16,
	\scale, Scale.chromatic,
	\octave, 3,
	\root, 0,  // key of C
	\amp, 1.0,
	\degree, Pseq([-1,   0,   \rest, 0,   -1,   0,   \rest, 0,   -1,   0,    \rest, 0,   1,   0,   -1 ], inf),
	\dur,    Pseq([0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,   0.5, 1.0, 0.5, 1.0], inf)
);

~stream = nil;
~start_requested = false;

~midi_latency = 0.2;

~period = nil;

~clock = nil;

~lead_in = 8;

~tap_tempo = TapTempo.new;
~tap_tempo.bang = { | period |
	Synth(\drum);
	~period = period;
	if (~start_requested, {
		Task.new({
			(~period - ~midi_latency).wait;
			~clock = TempoClock(1.0 / ~period);
			~stream = ~pattern.play(~clock);
			~start_requested = false;
		}, SystemClock).play;
	});
};

~pc44 = FaderFoxPC44.new;
~pc44.on_button_change = { | button_number, value |
	switch (button_number,
		1, { ~tap_tempo.reset(); },
		5, { ~start_requested = true; },
		6, { ~stream.stop; },
		// Using the FaderFox UC44, the switches latch so we accept any state change,
		// which switching to proper MIDI momentary switches we will switch code to
		// positive edge changes only.
		8, { ~tap_tempo.tap(); }
	).value;
};
)


