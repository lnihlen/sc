DroneMixer {
	classvar busses;
	classvar init_server;
	// Creates serious memory pressure at higher values.
	classvar max_delay_time = 5.0;

	var <>drone_periods;
	var <>drone_durations;

	var drone_channels;
	var drone_tasks;
	var mixer_synth;
	var drone_group;
	var mixer_group;

	var <>quit;

	*setup { | server |
		busses = Array.fill(32, { Bus.audio(server); });
		init_server = server;

		SynthDef.new("drone_mixer", {
			arg amp = 1.0, reverb_mix = 0.33, delay_time = 2.0, delay_mix = 0.5;
			var audio, panned_busses;
			panned_busses = Array.fill(32, { | i |
				Pan2.ar(In.ar(busses[i]), (i / 16.0) - 1.0);
			});
			audio = Mix.new(panned_busses);

			/*
			audio = DelayC.ar(audio, max_delay_time, delay_time, delay_mix, audio);
			audio = DelayC.ar(audio, max_delay_time, delay_time, delay_mix / 2.0, audio);
			audio = DelayC.ar(audio, max_delay_time, delay_time, delay_mix / 4.0, audio);
			audio = DelayC.ar(audio, max_delay_time, delay_time, delay_mix / 8.0, audio);
			audio = DelayC.ar(audio, max_delay_time, delay_time, delay_mix / 16.0, audio);
			*/

			audio = FreeVerb.ar(audio, reverb_mix);
			audio = Limiter.ar(audio, 0.9);
			audio = audio * amp;
			Out.ar(0, audio);
		}).add;

		Apollo11Sounds.add(server);
		KlangOvertones.add(server);
		ImpulseIntegrator.add(server);
		PMDrone.add(server);
		PowerChirper.add(server);
		ImpulseGatedStatic.add(server);
		ChipperShredder.add(server);
		ShalabiDrone.add(server);
	}

	*new {
		^super.new.init();
	}

	*teardown {
		KlangOvertones.remove;
		Apollo11Sounds.remove;
		ImpulseIntegrator.remove;
		PMDrone.remove;
		PowerChirper.remove;
		ImpulseGatedStatic.remove;
		ChipperShredder.remove;
		ShalabiDrone.remove;
		busses.do({ | bus | bus.free(); });
	}

	init {
		drone_channels = [
			Apollo11Sounds.new(init_server),
			KlangOvertones.new(init_server),
			ImpulseIntegrator.new(init_server),
			PMDrone.new(init_server),
			PowerChirper.new(init_server),
			ImpulseGatedStatic.new(init_server),
			ChipperShredder.new(init_server),
			ShalabiDrone.new(init_server)
		];
		drone_group = Group.new(init_server);
		mixer_group = Group.new(drone_group, \addAfter);
		drone_periods = Array.fill(drone_channels.size, { | i | drone_channels[i].getRangedPeriod(0.0); });
		drone_durations = Array.fill(drone_channels.size, { | i | drone_channels[i].getRangedDuration(0.0); });
		drone_tasks = Array.fill(drone_channels.size, { | i | Task.new({
			var bus_index;
			while ({ this.quit.not }, {
				bus_index = rrand(0, busses.size - 1);
				drone_channels[i].play(init_server, drone_group, busses[bus_index], drone_durations[i]);
				wait(drone_periods[i]);
			});
			postln("Task for channel % exiting.\n", i);
		}, SystemClock)});
		quit = false;
		mixer_synth = Synth("drone_mixer", [\amp, 1.0], mixer_group);
		^this;
	}

	setChannelAmp { | channel_number, amp |
		drone_channels[channel_number].setAmp(amp);
	}

	setChannelMuted { | channel_number, muted |
		drone_channels[channel_number].setMuted(muted);
	}

	setChannelPeriod { | channel_number, range |
		drone_periods[channel_number] = drone_channels[channel_number].getRangedPeriod(range);
	}

	setChannelDuration { | channel_number, range |
		drone_durations[channel_number] = drone_channels[channel_number].getRangedDuration(range);
	}

	setChannelTone { | channel_number, tone_index, range |
		drone_channels[channel_number].setToneValue(tone_index, range);
	}

	setMasterVolume{ | volume |
		mixer_synth.set(\amp, volume);
	}

	// dry/wet balance, range 0-1
	setReverbMix { | mix |
		mixer_synth.set(\reverb_mix, mix);
	}

	// Set between some fraction of minimum delay and max, range 0-1
	setDelayTime { | scaled_time |
		var delay_time = max(min(scaled_time * max_delay_time, 0.01), max_delay_time);
		mixer_synth.set(\delay_time, delay_time);
	}

	setDelayMix { | mix |
		mixer_synth.set(\delay_mix, mix);
	}

	play {
		drone_tasks.do({ | task | task.play; });
	}

	pause {
		drone_tasks.do({ | task | task.pause; });
	}

	numberOfChannels {
		^drone_channels.size;
	}

	free {
		quit = true;
		mixer_synth.free;
		drone_tasks.do({ | task | task.free; });
	}
}