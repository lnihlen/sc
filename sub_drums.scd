// Subtractive Synthesis Drum Machine


(
SynthDef("impulse_tom", {| freq = 110, amp = 1.0, impulse_freq = 1 |
		var audio;
		audio = WhiteNoise.ar();
	    audio = Klank.ar(`[
		[1, 2, 3, 4, 5],
		[1, 0.125, 0.037037037037037, 0.015625, 0.008], // Array.fill(5, {|i| 1.0 / ((i + 1).cubed)})
		[1, 0.25, 0.11111111111111, 0.0625, 0.04], // Array.fill(5, {|i| 1.0 / ((i + 1).squared)})
		], audio, freq);
	    // Deliberately persist this synth so it can continue to play itself.
	    audio = audio * Linen.kr(gate: Impulse.kr(impulse_freq), attackTime: 0.01, releaseTime: 0.1);
		audio = Pan2.ar(audio, 0, amp);
		Out.ar(0, audio);
	}
).add

)

Synth(\impulse_tom, [\freq, 220, \impulse_freq, 1 / 5])

(

SynthDef("tom",
	{
		arg gate = 1, freq = 110, amp = 1.0;
		var audio, frequencies, amplitudes, ring_times, overtones = 5;
		frequencies = Array.fill(overtones, {|i| (i + 1) * freq});
		amplitudes = Array.fill(overtones, {|i| amp / ((i + 1).cubed)});
		ring_times = Array.fill(overtones, {|i| 1.0 / ((i + 1).squared)});
		audio = WhiteNoise.ar();
		audio = Klank.ar(`[frequencies, amplitudes, ring_times], audio);
		audio = audio * Linen.kr(gate: gate, attackTime: 0.01, releaseTime: 0.1, doneAction: 2);
		audio = Pan2.ar(audio, 0);
		Out.ar(0, audio);
	}
).add;

SynthDef("static_tom", {| gate = 1, freq = 110, amp = 1.0 |
		var audio;
		audio = WhiteNoise.ar();
	    audio = Klank.ar(`[
		[1, 2, 3, 4, 5],
		[1, 0.125, 0.037037037037037, 0.015625, 0.008], // Array.fill(5, {|i| 1.0 / ((i + 1).cubed)})
		[1, 0.25, 0.11111111111111, 0.0625, 0.04], // Array.fill(5, {|i| 1.0 / ((i + 1).squared)})
		], audio, freq);
		audio = audio * Linen.kr(gate: gate, attackTime: 0.01, releaseTime: 0.1, doneAction: 2);
		audio = Pan2.ar(audio, 0, amp);
		Out.ar(0, audio);
	}
).add

)

/* Note from Control help shows how to pass arrays to synths:

(
SynthDef(\help_Control, { arg out=0, i_freq;
    var klank, harm, amp, ring;

    // harmonics
    harm = Control.names([\harm]).ir(Array.series(4, 1, 1).postln);
    // amplitudes
    amp = Control.names([\amp]).ir(Array.fill(4, 0.05));
    // ring times
    ring = Control.names([\ring]).ir(Array.fill(4, 1));

    klank = Klank.ar(`[harm, amp, ring], { ClipNoise.ar(0.01) }.dup, i_freq);

    Out.ar(out, klank);
}).add;
)

a = Synth(\help_Control, [\i_freq, 300, \harm, [1, 3.3, 4.5, 7.8]]);
a.free;
a = Synth(\help_Control, [\i_freq, 300, \harm, [2, 3, 4, 5]]);
a.free;

*/


(
var snare, kick, clock, skew;

kick = Pbind(
	\instrument, \static_tom,
	\sustain, 0.02,
	\freq, 60,
	\amp, Prand(
		[Pseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 0, 1,  0, 0, 0, 0], 1),
		 Pseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 1, 0,  0, 0, 0, 0], 1),
		 Pseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 1, 1,  0, 0, 0, 0], 1),
	], inf)
);

snare = Pbind(
	\instrument, \static_tom,
	\sustain, 0.01,
	\freq, 220,
	\amp, Pseq(#[0, 0, 0, 0,  1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 0, 0], inf)
);

skew = Pbind(
	\tempo, Pseq([Pseq((40..220) * 4 / 60, 1), Pseq((220..40) * 4 / 60, 1)], inf)
);

clock = TempoClock((220 * 4) / 60);

Ppar([kick, snare]).play(clock);

)
