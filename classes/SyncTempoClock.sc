SyncTempoClock : TempoClock {
	var timeBase;

	*new { | timeBaseClient, tempo, beats, seconds, queueSize=256 |
		^super.new.init(timeBaseClient, tempo, beats, seconds, queueSize);
	}

	init { | timeBaseClient, tempo, beats, seconds, queueSize |
		timeBase = timeBaseClient;
		super.init(tempo, beats, seconds, queueSize);
	}

	elapsedBeats {
		^this.secs2beats(timeBase.elapsedTime);
	}

	beats {
		^this.secs2beats(thisThread.seconds + timeBase.timeDiff);
	}

	seconds {
		^(thisThread.seconds + timeBase.timeDiff);
	}
}