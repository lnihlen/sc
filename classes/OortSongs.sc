// Base class for Oort Cloud song objects, manipulated by OortShow object.
OortSong {
	var playing = false;

	*new {
		^super.new.init;
	}

	getName {
		^"** undefined base name";
	}

	init {
	}

	bang {
	}

	// Start requested by user on this song.
	// | midi_out | is a midi output instrument
	// | countown_update_func | is a function called with a string argument that
	//     will update a label with the provided string.
	// | cue_out | is a stereo bus for cue audio output
	start { | midi_out = nil, countdown_update_func = nil, cue_out = 0 |
		playing = true;
	}

	stop {
		playing = false;
	}

	reset {
	}
}

// OortsSequencedSongs have some pattern to play on the Moog Minitaur synth
// over MIDI, use the tap tempo input to track a tempo, provide a countdown
// before starting to the cue audio output (a lead-in) and visuals.
OortSequencedSong : OortSong {
	classvar <>midi_latency = 0.2;
	classvar count_samples;

	var start_requested = false;
	var countdown = 0;
	var <>cue_out = 0;
	var <>tempo_clock = nil;
	var <>countdown_update_func = nil;
	var <>stream = nil;
	var <>midi_out = nil;

	*setup { | server |
		SynthDef("play_cue_buf", { | amp = 1.0, out = 0, bufnum = 0 |
			var audio = Pan2.ar(PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), doneAction: Done.freeSelf));
			Out.ar(out, audio);
		}).add;

		SynthDef("cue_chirp", { | amp = 1.0, out = 0 |
			var audio = BPF.ar(WhiteNoise.ar(), 2000.0, 0.05,
				5.5 * EnvGen.kr(Env.perc(0.01, 0.1), doneAction: Done.freeSelf));
			Out.ar(out, Pan2.ar(audio));
		}).add;

		count_samples = [
			Buffer.read(server, "/Users/luken/src/audio/counting/go.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/one.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/two.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/three.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/four.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/five.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/six.wav"),
			Buffer.read(server, "/Users/luken/src/audio/counting/seven.wav"),
		];
	}

	bang { | period |
		Synth(\cue_chirp, [\out, this.cue_out]);

		if (playing, {
			// Tweak tempo based on most recent data.
			tempo_clock.tempo = 1.0 / period;
		}, {
			if (start_requested, {
				countdown = countdown - 1;
				if (countdown <= 8, {
					Synth(\play_cue_buf, [
						\out, this.cue_out,
						\bufnum, count_samples[countdown]
					]);
				});

				// To absorb MIDI control latency we start a beat earlier than necessary but
				// don't wait for the entire beat to start the pattern, resulting in the pattern
				// starting a bit early but then delay hopefully landing right around the time
				// of the next bang().
				if (countdown == 0, {
					tempo_clock = TempoClock.new(1.0 / period);
					Task.new({
						(period - OortSequencedSong.midi_latency).wait;
						this.stream = this.playSequence(this.midi_out, this.tempo_clock);
					}, SystemClock).start;
					start_requested = false;
					playing = true;
					countdown_update_func.value("P");
				}, {
					countdown_update_func.value(countdown.asString);
				});
			});
		});
	}

	start { | midi_out = nil, countdown_update_func = nil, cue_out = 0 |
		if (playing.not, {
			start_requested = true;
			countdown = this.getLeadInCounts;
			this.cue_out = cue_out;
			this.countdown_update_func = countdown_update_func;
			this.midi_out = midi_out;
		});
	}

	stop {
		if (start_requested, {
			start_requested = false;
		});
		if (playing, {
			playing = false;
			stream.stop;
		});
	}

	playSequence { | midi_out, tempo_clock |
	}

	getLeadInCounts {
		^8;
	}
}

OSFreeFormIntro : OortSong {
	getName {
		^"Free-Form Intro";
	}
}

OSCaveman : OortSequencedSong {
	getName {
		^"Caveman";
	}

	playSequence { | midi_out, tempo_clock |
		^Pbind(
			\type, \midi,
			\midiout, midi_out,
			\midicmd, \noteOn,
			\chan, 16,
			\scale, Scale.chromatic,
			\octave, 3,
			\root, 0,  // key of C
			\amp, 1.0,
			\degree, Pseq([-1,   0,   \rest, 0,   -1,   0,   \rest, 0,   -1,   0,    \rest, 0,   1,   0,   -1 ], inf),
			\dur,    Pseq([0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,   0.5, 1.0, 0.5, 1.0], inf)
		).play(tempo_clock);
	}
}

OSTransitionToSadness : OortSong {
	getName {
		^"To Sadness";
	}
}

OSSadness : OortSong {
	getName {
		^"Sadness";
	}
}

OSSegueOnToms : OortSong {
	getName {
		^"Segue on Toms";
	}
}

OSHeavy : OortSong {
	getName {
		^"Heavy";
	}
}

OSHeavyToInterlude : OortSong {
	getName {
		^"Heavy to Interlude";
	}
}

OSInterlude : OortSong {
	getName {
		^"Interlude";
	}
}

OSMintaka : OortSong {
	getName {
		^"Mintaka";
	}
}

OSNoisyEnding : OortSong {
	getName {
		^"Noisy Ending";
	}
}