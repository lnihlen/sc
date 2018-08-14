SynthDef("shalabi_drone", {
	arg out = 0, gate = 1, attack = 5.0, dur = 2.0, release = 5.0, amp = 0.7, freq = 73,
	impulse_rate = 7.0, impulse_attack = 0.05, impulse_release = 0.5;

	var audio, trigger, perc, noise_perc, octave_overtones = 3, fifth_overtones = 3,
	flat_third_overtones = 3, flat_seven_overtones = 3;

	trigger = Impulse.ar(impulse_rate);
	perc = EnvGen.ar(Env.perc(attackTime: impulse_attack, releaseTime: impulse_release), trigger);
	// note that noise_perc fades off faster
	noise_perc = EnvGen.ar(Env.perc(attackTime: impulse_attack, releaseTime: impulse_attack), trigger);

	audio = DynKlang.ar(`[
		// frequencies
		Array.fill(octave_overtones, { | i | 2**i }) ++
		Array.fill(fifth_overtones, { | i | (2**i) * 1.498356394771042}) ++
		Array.fill(flat_third_overtones, { | i | (2**i) * 1.189205718217262}) ++
		Array.fill(flat_seven_overtones, { | i | (2**i) * 1.78180602006689}),

		// amplitudes
		Array.fill(octave_overtones, { | i | 1.0 / (3**(i + 1))}) ++
		Array.fill(fifth_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}) ++
			Array.fill(flat_third_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}) ++
		Array.fill(flat_seven_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}),

		// phases
		Array.fill(octave_overtones + fifth_overtones + flat_third_overtones + flat_seven_overtones,
			{ | i | TRand.ar(0, 2pi, trigger) * perc; })],
	freqscale: freq * (1.0 + (TRand.ar(-0.01, 0.01, trigger) * noise_perc)));

	audio = LPF.ar(audio, freq * (1.0 + perc));

	audio = LinXFade2.ar(audio, PinkNoise.ar, -1.0 + (noise_perc * 0.02), amp);

	audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: Done.freeSelf);

	Out.ar(out, Pan2.ar(audio));
}).add;

m = Synth("shalabi_drone", [
	\freq, 38.midicps
]);

m.set(\impulse_rate, 7);
m.set(\impulse_attack, 0.04);
m.set(\impulse_release, 0.7);

q.free; r.free; m.free;