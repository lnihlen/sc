SyncTempoClock : Clock {
	var timeBaseClient;
	var <tempo = 1.0;
	var scheduler;

	var <beatsPerBar = 4.0;
	var <barsPerBeat = 0.25;
	var <baseBarBeat = 0.0;
	var <baseBar = 0.0;

	*new { | timeBaseClient, tempo |
		^super.newCopyArgs(timeBaseClient, tempo).init;
	}

	init {
		scheduler = Scheduler.new(this, drift: false, recursive: false);
	}

	tick {
		var saveClock = thisThread.clock;
		thisThread.clock = this;
		scheduler.seconds = this.seconds;
		thisThread.clock = saveClock;

		// Also scheduler is thinking in units of beats I think
		if (scheduler.isEmpty, {
			"tick empty".postln;
			^nil;
		}, {
			"tick next: %".format(scheduler.queue.topPriority - timeBaseClient.timeDiff).postln;
			^(scheduler.queue.topPriority - timeBaseClient.timeDiff)
		});
	}

	clear {
		scheduler.clear;
	}

	elapsedBeats {
		^this.secs2beats(this.seconds);
	}

	beats {
		^this.secs2beats(this.seconds);
	}

	seconds {
		^(thisThread.seconds + timeBaseClient.timeDiff);
	}

	secs2beats { | secs |
		^(secs / tempo);
	}

	play { | task, quant = 1 |
		this.schedAbs(this.nextTimeOnGrid(quant), task);
	}

	sched { | delta, item |
		var wasEmpty = scheduler.isEmpty;
		scheduler.sched(delta, item);
		if (wasEmpty, { SystemClock.play({ this.tick; }); });
	}

	schedAbs { | beat, item |
		var wasEmpty = scheduler.isEmpty;
		scheduler.schedAbs(beat, item);
		if (wasEmpty, { SystemClock.play({ this.tick; }); });
	}

	nextTimeOnGrid { | quant = 1, phase = 0 |
		if (quant == 0) { ^this.beats + phase };
		if (quant < 0) { quant = beatsPerBar * quant.neq };
		if (phase < 0) { phase = phase % quant };
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase;
	}
}
