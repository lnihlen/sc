DroneChannel {
	var amp = 0.0;
	var muted = true;

	// Keep a stack of currently playing synths, remove old ones with pruneOldEvents().
	var synths;

	// Load or setup whatever states need setting up, define Synths.
	*add { | server | }
	// Tear down state.
	*remove { }

	*new { | server |
		^super.new.init(server);
	}

	init { | server |
		synths = List.new;
	}

	pruneOldSynths {
		while ({ (synths.size() > 0) && (synths[synths.size() - 1].isPlaying.not) }, {
			synths.pop();
		});
	}

	// Render mono audio to output channel | out | with duration | duration |.
	play { | server, group, out = 0, duration = 30.0 |
		this.pruneOldSynths();
	}

	setMuted { | is_muted |
		muted = is_muted;
		this.setAmp(amp);
	}

	setValueOnSynths { | key, value |
		this.pruneOldSynths();
		synths.do({ | synth |
			if (synth.isPlaying, { synth.set(key, value) });
		});
	}

	setAmp { | new_amp |
		var amp_val;
		amp = new_amp;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		this.setValueOnSynths(\amp, amp_val);
	}

	// synth-dependent tone parameters, range between 0 and 1.
	setToneValue { | index, range |
	}

	// Given an input range 0 through 1 return a number in seconds to use as a duration
	// for this drone. Default is 1 to 30s.
	getRangedDuration { | range |
		^(1.0 + (range * 29.0));
	}

	// Same but for how often should wait between issuing plays. Default is 1s to 30s.
	getRangedPeriod { | range |
		^(1.0 + (range * 29.0));
	}
}

