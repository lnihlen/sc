// 2019-01-04 - first attempt, not really anywhere near what I'm hearing in my head.
//
// 2019-01-05 - Continued work. Thinking of two sounds for inspiration, the first is
// the sound of steam escaping wet firewood when burning, the second is packing
// styrofoam compressing or rubbing against other styrofoam pieces.

// Neurons - This is a sound I hear sometimes in my head when it is very very quiet.
// I have come to think, for no reason, that it is the sound of neurons firing in
// my nervous system.

(
{
	var env =
	Env.new(
		levels: [ 0, 1, 1, 0],
		times: [0.01, 0.01, 0.01, 0.01, 0.01],
		curve: \sine
	);
	var trigger = Impulse.ar(
		LFGauss.kr(duration: 8, width: 0.45) * 15
	);
	var envgen = EnvGen.ar(env, trigger);
	var pitch = (LFGauss.kr(duration: 8, width: 0.75, doneAction: 2) * 180.0) + 30.0;
	var sound = SinOsc.ar(pitch) * envgen;
	sound = BPF.ar(sound, 1000.0, mul: 0.5);
	Pan2.ar(sound, 0.0, 1.0);
}.play;
)
