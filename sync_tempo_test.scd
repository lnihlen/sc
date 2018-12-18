// Run on Server
(
~q = TimeBaseServer.new;
)

// Run on Client
(
~p = TimeBaseClient.new(NetAddr.new("172.16.163.218", TimeBaseServer.defaultOscPort));
)

(
SynthDef("hihat", { arg out = 0, amp = 0.5, att = 0.01, rel = 0.2, ffreq = 6000, pan = 0;
	var env, snd;
	env = Env.perc(att, rel, amp).kr(doneAction: 2);
	snd = WhiteNoise.ar;
	snd = HPF.ar(in: snd, freq: ffreq, mul: env);
	Out.ar(out, Pan2.ar(snd, pan));
}).add;
)

(
~player1 = Pbind(
	\instrument, "hihat",
	\dur, 1,
	\amp, 1
).play(SyncTempoClock.new(~p, 1.0), quant: 4);
)

// Ignore Below

~p.timeDiff;

(
~q = TimeBaseServer.new;
~k = TimeBaseClient.new(
	NetAddr.new("127.0.0.1", TimeBaseServer.defaultOscPort));
)

~k.timeDiff;

(
~c = SyncTempoClock.new(~p, 1.0);
~c.play({ | beats, time, clock |
	[ beats, time, clock ].postln; ^1; });
)

SystemClock.play({|beats, time, clock | [beats, time, clock].postln; 1.0.yield; });

(
Task.new({ "blah".postln; }, SystemClock).start;
)

(
~m = { | beats, time, clock |
	[ beats, time, clock].postln; ^1 };
~d = ~m.awake(Main.elapsedTime - 600, Main.elapsedTime - 600, SystemClock);
d.postln;
)

(
~b = { "magotago".postln; thisThread.clock.postln; ^1; };
~m = LogClock.new(1.0);
~a = {
	var c;
	c = ~b.next(100);
	c.postln;
	^nil;
};
~m.play(~a);
)

~a