// Impulsive Tom

(
SynthDef("impulsive_tom_drums", { | amp = 0.5 |
	var demand_trigger, kick_sequence, snare_sequence, noise, snare, kick, audio;

	// Sequencing in-synth because tempos exceed the control rate of the interpreter.
	demand_trigger = Impulse.kr(freq: MulAdd.new(LFGauss.kr(20, 0.25), 1000 * 4 / 60, 10 * 4 / 60));
	kick_sequence = Drand([
		Dseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 0, 1,  0, 0, 0, 0], 1),
		Dseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 1, 0,  0, 0, 0, 0], 1),
		Dseq(#[1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 1, 1,  0, 0, 0, 0], 1)
	], inf);

	snare_sequence = Dseq(#[0, 0, 0, 0,  1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 0, 0], inf);

	// Signal processing
	noise = WhiteNoise.ar();

	snare = Klank.ar(`[
		[1, 2, 3, 4, 5],
		[1, 0.125, 0.037037037037037, 0.015625, 0.008], // Array.fill(5, {|i| 1.0 / ((i + 1).cubed)})
		[1, 0.25, 0.11111111111111, 0.0625, 0.04], // Array.fill(5, {|i| 1.0 / ((i + 1).squared)})
		], noise, 220);
	snare = snare * Linen.kr(
		gate: Trig.kr(Demand.kr(demand_trigger, 0, snare_sequence), 0.01),
		attackTime: 0.01,
		releaseTime: 0.1,
		doneAction: 0);

	kick = Klank.ar(`[
		[1, 2, 3, 4, 5],
		[1, 0.125, 0.037037037037037, 0.015625, 0.008], // Array.fill(5, {|i| 1.0 / ((i + 1).cubed)})
		[1, 0.25, 0.11111111111111, 0.0625, 0.04], // Array.fill(5, {|i| 1.0 / ((i + 1).squared)})
		], noise, 60);
	kick = kick * Linen.kr(
		gate: Trig.kr(Demand.kr(demand_trigger, 0, kick_sequence), 0.01),
		attackTime: 0.01,
		releaseTime: 0.1,
		doneAction: 0);

	audio = Mix.new([kick, snare]);
	audio = Pan2.ar(audio, 0, amp);
	audio = FreeVerb.ar(audio);
	Out.ar(0, audio);
}).add;

)

s = Synth(\impulsive_tom_drums);

{ MulAdd.new(LFGauss.ar(1, 0.25), 1, 1) }.plot(duration: 2);
{ LFPulse.ar(Line.kr(100, 800, 0.1)) }.plot(0.1);