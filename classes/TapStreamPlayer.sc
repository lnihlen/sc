TapStreamPlayer {
	const min_values_for_mean = 3;
	const max_values_for_mean = 23;
	const scheduler_slack = 0.001;

	var stream;
	var condition;
	var quit;

	// Mean and standard deviation of periods between taps.
	var period, range;
	var tap_time_deltas;
	var last_tap;

	var streamer_task;

	// Supply function to be called with | period, std_dev |
	// whenver it changes.
	var <>period_changed;

	*new { | init_stream |
		^super.new.init(init_stream);
	}

	init { | init_stream |
		stream = init_stream;
		condition = Condition.new(false);
		quit = false;
		tap_time_deltas = List.new;
	}

	tap {
		var tap_time, delta;
		tap_time = Process.monotonicClockTime;

		// This input tap can be provided in a few possible cases:
		// a) It is the first tap ever, so this.last_tap is nil.
		// b) It is the first tap in a while, so the first tap in
		//      a tap train. We will have a value for this.last_tap
		//      but the delta will be well outside the standard
		//      deviation of previous periods.
		// c) It is some subsequent tap in a tap train.
		if (last_tap.notNil, {
			var new_tap_train = false;
			delta = tap_time - last_tap;

			if (period.notNil, {
				new_tap_train = delta > (3.0 * period);
			});

			if (new_tap_train.not, {
				tap_time_deltas.addFirst(delta);
				while ({ tap_time_deltas.size > max_values_for_mean }, {
					tap_time_deltas.pop();
				});
			}, {
				// Looks like the start of a new pulse train, so we pare down
				// the list of deltas to the minimum, to allow the system to
				// react more quickly to a new mean. We don't add this delta
				// because it is likely to be spurious and will throw off the
				// mean.
				while ({ tap_time_deltas.size > min_values_for_mean }, {
					tap_time_deltas.pop();
				});
			});
		});

		last_tap = tap_time;

		// Now compute new mean and standard deviation, if we have sufficient data.
		if (tap_time_deltas.size >= min_values_for_mean, {
			var sum = 0.0;
			tap_time_deltas.do({ | x | sum = sum + x; });
			period = sum / tap_time_deltas.size.asFloat;
			sum = 0.0;
			tap_time_deltas.do({ | x |
				sum = sum + (period - x).squared;
			});
			range = (sum / (tap_time_deltas.size.asFloat - 1.0)).pow(0.5);
			period_changed.value(period, range);
		});

		condition.test = true;
		condition.signal;
	}

	start {
		// ** Enforce pre-requisites: period, range valid, stream valid.

		if (streamer_task.notNil, { streamer_task.stop; });

		// For every tap pulse train we compute mean, std_dev period between taps based on weighted
		// average of last k taps. When a tap comes in we measure the timing and compare it against
		// the expected next tap time and std_dev. There is some range of expected timing of
		// next tap which is r*std_dev, with r some parameter to scale standard_deviation of
		// tap values.
		//
		// Tap timing could be any of the following:
		//
		// Super early, e.g. well before expected - could consider a debouncing case when std_dev
		//    is tight, and disregard as spurious.
		//
		// Early, but within std_dev - fire next beat of scheduling now, recompute mean timing,
		//    then start waiting now for next tap.
		//
		//  **  At this point tap would be exactly on time, so we would fire events based on timer
		//    going off, but will need to ensure that behavior here is sane
		//
		//  Late, but within std_dev - we've already fired event, but we re-compute mean tempo
		//    based on longer period tap and then restart the clock for when we should wake from
		//    this time
		//
		//  Super late, outside of std_dev but somehow differentiated from super early for the next
		//    beat. It might be worth considering these cases same as super early for next beat?
		//    But how does one slow down the tap tempo-er?
		streamer_task = Task.new({
			var current_event = nil;
			var remaining_time = 0.0;
			var next_beat_expected_at = nil;
			var delta = 0.0;
			var beat_number = 0;

			current_event = stream.next(());

			while ({ quit.not; }, {
				// We schedule musical events one beat at a time. Since musical events can occur
				// at any point within the beat, or last longer than a single beat, we account
				// for both cases by keeping a running countdown for the longer events, and
				// moving through events in order until we have scheduled all of them that occur
				// in this next beat.
				while ({ remaining_time < 1.0 }, {
					var e = current_event.copy;
					e.dur = current_event.dur * period;
					if (remaining_time == 0.0, {
						e.play();
					}, {
						SystemClock.sched(remaining_time * period, { e.play(); });
					});
					remaining_time = remaining_time + current_event.dur;
					current_event = stream.next(());
				});

				remaining_time = remaining_time - 1.0;

				next_beat_expected_at = Process.monotonicClockTime + period;
				condition.test = false;
				fork {
					period.wait;
					condition.test = true;
					condition.signal;
				};
				condition.wait;
				condition.test = false;

				delta = next_beat_expected_at - Process.monotonicClockTime;

				// We divde the beat in half. Wakeups occuring earlier than the halfway
				// point between the last beat and the next beat are considered to be
				// late taps for the (already scheduled) last beat issued. Since we have
				// already scheduled the next beat of events we will wait for the next
				// beat timeout to fire, then wait a bit longer to add this additional
				// delay into the sequence.
				if (delta > (period / 2.0), {
					condition.wait;
					(period - delta).wait;
				});
			});
		}, SystemClock).start;
	}
}