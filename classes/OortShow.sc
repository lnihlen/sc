// An OortShow runs an overall control for an automated accompaniment to the band when performing.
// It performs the following tasks:
//   * Keeps an in-order list of OortSongs that can be segued through forward and backward
//   * Maintains a TapTempo object for tempo control of the OortSongs, plus an onscreen display of that tempo
//   * Allows for individual monitor and main audio sends with volume control on each, to allow cues
//   * Other functions as needed and discovered to be useful for the band

// Current concert device setup:
//   * Raspberry Pi - serves as primary supercollider running interface, UI, also running dhcpd for LAN,
//       will share wireless network if it has one, hardcoded gateway IP addr.
//   * MacBook Pro (later Linux) - DHCP client with static IP, can run scserver/supernova for heavier lifting
//   * MOTU 16A - DHCP client with static IP, getting OSC messages from Pi
//
//   Pi is USB MIDI to Roland FC-300 and Minitaur (or other temp synth)
//   Audio output from Minitaur (goes through BigSky??), goes into MOTU stereo input.
//   Audio output from Raspberry Pi goes into MOTU stereo input.
//   Inside MOTU audio outputs from instruments (e.g. Minitaur, others) are coallated into one stereo output, main.
//     MOTU audio output Main goes to PA, after being modulated by OSC-controlled mains volume from Pi.
//   Additionally the audio output from Pi gets mixed with main for a separate delivery for headphones, with OSC control
//     cue volume.