ShalabiDrone : DroneChannel {
	*add { | server |
		SynthDef("shalabi_drone", {
			arg out = 0, gate = 1, attack = 5.0, dur = 2.0, release = 5.0, amp = 0.7, freq = 73,
			impulse_rate = 7.0, impulse_attack = 0.05, impulse_release = 0.5;

			var audio, trigger, perc, noise_perc, octave_overtones = 3, fifth_overtones = 3,
			flat_third_overtones = 3, flat_seven_overtones = 3;

			trigger = Impulse.ar(impulse_rate);
			perc = EnvGen.ar(Env.perc(attackTime: impulse_attack, releaseTime: impulse_release), trigger);
			// note that noise_perc fades off faster
			noise_perc = EnvGen.ar(Env.perc(attackTime: impulse_attack, releaseTime: impulse_attack), trigger);

			audio = DynKlang.ar(`[
				// frequencies
				Array.fill(octave_overtones, { | i | 2**i }) ++
				Array.fill(fifth_overtones, { | i | (2**i) * 1.498356394771042}) ++
				Array.fill(flat_third_overtones, { | i | (2**i) * 1.189205718217262}) ++
				Array.fill(flat_seven_overtones, { | i | (2**i) * 1.78180602006689}),

				// amplitudes
				Array.fill(octave_overtones, { | i | 1.0 / (3**(i + 1))}) ++
				Array.fill(fifth_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}) ++
				Array.fill(flat_third_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}) ++
				Array.fill(flat_seven_overtones, { | i | (1.0 / (3**(i + 1))) * (TRand.ar(0.2, 0.5, trigger) * (perc / 2))}),

				// phases
				Array.fill(octave_overtones + fifth_overtones + flat_third_overtones + flat_seven_overtones,
					{ | i | TRand.ar(0, 2pi, trigger) * perc; })],
			freqscale: freq * (1.0 + (TRand.ar(-0.01, 0.01, trigger) * noise_perc)));

			audio = LPF.ar(audio, freq * (1.0 + perc));

			audio = LinXFade2.ar(audio, PinkNoise.ar, -1.0 + (noise_perc * 0.02), amp);

			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: Done.freeSelf);

			Out.ar(out, Pan2.ar(audio));
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\shalabi_drone, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\freq, 38.midicps,
			\amp, amp_val,
			\out, out
		], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}
}

ChipperShredder : DroneChannel {
	classvar max_buffer_count = 10;
	var record_buffers;
	var playback_buffers;
	var quit = false;
	var buffer_count = 0;
	var record_task;
	var tone_switch_rate = 2;
	var tone_switch_time = 0.05;

	*add { | server |
		SynthDef("chipper_shredder_recorder", {
			arg record_buffer = 0;
			var audio = SoundIn.ar(0);
			RecordBuf.ar(audio, record_buffer, loop: 0, doneAction: Done.freeSelf);
		}).add;

		SynthDef("chipper_shredder_playback", {
			arg playback_buffer = 0, out = 0, attack = 1, release = 1, gate = 1, amp = 1,
			switch_rate = 2, switch_time = 0.05;

			var trigger, a_trigger, b_trigger, a_active, b_active, ramp, buf_a, buf_b, audio;
			// When trigger fires we start a rapid cross-fade from one buffer stream to the next, while
			// jumping to a new location in the new buffer stream. The TDelay prevents Dust from
			// generating triggers at a rate greater than the switch_time, allowing the crossfade to finish
			// and thus limiting switching artifacts.
			trigger =  TDelay.ar(Dust.ar(switch_rate), switch_time);
			a_trigger = PulseDivider.ar(trigger, 2, 0);
			b_trigger = PulseDivider.ar(trigger, 2, 1);
			a_active = SetResetFF.ar(a_trigger, b_trigger);
			b_active = SetResetFF.ar(b_trigger, a_trigger);

			buf_a = PlayBuf.ar(1, playback_buffer,
				trigger: TDelay.ar(b_trigger, switch_time),
				startPos: TIRand.ar(0, BufFrames.kr(playback_buffer) - 1, b_trigger),
				loop: 1);
			buf_b = PlayBuf.ar(1, playback_buffer,
				trigger: TDelay.ar(a_trigger, switch_time),
				startPos: TIRand.ar(0, BufFrames.kr(playback_buffer) - 1, a_trigger),
				loop: 1);

			// If a_trigger triggers a_active, ramp goes to 1.0 and we transition from buf_b
			// (1.0 in LinXFade2 pan argument) to buf_a (-1), and can ignore second term because
			// b_active goes to 0.
			ramp =
			(a_active * LinLin.ar(Clip.ar(Sweep.ar(a_trigger, 1.0 / switch_time), 0, 1), 0, 1, 1, -1)) +
			(b_active * LinLin.ar(Clip.ar(Sweep.ar(b_trigger, 1.0 / switch_time), 0, 1), 0, 1, -1, 1));
			audio = LinXFade2.ar(buf_a, buf_b, ramp, amp);

			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: 2);
			Out.ar(out, audio);

		}).add;

		^super.add(server);
	}

	init { | server |
		record_buffers = List.new;
		playback_buffers = List.new;
		record_task = Task.new({
			var buffer;
			while ({ quit.not }, {
				buffer = record_buffers.pop();
				if (buffer.notNil, {
					Synth(\chipper_shredder_recorder, [\record_buffer, buffer]);
					wait(5.1);
					buffer.normalize(0.9);
					playback_buffers.insert(0, buffer);
				}, {
					if (buffer_count < max_buffer_count, {
						record_buffers.insert(0, Buffer.alloc(server, server.sampleRate * 5.0, 1));
						buffer_count = buffer_count + 1;
					}, {
						// We've allocated all buffers we can, just have to wait for some to finish
						// playing.
						wait(5.1);
					});
				});
			});
		}, SystemClock).start;

		^super.init(server);
	}

	free {
		quit = true;
		record_task.free;
		record_buffers.do({ | e | e.release; });
		playback_buffers.do({ | e | e.release; });
	}

	setToneValue { | index, range |
		switch (index,
			0, {
				tone_switch_rate = 1.0 / (0.25 + (range * 3.75));
				this.setValueOnSynths(\switch_rate, tone_switch_rate);
			},
			1, {
				tone_switch_time = 0.05 + (range * 0.19);
				this.setValueOnSynths(\switch_time, tone_switch_time);
			}
		).value;
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val, buffer;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		buffer = playback_buffers.pop();
		if (buffer.notNil, {
			synth = Synth(\chipper_shredder_playback, [
				\attack, duration / 3.0,
				\release, duration / 3.0,
				\playback_buffer, buffer,
				\amp, amp_val,
				\switch_rate, tone_switch_rate,
				\switch_time, tone_switch_time,
				\out, out], group);
			Task({
				duration.wait;
				synth.release;
				(duration / 3.0).wait;
				record_buffers.insert(0, buffer);
			}).start;

			NodeWatcher.register(synth, true);
			synths.insert(0, synth);
		});
		^super.play(server, group, out, duration);
	}
}

ImpulseGatedStatic : DroneChannel {
	*add { | server |
		SynthDef("impulse_gated_static", {
			arg out = 0, attack = 1, release = 1, gate = 1, amp = 1;
			var audio, env;

			env = Clip.ar(
				LPF.ar(
					Integrator.ar(
						Dust.ar(
							TRand.kr(10.0, 100.0, Dust.kr(0.5))),
						0.999),
					100),
				0.0, 1.0);
			env = env * Linen.kr(gate, attack, 1.0, release, doneAction: 2);
			audio = env * GrayNoise.ar(mul: amp);

			Out.ar(out, audio);
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\impulse_gated_static, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\amp, amp_val,
			\out, out], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}
}

PowerChirper : DroneChannel {
	*add { | server |
		SynthDef("power_chirper", { | freq = 440, dur = 5.0, gate = 1, attack = 1.0, release = 1.0, amp = 1.0, out = 0 |
			var audio, start_freq, stop_freq;
			start_freq = freq * Rand(1.0, 2.0);
			stop_freq = start_freq * Rand(4.0, 8.0);

			audio = SinOsc.ar(
				freq: LinExp.kr(
					SinOsc.kr(
						freq: Line.kr(Rand(-4.0, 4.0), Rand(-4.0, 4.0), dur) / dur,
						phase: Rand(0, 2pi),
						mul: start_freq - 1.0,
						add: Line.kr(start: start_freq, end: stop_freq, dur: dur)) +
					SinOsc.kr(
						freq: Rand(5.0, 15.0),
						phase: Rand(0, 2pi),
						mul: Rand(start_freq / 8.0, start_freq / 4.0)),
					start_freq / 2.0, stop_freq, start_freq, stop_freq),
				mul: amp);

			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: 2);
			Out.ar(out, audio);
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 3.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\power_chirper, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\freq, 38.midicps,
			\amp, amp_val,
			\out, out], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}

	getRangedDuration { | range |
		^(0.5 + (range * 9.5));
	}
}

PMDrone : DroneChannel {
	*add { | server |
		SynthDef("pm_drone", { | out = 0, freq = 440, gate = 1, amp = 1.0, attack = 10.0, dur = 30.0, release = 10.0 |
			var audio;
			audio = PMOsc.ar(
				freq / 2,
				SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), Rand(0, 2pi), freq / 12.0, freq),
				SinOsc.kr(SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), Rand(0, 2pi), 4, 5), 0, pi / 2.0, pi), mul: amp);
			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: 2);
			Out.ar(out, audio);
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\pm_drone, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\freq, 38.midicps,
			\amp, amp_val,
			\out, out], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}
}

ImpulseIntegrator : DroneChannel {
	*add { | server |
		SynthDef("impulse_oort", {
			arg out = 0, gate = 1, attack = 5.0, dur = 2.0, release = 5.0, amp = 0.7, freq = 73;
			var audio, sin, peak_pulses;
			peak_pulses = Rand(5000, 15000);
			sin = SinOsc.kr(freq / 2.0, 0, peak_pulses);
			audio = Dust.ar(sin.abs);
			audio = (audio * InRange.kr(sin, 0, peak_pulses)) - (audio * InRange.kr(sin, -1.0 * peak_pulses, 0));
			audio = Clip.ar(Integrator.ar(audio, SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), 0, 0.05, 0.9)), -1, 1);
			audio = LPF.ar(audio,
				SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), Rand(0, 2pi), freq * 1.5, freq * 4), mul: amp);
			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: Done.freeSelf);
			Out.ar(out, audio);
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\impulse_oort, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\freq, 38.midicps,
			\amp, amp_val,
			\out, out], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}
}

KlangOvertones : DroneChannel {
	*add { | server |
		SynthDef("klang_overtone", {
			arg out = 0, gate = 1, attack = 5.0, dur = 2.0, release = 5.0, amp = 0.7, freq = 73;
			var audio, overtones = 8;

			audio = DynKlang.ar(`[
				Array.fill(overtones, { |i|
					SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), Rand(0.0, 2pi), 1.0 / 8.0, i + 1); }),
				Array.fill(overtones, { |i|
					SinOsc.kr(
						Rand(1.0 / 20.0, 1.0 / 10.0),
						Rand(0.0, 2pi),
						0.25 / (i + 1).squared,
						0.5 / (i + 1).squared); }),
				Array.fill(overtones, { Rand(0.0, 2pi); })
			],
			freqscale: freq);

			audio = LPF.ar(audio,
				SinOsc.kr(Rand(1.0 / 20.0, 1.0 / 10.0), Rand(0.0, 2pi), freq / 4.0, freq + (freq / 4.0)), mul: amp);

			audio = audio * Linen.kr(gate, attack, 1.0, release, doneAction: Done.freeSelf);

			Out.ar(out, audio);
		}).add;

		^super.add(server);
	}

	play { | server, group, out = 0, duration = 30.0 |
		var synth, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		synth = Synth(\klang_overtone, [
			\attack, duration / 3.0,
			\release, duration / 3.0,
			\freq, 38.midicps,
			\amp, amp_val,
			\out, out
		], group);
		Task({ duration.wait; synth.release; }).start;

		NodeWatcher.register(synth, true);
		synths.insert(0, synth);
		^super.play(server, group, out, duration);
	}
}

