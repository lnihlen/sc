// Uses a simple rolling average to track time distances between calls to tap(), then when it has
// enough data it will start calling the user-supplied function bang() with the approximated tempo.
// To reset the tempo detector call reset(), which will stop calling bang() until the tapper has
// enough data to resume. Can alter the parameters min_values_for_mean and max_values_for_mean to
// keep track of how many values to track. The function bang() is called with one argument, which is
// the predicted wait in seconds until the next execution of bang(). TapTempo will also call
// onTrackingChanged() supplied function when either we have enough data to start tracking or we
// have been reset and therefore stop tracking.
TapTempo {
	var <>min_values_for_mean = 7;
	var <>max_values_for_mean = 15;
	var <>bang;
	var <>onTrackingChanged;

	var <quit;
	var tap_time_deltas;
	var ticker_task;

	var last_tap;
	var <running = false;
	var <mean_period;

	*new {
		^super.new.init();
	}

	init {
		ticker_task = Task.new({
			while ({this.quit.not}, {
				this.mean_period.wait;
				this.bang.value(mean_period);
			});
		}, SystemClock);

		quit = false;
		tap_time_deltas = List.new;
		this.reset();

		^this;
	}

	tap {
		var tap_time = Process.monotonicClockTime;
		if (last_tap.notNil, {
			var unlikely_delta;
			var delta = tap_time - last_tap;
			// If we've already established a mean period and this value
			// is much greater we consider that input has stopped and is now
			// running again, we discard the delta and start timing anew.
            if (mean_period.notNil, {
				unlikely_delta = mean_period * 2.0;
			}, {
				unlikely_delta = delta * 2.0;
			});

			if (delta < unlikely_delta, {
				tap_time_deltas.addFirst(delta);
				while ({ tap_time_deltas.size > max_values_for_mean }, {
					tap_time_deltas.pop();
				});
			});
		});

		last_tap = tap_time;

		if (tap_time_deltas.size >= min_values_for_mean, {
			var sum = 0.0;
			tap_time_deltas.do({ | x | sum = sum + x; });
			mean_period = sum / tap_time_deltas.size.asFloat;
			if (running.not, {
				ticker_task.start;
				running = true;
			});
		});
	}

	reset {
		if (running, { ticker_task.stop; });
		last_tap = nil;
		running = false;
		mean_period = nil;
		tap_time_deltas.clear();
	}

	free {
		quit = true;
	}
}
