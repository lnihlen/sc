SyncTempoClock : Clock {
	var timeBaseClient;
	var <tempo = 1.0;
	var queue;
	var schedulerTask;
	var shouldQuit;

	var <beatsPerBar = 4.0;
	var <barsPerBeat = 0.25;
	var <baseBarBeat = 0.0;
	var <baseBar = 0.0;

	*new { | timeBaseClient, tempo |
		^super.newCopyArgs(timeBaseClient, tempo).init;
	}

	init {
		queue = PriorityQueue.new;

		schedulerTask = Task.new({

		}, SystemClock);
	}

	stop {
	}

	clear {
		queue.clear;
	}

	elapsedBeats {
		^this.secs2beats(this.seconds);
	}

	beats {
		^this.secs2beats(this.seconds);
	}

	seconds {
		^(Main.elapsedTime + timeBaseClient.timeDiff);
	}

	secs2beats { | secs |
		^(secs / tempo);
	}

	play { | task, quant = 1 |
		this.schedAbs(this.nextTimeOnGrid(quant), task);
	}

	sched { | delta, item |
	}

	schedAbs { | beat, item |
	}

	nextTimeOnGrid { | quant = 1, phase = 0 |
		if (quant == 0) { ^this.beats + phase };
		if (quant < 0) { quant = beatsPerBar * quant.neq };
		if (phase < 0) { phase = phase % quant };
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase;
	}
}