Apollo11Sounds : DroneChannel {
	var number_of_buffers = 25;
	var a11_files;
	var a11_buffers;
	var buffer_server;
	var tone_rate_amp = 0.0;
	var tone_rate_freq = 0.5;

	init { | server |
		var a11_base_path = PathName.new("/Users/luken/Documents/sc/samples/apollo_11/");
		buffer_server = server;

		a11_files = List.new;
		a11_base_path.filesDo({|path_name|
			if (path_name.isFile && path_name.extension == "wav", { a11_files.add(path_name.fullPath); });
		});
		a11_files = a11_files.scramble;

		a11_buffers = List.new;
		Task.new({ this.readNextBuffer(nil); }).start;

		SynthDef("a11_sounds", {
			arg buffer_number, out = 0, gate = 1, attack = 5.0, amp = 0.7, dur = 2.0, release = 5.0, buffer_start = 0,
			playback_rate_freq = 0.5, playback_rate_amp = 0.0;
			var audio, buffer;
			audio = PlayBuf.ar(1, buffer_number,
				SinOsc.kr(playback_rate_freq, 0, playback_rate_amp, 1.0),
					1, buffer_start);
			audio = audio * amp * Linen.kr(gate, attack, 1.0, release, doneAction: Done.freeSelf);
			Out.ar(out, audio);
		}).add;

		^super.init(server);
	}

	readNextBuffer { | previous_buffer_read |
		if (previous_buffer_read.notNil, {
			a11_buffers.add(previous_buffer_read);
		});

		if (a11_buffers.size < number_of_buffers, {
			Buffer.read(buffer_server, a11_files[a11_buffers.size], 0, -1, { |buffer|
				this.readNextBuffer(buffer);
			});
		}, {
			"Apollo11Sounds ready.".postln;
		});
	}

	remove {
		a11_buffers.do{ |buffer|
			buffer.free;
		};
		^super.remove;
	}

	setToneValue { | index, range |
		switch (index,
			0, {
				tone_rate_amp = range / 2.0;
				this.setValueOnSynths(\playback_rate_amp, tone_rate_amp);
			},
			1, {
				tone_rate_freq = range.linexp(0, 1, 0.1, 10.0);
				this.setValueOnSynths(\playback_rate_freq, tone_rate_freq);
			}
		).value;
	}

	play { | server, group, out = 0, duration = 30.0 |
		var buf_idx, synth, start_frame, amp_val;
		if (muted, { amp_val = 0.0; }, { amp_val = amp; });
		if (a11_buffers.size > 0, {
			buf_idx = rrand(0, a11_buffers.size - 1);
			start_frame = rrand(0, a11_buffers[buf_idx].numFrames - (duration * a11_buffers[buf_idx].sampleRate));
			synth = Synth(\a11_sounds, [
				\buffer_number, a11_buffers[buf_idx].bufnum,
				\attack, duration / 3.0,
				\release, duration / 3.0,
				\buffer_start, start_frame,
				\amp, amp_val,
				\playback_rate_freq, tone_rate_amp,
				\playback_rate_amp, tone_rate_freq,
				\out, out
			], group);
			Task({ duration.wait; synth.release; }).start;

			NodeWatcher.register(synth, true);
			synths.insert(0, synth);
		});

		^super.play(server, group, out, duration);
	}
}