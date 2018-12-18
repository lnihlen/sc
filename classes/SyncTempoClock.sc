SyncTempoClock : TempoClock {
	const beatsUpdateInterval = 0.2;
	var timeBase;
	var beatsSyncTask;

	*new { | timeBaseClient, tempo, beats, seconds, queueSize = 256 |
		^super.new(tempo, beats, seconds, queueSize).setVars(timeBaseClient);
	}

	setVars { | timeBaseClient |
		timeBase = timeBaseClient;
		this.beats_((Main.elapsedTime + timeBase.timeDiff) / this.tempo);

		beatsSyncTask = Task.new({
			while ({ true }, {
				beatsUpdateInterval.wait;
				this.beats_((Main.elapsedTime + timeBase.timeDiff) / this.tempo);
			});
		}, SystemClock).start;
	}

	// Override play to avoid the blast of events that can happen as this clock
	// catches up with the remote clock, with a different time base.
	play { | task, quant = 1 |
		this.schedAbs(this.nextTimeOnGrid(quant), task);
	}
}
