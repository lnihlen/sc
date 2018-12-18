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
}
