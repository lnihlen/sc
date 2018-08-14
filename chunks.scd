(
SynthDef("drum", { | amp = 0.5, out = 0, freq = 220, attack = 0.01, release = 0.1 |
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

SynthDef("hat", { | amp = 0.5, out = 0, attack = 0.01, release = 0.1 |
	var audio, env;
	audio = WhiteNoise.ar();
    env = Env.perc(attackTime: attack, releaseTime: release);
	audio = audio * EnvGen.kr(env, doneAction: 2);
	audio = Pan2.ar(audio, 0, amp);
	Out.ar(out, audio);
}).add;

SynthDef("wob", { | trigger_rate = 100.0, gate = 1, grain_duration = 0.1, freq = 220, dur = 1.0, amp = 1.0 |
	var audio, env;
	audio = GrainFM.ar(
		numChannels: 2,
		trigger: Dust.ar(trigger_rate),
		dur: grain_duration,
		carfreq: freq,
		modfreq: SinOsc.ar(freq: 2.0, add: freq, mul: (freq / 100.0)),
		mul: amp
	);
	env = Env.asr(attackTime: 0.1, releaseTime: 0.1);
	env.duration = dur;
	audio = audio * EnvGen.kr(env, gate: gate, doneAction: Done.freeSelf);
	Out.ar(0, audio);
}).add;

SynthDef("form", { | freq = 220, amp = 1.0, gate = 1, dur = 1.0 |
	var audio, env;
	audio = Formant.ar(
		fundfreq: freq,
		formfreq: freq * 2,
		bwfreq: freq * 4,
	);
	env = Env.asr(attackTime: 0.1, releaseTime: 0.1);
	env.duration = dur;
	audio = audio * EnvGen.kr(env, gate: gate, doneAction: Done.freeSelf);
	audio = Pan2.ar(audio, 0, amp);
	Out.ar(0, audio);
}).add;

SynthDef("freeer", { | freq = 220, amp = 1.0, mod_osc = 0.05, pm_osc = 0.05 |
	var audio;
	audio = PMOsc.ar(
		carfreq: freq / 2,
		modfreq: SinOsc.kr(mod_osc, Rand(0, 2pi), mul: freq / 12.0, add: freq),
		pmindex: SinOsc.kr(SinOsc.kr(pm_osc, Rand(0, 2pi), 4, 5), 0, mul: pi / 2.0, add: pi),
		mul: amp);
	audio = Pan2.ar(audio, 0);
	Out.ar(0, audio);
}).add;
)

(
MIDIIn.connectAll(false);

~snare = 220;
~kick = 40;
~quiet = 0.2;
~loud = 0.6;
~bass_amp = ~loud;
~quarter = 1.0;
~eigth = ~quarter / 2.0;
~sixteenth = ~eigth / 2.0;
~ksl = ~loud;
~ksq = ~quiet;
~hl = ~loud;
~hq = ~quiet;
~chords_amp = ~loud;
~minor = Scale.minorPentatonic;
~major = Scale.majorPentatonic;

~i = 0;
~iv = 3;
~v = 4;

~roots = Pseq([ ~i,     ~v,     ~i,     ~iv,    ~i], inf);
~scales = Pseq([~minor, ~major, ~minor, ~minor, ~minor], inf);

~bass = Pbind(
	\instrument, \form,
	\dur, 1,
	\degree, Pxrand([0, 2, 4, 6], inf),
	\root, Pstutter(4, ~roots),
	\scale, Pstutter(4, ~scales),
	\octave, 3,
	\amp, Pfunc({ ~bass_amp; }),
);

~kick_snare = Pbind(
	\instrument, \drum,
	\freq, Pseq([~kick,       ~kick,      \rest,       ~kick,      ~snare,     ~kick,      ~kick,      ~kick,      ~kick,      ~snare], inf),
	\dur,  Pseq([~sixteenth,  ~sixteenth, ~sixteenth,  ~sixteenth, ~eigth,     ~sixteenth, ~eigth,     ~eigth,     ~sixteenth, ~quarter], inf),
	\amp, Prout({{~ksq.yield; ~ksq.yield; \rest.yield; ~ksq.yield; ~ksl.yield; ~ksq.yield; ~ksq.yield; ~ksq.yield; ~ksq.yield; ~ksl.yield;}.loop}),
	\attack, 0.01,
	\release, 0.1
);

~hats = Pbind(
	\instrument, \hat,
	\dur, ~sixteenth,
	\amp, Prout({{~hl.yield; ~hq.yield;}.loop}),
	\attack, 0.01,
	\release, Prand([0.05, 0.1, 0.15, 0.2, 0.25], inf);
);

~chords = Pbind(
	\instrument, \wob,
	\scale, ~scales,
	\root,  ~roots,
	\degree, [0, 2, 4, 6],
	\dur, 4,
	\octave, 4,
	\strum, 0.3,
	\strumEndsTogether, true,
	\amp, Pfunc({~chords_amp}),
);


~k = (
	\instrument: \freeer,
	\amp: 1.0,
	\octave: 4,
	\degree: 0
).play;

~pc44 = FaderFoxPC44.new;
~pc44.on_button_change = { | button_number, value |
/*
	switch (button_number,
			1, { if (value == 1, { ~drone_mixer.play; }, { ~drone_mixer.pause; }); }
	).value;
*/
};

~pc44.on_knob_change = { | knob_number, value |
	switch (knob_number,
		1,  { ~bass_amp = value; },
		2,  { ~ksl = value; ~ksq = ~ksl / 2.0; },
		3,  { ~hl = value; ~hq = ~hl / 2.0; },
		4,  { ~chords_amp = value; },
		65, { ~k.synth.set(\mod_osc, 0.05 + (value * 0.2)); },
		66, { ~k.synth.set(\pm_osc, 0.05 + (value * 0.2)); },
		67, { ~k.synth.set(\freq, 20.0 + (value * 500.0)); },
		68, { ~k.synth.set(\amp, value); }
	).value;
};

~clock = TempoClock(90.0 / 60.0);
Ppar([~kick_snare, ~hats, ~chords, ~bass]).play(~clock);
)