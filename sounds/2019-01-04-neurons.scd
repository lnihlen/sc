// Neurons - This is a sound I hear sometimes in my head when it is very very quiet.
// I have come to think, for no reason, that it is the sound of neurons firing in
// my nervous system.
//
// ---++ Devlog:
//
// 2019-01-04 - first attempt, not really anywhere near what I'm hearing in my head.
//
// 2019-01-05 - Continued work. Thinking of two sounds for inspiration, the first is
// the sound of steam escaping wet firewood when burning, the second is packing
// styrofoam compressing or rubbing against other styrofoam pieces.
//
// 2019-01-06 - The squeakies described above may be a different sound, which plays
// in concert with this one. Conversion into a paramaterized synth, tweaking duration,
// added some additional color to the pitch.
//
(
SynthDef(\Neurons, {
	arg dur = 16.0,
	amp = 0.5,
	out = 0,
	freq = 210.0,          // At least 30.0
	freqModPhase = 0.0;    // [0.0..1.0]

	// Envelope for each individual pulse.
	var env = Env.new(
		levels: [ 0, 1, 1, 0],
		times: [0.01, 0.01, 0.01, 0.01, 0.01],
		curve: \sine
	);

	// Curve describing the pulse trigger rate.
	var rateCurve = Env.new(
		levels: [0.1, 1.0, 0.1 ],
		times: [0.5, 0.5],
		curve: \exp
	);
	var rateEnvelope = EnvGen.ar(rateCurve,
		timeScale: dur,
		doneAction: 2);

	// Triggers individual pulses.
	var trigger = Impulse.ar(rateEnvelope * 15.0);

	//  Curve controlling the pitch from baseline at 30 up to \freq.
	var pitch = (LFGauss.kr(duration: dur,
		width: 0.75,
		iphase: freqModPhase) * (freq - 30.0)) + 30.0;

	// Base sound is multiple SinOscs at half-multiples of pitch.
	var sound = Klang.ar(`[
		[0.5, 1.0, 2.0, 3.0, 4.0],
		[1.0, 4.0, 1.0, 3.0, 1.0],
		[0.0, pi / 2.0, pi, 3pi / 2.0, 2pi]], freqscale: pitch) * EnvGen.ar(env, trigger);

	// Modulate bandwidth of filter to broaden as rate increases.
	sound = Resonz.ar(sound, freq: 1500.0, bwr: rateEnvelope, mul: amp);

	Out.ar(out, sound);
}).add;
)

(
~neuron = Synth.new(\Neurons, [
	\dur, rrand(8.0, 24.0),
	\amp, 0.8,
	\freq, rrand(100.0, 300.0),
	\freqModPhase, rrand(0.0, 1.0)
]);
)