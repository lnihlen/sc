FaderFoxUC44 {
	var win;
	var midi_func;

	// We keep the UI controls in these lists to allow both polling for state
	// when the user presses the "snapshot" button in the UI, as well as for
	// direct setting of values via MIDI.
	var rotary_knobs;
	var rotary_knob_buttons;
	var gray_buttons;
	var green_buttons;
	var sliders;
	var midi_button;

	// Function called on slider change with | slider_number (1-16), value ([0..1]) |
	var <>on_slider_change;

	// Function called on button change with | button_number (1-16), value (0, 1) |
	var <>on_green_button_change;
	var <>on_gray_button_change;

	*new { | window_origin_point = nil |
		^super.new.init(window_origin_point);
	}

	init { | window_origin_point = nil |
		var slider, button, slider_height, button_size, slider_channel_view, bg_gray,
		slider_channel_view_size, view_x, slider_number, knob_number, text, knob, knob_size,
		rotary_knob_view, rotary_knob_view_size, rotary_value_label_size, control_buttons_view,
		win_size;

		var pad_size = 2;
		var slider_width = 48;
		if (window_origin_point.isNil, { window_origin_point = Point(0, 0); });

		rotary_knobs = List.new;
		rotary_knob_buttons = List.new;
		gray_buttons = List.new;
		green_buttons = List.new;
		sliders = List.new;

		slider_height = slider_width * 4;
		knob_size = slider_width;
		button_size = slider_width / 2;
		rotary_value_label_size = Size(slider_width, (slider_width * 3) / 4);
		rotary_knob_view_size = Size(
			slider_width + (pad_size * 2),
			rotary_value_label_size.height + knob_size + button_size + (4 * pad_size));
		slider_channel_view_size = Size(
			slider_width + (pad_size * 2),
			slider_height + (7 * button_size) + (pad_size * 8));
		win_size = Size(
			(slider_channel_view_size.width * 16) + (17 * pad_size),
			slider_channel_view_size.height + (3 * pad_size) + rotary_knob_view_size.height);

		win = Window("FakerFox UC44", Rect(
			window_origin_point.x,
			window_origin_point.y,
			win_size.width,
			win_size.height), false, false).front;
		bg_gray = Color.new(0.8, 0.8, 0.8);

		// Construct 8 rotary knobs at top left.
		view_x = pad_size;
		knob_number = 1;
		8.do({
			rotary_knob_view = CompositeView.new(win, Rect(
				view_x,
				pad_size,
				rotary_knob_view_size.width,
				rotary_knob_view_size.height));
			rotary_knob_view.background = bg_gray;

			text = StaticText.new(rotary_knob_view, Rect(
				pad_size,
				pad_size,
				rotary_value_label_size.width,
				rotary_value_label_size.height));
			text.font = Font("Courier New", rotary_value_label_size.height);
			text.stringColor = Color.white;
			text.align = \right;
			text.string = "00";
			knob = Knob.new(rotary_knob_view, Rect(
				pad_size,
				text.bounds.bottom + pad_size,
				knob_size,
				knob_size));
			rotary_knobs.add(knob);
			button = Button.new(rotary_knob_view, Rect(
				pad_size,
				knob.bounds.bottom + pad_size,
				knob_size,
				button_size));
			button.string = "send " + knob_number;
			rotary_knob_buttons.add(button);

			knob_number = knob_number + 1;
			view_x = view_x + rotary_knob_view_size.width + pad_size;
		});

		// Construct control buttons and window label in remaining space top right.
		control_buttons_view = CompositeView.new(win, Rect(
			view_x + pad_size,
			pad_size,
			win_size.width - (view_x + (2 * pad_size)),
			rotary_knob_view_size.height));
		control_buttons_view.background = bg_gray;
		button = Button.new(control_buttons_view, Rect(
			button_size,
			(rotary_knob_view_size.height / 2) - (button_size / 2),
			button_size * 3,
			button_size));
		button.string = "snapshot";
		button.action = { this.sendUISnapshot(); };
		midi_button = Button.new(control_buttons_view, Rect(
			button_size * 5,
			(rotary_knob_view_size.height / 2) - (button_size / 2),
			button_size * 5,
			button_size));
		midi_button.states = [["connnect MIDI"], ["disconnect MIDI"]];
		midi_button.action = { | v | this.onConnectMIDI(v.value == 1) };

		// Construct 16 sliders across bottom.
		view_x = pad_size;
		slider_number = 1;
		16.do({
			slider_channel_view = CompositeView.new(win, Rect(
				view_x,
				rotary_knob_view_size.height + (2 * pad_size),
				slider_channel_view_size.width,
				slider_channel_view_size.height));
			slider_channel_view.background = bg_gray;

			button = Button.new(slider_channel_view, Rect(
				pad_size + (slider_width / 2) - (button_size / 2),
				pad_size + (button_size / 2),
				button_size,
				button_size));
			button.states = [
				["off", Color.white, Color.black],
				["on", Color.white, Color.gray]];
			button.name = slider_number;
			button.action = { | v | this.onUIGrayButtonChange(v.name.asInteger, v.value); };
			gray_buttons.add(button);

			button = Button.new(slider_channel_view, Rect(
				button.bounds.left,
				button.bounds.bottom + pad_size + (button_size / 2),
				button_size,
				button_size));
			button.states = [
				["off", Color.black, Color.new(0.4, 1.0, 0.4)],
				["on", Color.black, Color.new(0.7, 1.0, 0.7)]];
			button.name = slider_number;
			button.action = { | v | this.onUIGreenButtonChange(v.name.asInteger, v.value); };
			green_buttons.add(button);

			text = StaticText.new(slider_channel_view, Rect(
				pad_size,
				button.bounds.bottom + (pad_size * 2) + (button_size / 2),
				slider_width,
				button_size));
			text.string = slider_number;
			text.align = \center;
			text.font = Font.new("Courier New", button_size);

			slider = Slider.new(slider_channel_view, Rect(
				pad_size,
				text.bounds.bottom + (pad_size * 2) + (button_size / 2),
				slider_width,
				slider_height));
			slider.name = slider_number;
			slider.action = { | v | this.onUISliderChange(v.name.asInteger, v.value); };
			sliders.add(slider);

			text = StaticText.new(slider_channel_view, Rect(
				pad_size,
				slider.bounds.bottom + (pad_size * 2) + (button_size / 2),
				slider_width,
				button_size));
			text.string = slider_number;
			text.align = \center;
			text.font = Font.new("Courier New", button_size);

			slider_number = slider_number + 1;
			view_x = view_x + slider_channel_view_size.width + pad_size;
		});

		if (this.isPhysicalDeviceConnected(), { this.onConnectMIDI(true); });
	}

	onUISliderChange { | slider_number, value |
		this.on_slider_change.value(slider_number, value);
	}

	onUIGreenButtonChange { | button_number, value |
		this.on_green_button_change.value(button_number, value);
	}

	onUIGrayButtonChange { | button_number, value |
		this.on_gray_button_change.value(button_number, value);
	}

	sendUISnapshot {
		// rotary_knob_buttons.do()
		green_buttons.do({ | e, i | this.onUIGreenButtonChange(i + 1, e.value); });
		gray_buttons.do({ | e, i | this.onUIGrayButtonChange(i + 1, e.value); });
		sliders.do({ | e, i | this.onUISliderChange(i + 1, e.value); });
	}

	onConnectMIDI { | do_connect |
		if (do_connect, {
			var midi_port;
			midi_port = MIDIIn.findPort("Faderfox UC44", "Faderfox UC44");
			if (midi_port.notNil, {
				midi_func = MIDIFunc.cc({ | value, number, channel, source_id |
					var app_action = case
					// sliders number 16-31
					{ number >= 16 && number <= 31 } {
						{ sliders[number - 16].valueAction = value / 127.0; }
					}
					// green buttons 32-47
					{ number >= 32 && number <= 47 } {
						{ green_buttons[number - 32].valueAction = value / 127.0; }
					}
					// gray buttons momentary 48-63
					{ number >= 48 && number <= 63 } {
						{ gray_buttons[number - 48].valueAction = value / 127.0; }
					};
					if (app_action.notNil, { Task.new(app_action, AppClock).start });
				}, srcID: midi_port.uid);
				midi_button.value = 1;
			}, {
				"MIDIIn unable to find Faderfox UC44".postln;
				midi_button.value = 0;
			});
		}, {
			midi_func.free;
		});
	}

	// Returns boolean, if true, PC44 is detected.
	isPhysicalDeviceConnected {
		^(MIDIIn.findPort("Faderfox UC44", "Faderfox UC44").notNil);
	}

	getWindowSize {
		^win.bounds.size;
	}

	free {
		win.close;
		if (midi_func.notNil, { midi_func.free; });
	}
}