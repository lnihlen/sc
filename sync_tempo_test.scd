(
~q = TimeBaseServer.new;
~k = TimeBaseClient.new(
	NetAddr.new("127.0.0.1", TimeBaseServer.defaultOscPort));
)

~k.timeDiff;

(
~c = SyncTempoClock.new(~k, 1.0);
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
~b = { "magotago".postln; ^1; };
~a = fork {
	var c = ~b.awake(100);
	c.postln;
	^nil;
};
)

~a