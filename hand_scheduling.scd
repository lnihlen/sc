~pattern.play;

(
~quit = false;
~cond = Condition.new(false);
~cleanup = EventStreamCleanup.new;
~pattern = 	Pbind(
	\scale, Scale.chromatic,
	\octave, 4,
	\root, 0,  // key of C
	\amp, 1.0,
	\degree, Pseq([-1,   0,   \rest, 0,   -1,   0,   \rest, 0,   -1,   0,    \rest, 0,   1,   0,   -1 ], inf),
	\dur,    Pseq([0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,   0.5, 1.0, 0.5, 1.0], inf)
);
~stream = ~pattern.asStream;

~period = 1.0;
~range = 0.5;

~min_values_for_mean = 7;
~max_values_for_mean = 15;
~tap_time_deltas = List.new;
~last_tap = nil;

MIDIIn.connectAll;
~midi_port = MIDIIn.findPort("Logidy UMI3", "Logidy UMI3 MIDI 1");
if (~midi_port.notNil, {
	~midi_func = MIDIFunc.noteOn({ | value, number, channel, source_id |
		var tap_time = Process.monotonicClockTime;
		~cond.test = true;
		~cond.signal;

		if (~last_tap.notNil, {
			var unlikely_delta;
			var delta = tap_time - ~last_tap;
			// If we've already established a mean period and this value
			// is much greater we consider that input has stopped and is now
			// running again, we discard the delta and start timing anew.
            if (~period.notNil, {
				unlikely_delta = ~period * 2.0;
			}, {
				unlikely_delta = delta * 2.0;
			});

			if (delta < unlikely_delta, {
				~tap_time_deltas.addFirst(delta);
				while ({ ~tap_time_deltas.size > ~max_values_for_mean }, {
					~tap_time_deltas.pop();
				});
			});
		});

		~last_tap = tap_time;

		if (~tap_time_deltas.size >= ~min_values_for_mean, {
			var sum = 0.0;
			~tap_time_deltas.do({ | x | sum = sum + x; });
			~period = sum / ~tap_time_deltas.size.asFloat;
			sum = 0.0;
			~tap_time_deltas.do({ | x | sum = (~period - x).squared + sum; });
			~range = sum / (~tap_time_deltas.size.asFloat - 1.0);
		});

	});
}, {
	"unable to find MIDI controller.".postln;
});

// For every tap pulse train we compute mean, std_dev period between taps based on weighted
// average of last k taps. When a tap comes in we measure the timing and compare it against
// the expected next tap time and std_dev. There is some range of expected timing of next tap
// which is r*std_dev, with r some parameter to scale standard_deviation of tap values.
// Tap timing could be any of the following:
//
// Super early, e.g. well before expected - could consider a debouncing case when std_dev is tight,
//    and disregard as spurious.
//
// Early, but within std_dev - fire next beat of scheduling now, recompute mean timing, then start
//    waiting now for next tap.
//
//  **  At this point tap would be exactly on time, so we would fire events based on timer going off,
//    but will need to ensure that behavior here is sane
//
//  Late, but within std_dev - we've already fired event, but we re-compute mean tempo based on longer
//    period tap and then restart the clock for when we should wake from this time
//
//  Super late, outside of std_dev but somehow differentiated from super early for the next beat.
//    might be worth considering these cases same as super early for next beat? But how does one
//    slow down the tap tempo-er?

fork {
	// For any event scheduled for longer then one beat we decrement the timer
	// one beat at a time.
	var current_event = nil, remaining_time = 0.0;
	var next_beat_expected_at = nil;
	var delta = 0.0;
	var beat_number = 0;

	current_event = ~stream.next(());
	current_event.dur = current_event.dur * ~period;

	while ({~quit.not}, {
		// If the current event has a remaining_time > 1
		// we take no action this iteration. Otherwise we
		// can assume next event takes place within this
		// beat.
		while ({ remaining_time < ~period }, {
			if (current_event.isRest.not, {
				if (remaining_time == 0.0, {
					current_event.play();
				}, {
					var e = current_event;
					SystemClock.sched(remaining_time, { e.play(); });
				});
			});
			remaining_time = remaining_time + current_event.dur;
			current_event = ~stream.next(());
			current_event.dur = current_event.dur * ~period;
		});

		remaining_time = remaining_time - 1.0;

		next_beat_expected_at = Process.monotonicClockTime + ~period;
		~cond.test = false;
		fork {
			~period.wait;
			~cond.test = true;
			~cond.signal;
		};
		~cond.wait;
		~cond.test = false;

		delta = next_beat_expected_at - Process.monotonicClockTime;

		if (delta > 0, {
			// Taps earlier than the expected range will require some
			// additional waiting, as they could be either spurious or
			// possibly late taps from last beat.
			if (delta > ~range, {
				// Too early to fire another beat, wait for already fired
				// timeout.
				~cond.wait;

				// Super early taps, within range of the previous beat, are
				// likely late taps. We add a little extra wait before firing
				// next beat.
				if (delta > (~period - ~range), {
					(~period - delta).wait;
				});
			});
		});
	});
};
)

(
~midi_func.free;
~quit = true;
~cond.test = true;
~cond.signal;
)