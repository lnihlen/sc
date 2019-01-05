// 2019-01-04 - first attempt, not really anywhere near what I'm hearing in my head.

// Neurons - This is a sound I hear sometimes in my head when it is very very quiet.
// I have come to think, for no reason, that it is the sound of neurons firing in
// my nervous system.

{ XLine.kr(start: 1.0, end: 2.0, dur: 1.0); }.plot(1.0);

(
{
	var env = Env.perc(
		attackTime: 0.01,
		releaseTime: 0.5,
		level: 1.0,
		curve: -4.0);
	var trigger = Impulse.ar(
		XLine.kr(1.5, 10, 8, doneAction: 2);
	);
	var envgen = EnvGen.ar(env, trigger);

	Pan2.ar(Saw.ar(
		XLine.kr(50.0, 440.0, 8.0, doneAction: 2)
	) * envgen, 0.0, 1.0);
}.play;
)

Quarks.gui;

(
SynthDef(\bubblebub, {	|out=0, t_trig=0, attack=0.01, decay=0.08, pitchcurvelen=0.1, freq=1000, doneAction=0, amp=0.1|
	var pitch, son;
	amp   = amp * EnvGen.ar(Env.perc(attack, decay).delay(0.003), t_trig, doneAction: doneAction);
	pitch = freq * EnvGen.ar(Env.new([0,0,1],[0,1]).exprange(1, 2.718), t_trig, timeScale: pitchcurvelen);
	son = SinOsc.ar(pitch);
	// high-pass to remove any lowpitched artifacts, scale amplitude
	son = HPF.ar(son, 500) * amp * 10;
	Out.ar(out, son);
}).store
)