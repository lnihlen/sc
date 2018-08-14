(
SynthDef("impulse_gated_static", {
	var audio, env;

	env = Clip.ar(
		LPF.ar(
			Integrator.ar(
				Dust.ar(
					TRand.kr(10.0, 100.0, Dust.kr(0.5))),
				0.999),
			100),
		0.0, 1.0);
	audio = env * GrayNoise.ar();

	Out.ar(0, Pan2.ar(audio));
}).add;

~q = Synth(\impulse_gated_static);

)