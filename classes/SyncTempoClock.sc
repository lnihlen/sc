LogClock : TempoClock {
	*new { | tempo, beats, seconds, queueSize=256 |
		"LogClock::new".postln;
		^super.new.init(tempo, beats, seconds, queueSize);
	}
	init { | tempo, beats, seconds, queueSize |
		"LogClock::init".postln;
		super.init(tempo, beats, seconds, queueSize);
	}
	stop {
		"LogClock::stop".postln;
		super.stop;
	}
	play { | task, quant = 1 |
		"LogClock::play".postln;
		super.play(task, quant);
	}
	playNextBar { | task |
		"LogClock::playNextBar".postln;
		super.playNextBar(task);
	}
	tempo {
		"LogClock::tempo".postln;
		^super.tempo;
	}
	beatDur {
		"LogClock::beatDur".postln;
		^super.beatDur;
	}
	elapsedBeats {
		"LogClock::elapsedBeats".postln;
		^super.elapsedBeats;
	}
	beats {
		"LogClock::beats".postln;
		^super.beats;
	}
	beats_ { | beats |
		"LogClock::beats_".postln;
		^super.beats_ = beats;
	}
	seconds {
		"LogClock::seconds".postln;
		^super.seconds;
	}
	sched { | delta, item |
		"LogClock::sched".postln;
		super.sched(delta, item);
	}
	schedAbs { | beat, item |
		"LogClock::schedAbs".postln;
		super.schedAbs(beat, item);
	}
	clear { | releaseNodes = true |
		"LogClock::clear".postln;
		^super.clear(releaseNodes);
	}
	etempo_ { | newTempo |
		"LogClock::etempo_".postln;
		^super.etempo_ = newTempo;
	}
	beats2secs { | beats |
		"LogClock::beats2secs".postln;
		^super.beats2secs(beats);
	}
	secs2beats { | secs |
		"LogClock::secs2beats".postln;
		^super.secs2beats(secs);
	}
	nextTimeOnGrid { | quant = 1, phase = 0 |
		"LogClock::nextTimeOnGrid".postln;
		^super.nextTimeOnGrid(quant, phase);
	}
	timeToNextBeat { | quant = 1.0 |
		"LogClock::timeToNextBeat".postln;
		^super.timeToNextBeat(quant);
	}
}

SyncTempoClock : TempoClock {
	var timeBaseClient;
	var beatsSyncTask;

	*new { | timeBase, tempo, beats, seconds, queueSize = 256 |
		^super.new(tempo, beats, seconds, queueSize).setVars(timeBase);
	}

	setVars { | timeBase |
		timeBaseClient = timeBase;
		beatsSyncTask = new Task({

		}, SystemClock).start;
	}
}

OldSyncTempoClock : Clock {
	const maxSleepInterval = 0.5;
	const minSleepInterval = 0;
	var timeBaseClient;
	var <tempo = 1.0;
	var queue;
	var shouldQuit;
	var wakeup;
	var schedulerTask;

	var <beatsPerBar = 4.0;
	var <barsPerBeat = 0.25;
	var <baseBarBeat = 0.0;
	var <baseBar = 0.0;

	*new { | timeBaseClient, tempo = 1.0 |
		^super.newCopyArgs(timeBaseClient, tempo).init;
	}

	init {
		queue = PriorityQueue.new;
		shouldQuit = false;
		wakeup = { | item, beats, seconds |
			var delta, saveClock;
			"wakeup: %, %, %".format(item, beats, seconds).postln;
			saveClock = thisThread.clock;
			thisThread.beats = beats;
			thisThread.seconds = seconds;
			delta = item.awake(beats, seconds, this);
			thisThread.clock = saveClock;
			delta.postln;
			if (delta.isNumber, {
				"repeat business".postln;
				queue.put(beats + delta, item);
			});
		};

		schedulerTask = Task.new({
			var expired = List.new;
			"starting schedulerTask".postln;
			while ({ shouldQuit.not }, {
				var beat, interval;
				// Fire any tasks that were scheduled for beats before
				// or up to now.
				while ({
					beat = queue.topPriority;
					beat.notNil and: { beat <= this.beats }
				}, {
					expired.add([beat, queue.pop()]);
				});

				expired.do({ | pair |
					var beat, item;
					beat = pair[0];
					item = pair[1];
					wakeup.(item, beat, this.seconds);
				});

				expired.clear();

				if (beat.notNil, {
					interval = this.beats2secs(beat) - this.seconds;
					interval = min(maxSleepInterval, interval);
					interval = max(minSleepInterval, interval);
				}, {
					interval = maxSleepInterval;
				});
				interval.wait;
			});
		}, SystemClock).start;
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

	beats2secs { | beats |
		^(beats * tempo);
	}

	secs2beats { | secs |
		^(secs / tempo);
	}

	play { | task, quant = 1 |
		this.schedAbs(this.nextTimeOnGrid(quant), task);
	}

	sched { | delta, item |
		this.schedAbs(this.beats + delta, item);
	}

	schedAbs { | beat, item |
		"schedAbs: % at %, item: %".format(beat, this.beats, item).postln;
		// Because of our schedule polling interval we execute anything
		// scheduled for *now* right now, instead of putting on the queue,
		// to avoid problems with the granularity adding latency to the
		// immediate tasks.
		if (beat <= this.beats, {
			Task.new({
				"task wakeup % <= %".format(beat, this.beats).postln;
				wakeup.(item, beat, this.seconds);
			}, SystemClock).start;
		}, {
			queue.put(beat, item);
		});
	}

	nextTimeOnGrid { | quant = 1, phase = 0 |
		if (quant == 0) { ^this.beats + phase };
		if (quant < 0) { quant = beatsPerBar * quant.neq };
		if (phase < 0) { phase = phase % quant };
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase;
	}
}