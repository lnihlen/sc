TapStreamPlayer {
	var condition;
	var quit;
	var stream;

	*new { | stream |
		^super.new.init(stream);
	}

	init { | stream |
		this.stream = stream;
		condition = Condition.new(false);
		quit = false;
	}

	tap {
	}
}