OortShow {
	// Currently using the LiDiVi MIDI->USB interface for the FC-300, so it
	// shows up as this very generic name.
	classvar fc_300_midi_name = "USB MIDI Interface";
	// Until Minitaur is ready using the Bitwig synth on Apple MIDI bus.
	classvar minitaur_midi_device_name = "IAC Driver";
	classvar minitaur_midi_port_name = "IAC Bus 1";

	var <>tap_tempo;
	var <>songs;
	var <>song_index;
	var <>playing;

	var win;
	var <>bpm_label;
	var <>lead_in_label;
	var <>previous_song_label;
	var <>current_song_label;
	var <>next_song_label;
	var <>tap_view;
	var <>bg_gray;
	var fc_300_midi_button;
	var minitaur_midi_button;
	var fc_300_midi_func;
	var minitaur_midi_out;

	*new {
		^super.new.init;
	}

	init {
		var win_bounds, view, dark_gray, button, button_width;
		var pad = 2;

		tap_tempo = TapTempo.new;
		tap_tempo.bang = { | period | this.bang(period); };
		songs = [
//			OSFreeFormIntro.new,
			OSCaveman.new,
			OSTransitionToSadness.new,
			OSSadness.new,
			OSSegueOnToms.new,
			OSHeavy.new,
			OSHeavyToInterlude.new,
			OSInterlude.new,
			OSMintaka.new,
			OSNoisyEnding.new,
		];
		song_index = 0;
		playing = false;

		win_bounds = Window.screenBounds;
		// For now set to half size.
		win_bounds = Rect.new(
			win_bounds.left,
			win_bounds.top,
			win_bounds.width / 2.0,
			win_bounds.height / 2.0);

		bg_gray = Color.new(0.8, 0.8, 0.8);
		dark_gray = Color.new(0.4, 0.4, 0.4);

		win = Window("Oort Show",
			bounds: win_bounds,
			resizable: false,
			border: false,
			scroll: false).front;

		// Window is split in half vertically. Top half has tempo lock status, count-in.
		// Bottom half has previous, current, next songs.
		tap_view = CompositeView.new(win, Rect(pad, pad,
			win_bounds.width - (2 * pad), (win_bounds.height / 2) - (1.5 * pad)));
		tap_view.background = bg_gray;

		bpm_label = StaticText.new(tap_view, Rect(pad, pad,
			(5 * tap_view.bounds.width / 8) - (pad * 2),
			tap_view.bounds.height - (pad * 2)));
		bpm_label.font = Font.monospace(bpm_label.bounds.height - (2 * pad));
		bpm_label.align = \right;
		bpm_label.string = "XXX";

		lead_in_label = StaticText.new(tap_view, Rect(
			bpm_label.bounds.right + pad,
			pad,
			(tap_view.bounds.width / 4) - (pad * 2),
			bpm_label.bounds.height));
		lead_in_label.font = Font.monospace(lead_in_label.bounds.height - (2 * pad));
		lead_in_label.align = \center;
		this.updateLeadInLabel("S");

/*
		// Make the tap button. NOTE: hacky but if you always make this button first it
		// will retain keyboard focus, allowing it to be pressed using the space bar.
		button = Button.new(tap_view, Rect(pad, pad,
			(tap_view.bounds.width / 4) - (pad * 2), tap_view.bounds.height - (pad * 2)));
		button.string = "tap";
		button.action = { this.tap(); };
*/

		fc_300_midi_button = Button.new(tap_view, Rect(
			tap_view.bounds.width - (tap_view.bounds.width / 8) + pad,
			pad,
			(tap_view.bounds.width / 8) - (2 * pad),
			(tap_view.bounds.height / 2) - (3 * pad)));
		fc_300_midi_button.states = [["enable FC-300"], ["disable FC-300"]];
		fc_300_midi_button.action = { | v | this.onConnectFC300(v.value == 1) };

		minitaur_midi_button = Button.new(tap_view, Rect(
			fc_300_midi_button.bounds.left,
			fc_300_midi_button.bounds.bottom + pad,
			fc_300_midi_button.bounds.width,
			fc_300_midi_button.bounds.height));
		minitaur_midi_button.states = [["enable Minitaur"], ["disable Minitaur"]];
		minitaur_midi_button.action = { | v | this.onConnectMinitaur(v.value == 1) };

		// Song view with three labels for previous, current, next song names, and smallish
		// buttons for previous, next, start, stop.
		view = CompositeView.new(win, Rect(pad, (win_bounds.height / 2) + (0.5 * pad),
			win_bounds.width - (2 * pad), (win_bounds.height / 2) - (1.5 * pad)));
		view.background = bg_gray;

		// We divide the song view into quarters vertically with the middle two quarters
		// devoted to the current track name, and buttons hiding on the right.
		previous_song_label = StaticText.new(view, Rect(pad, pad,
			((2 * view.bounds.width) / 3) - (2 * pad), (view.bounds.height / 4) - (2 * pad)));
		previous_song_label.font = Font.sansSerif(2  * previous_song_label.bounds.height / 3);
		previous_song_label.string = "previous song";
		previous_song_label.stringColor = dark_gray;

		/*
		// Row of buttons on the right in the previous songs area because seeing what was last is
		// probably the least important of the three fields.
		button_width = (view.bounds.width / (3 * 5)) - pad;
		button = Button.new(view, Rect(previous_song_label.bounds.right + pad, pad, button_width,
			previous_song_label.bounds.height));
		button.string = "previous";
		button.action = { this.previousSong(); };
		button = Button.new(view, Rect(button.bounds.right + pad, pad, button_width, button.bounds.height));
		button.string = "restart";
		button.action = { this.restartSong(); };
		button = Button.new(view, Rect(button.bounds.right + pad, pad, button_width, button.bounds.height));
		button.string = "play";
		button.action = { this.startSong(); };
		button = Button.new(view, Rect(button.bounds.right + pad, pad, button_width, button.bounds.height));
		button.string = "stop";
		button.action = { this.stopSong(); };
		button = Button.new(view, Rect(button.bounds.right + pad, pad, button_width, button.bounds.height));
		button.string = "next";
		button.action = { this.nextSong(); };
		*/

		current_song_label = StaticText.new(view, Rect(pad, previous_song_label.bounds.bottom + pad,
			view.bounds.width - (2 * pad), (view.bounds.height / 2) - (2 * pad)));
		current_song_label.font = Font.sansSerif(2 * current_song_label.bounds.height / 3);
		current_song_label.string = "current song";

		next_song_label = StaticText.new(view, Rect(pad, current_song_label.bounds.bottom + pad,
			view.bounds.width - (2 * pad), previous_song_label.bounds.height));
		next_song_label.font = Font.sansSerif(2 * next_song_label.bounds.height / 3);
		next_song_label.string = "next song";
		next_song_label.stringColor = dark_gray;

		this.songIndexChanged();

		// Try to automatically connect the devices.
		this.onConnectFC300(true);
		this.onConnectMinitaur(true);
	}

	startSong {
		if (playing.not, {
			songs[song_index].start(minitaur_midi_out, { | v | this.updateLeadInLabel(v); }, 0);
			playing = true;
		});
	}

	stopSong {
		if (playing, {
			songs[song_index].stop;
			playing = false;
			this.updateLeadInLabel("S");
		});
	}

	nextSong {
		if (playing.not, {
			song_index = min(songs.size - 1, song_index + 1);
			this.songIndexChanged();
		});
	}

	previousSong {
		if (playing.not, {
			song_index = max(song_index - 1, 0);
			this.songIndexChanged();
		});
	}

	restartSong {
	}

	tap {
		this.tap_tempo.tap();
	}

	bang { | period |
		songs[song_index].bang(period);

		Task.new({
			this.bpm_label.string = (60.0 / period).asInteger.asString;
			this.bpm_label.stringColor = Color.white;
			this.bpm_label.background = Color.black;
			0.05.wait;
			this.bpm_label.background = this.bg_gray;
		}, AppClock).start;
	}

	resetTapTempo {
		this.tap_tempo.reset();
		Task.new({
			this.bpm_label.string = "XXX";
			this.bpm_label.stringColor = Color.black;
		}, AppClock).start;
	}

	songIndexChanged {
		Task.new({
			if (this.song_index == 0, {
				this.previous_song_label.string = "";
			}, {
				this.previous_song_label.string = this.songs[this.song_index - 1].getName();
			});

			this.current_song_label.string = this.songs[this.song_index].getName();

			if (this.song_index == (this.songs.size - 1), {
				this.next_song_label.string = "";
			}, {
				this.next_song_label.string = this.songs[this.song_index + 1].getName();
			});
		}, AppClock).start;
	}

	// We use the FC-300 in CONTROL mode, the second on the list of modes on the
	// device after STANDARD, selected by the MODE button on the right. In this
	// mode the factory default map of the controller is:
	//
	// |   Label     |  number  |
	// +-------------+----------+
	// | MODE DOWN   | not sent |
	// | MODE UP     | not sent |
	// | CTL 1       | 80       |
	// | CTL 2       | 81       |
	// | 1/6         | 65       |
	// | 2/7         | 66       |
	// | 3/8         | 67       |
	// | 4/9         | 68       |
	// | 5/10        | 69       |
	// | EXP PEDAL 1 | 7        |
	// | EXP PEDAL 2 | 1        |
	// +-------------+----------+
	onConnectFC300 { | do_connect |
		if (do_connect, {
			var midi_port;
			midi_port = MIDIIn.findPort(fc_300_midi_name, fc_300_midi_name);
			if (midi_port.notNil, {
				fc_300_midi_func = MIDIFunc.cc({ | value, number, channel, source_id |
					switch (number,
						80, { this.nextSong(); },        // CTL 1
						81, { this.previousSong(); },    // CTL 2
						65, { this.resetTapTempo(); },   // 1/6
						66, { this.stopSong(); },        // 2/7
						67, { this.restartSong(); },     // 3/8
						68, { this.startSong(); },       // 4/9
						69, { this.tap(); },             // 5/10
						7, { /* set cue volume */ },
						1, { /* set mains volume */ }).value;
				}, srcID: midi_port.uid);
				fc_300_midi_button.value = 1;
			}, {
				"MIDIIn unable to find Roland FC-300".postln;
				fc_300_midi_button.value = 0;
			});
		}, {
			fc_300_midi_func.free;
		});
	}

	onConnectMinitaur { | do_connect |
		minitaur_midi_out = MIDIOut.newByName(minitaur_midi_device_name, minitaur_midi_port_name, false);
		if (minitaur_midi_out.notNil, {
			minitaur_midi_button.value = 1;
		}, {
			"MIDIOut unable to find Minitaur".postln;
			minitaur_midi_button.value = 0;
		});
	}

	updateLeadInLabel { | v |
		Task.new({
			this.lead_in_label.string = v;
			if (v == "S", {
				this.lead_in_label.stringColor = Color.red;
			}, {
				if (v == "P", {
					this.lead_in_label.stringColor = Color.green;
				}, {
					this.lead_in_label.stringColor = Color.black;
				});
			});
		}, AppClock).start;
	}
}