// Contains MIDI code to connect to a FaderFox PC44, as well
// as UI code to emulate sending, show on screen, save and load
// values for the physical device.

FaderFoxPC44 {
	var win;
	var midi_func;

	// We keep all UI controls to allow for snapshot value writing and MIDI
	// setting of UI values.
	var knobs;
	var buttons;
	var rotary_knob;
	var rotary_knob_button;
	var midi_button;

	var <>ui_rotary_knob_value = 0.0;

	// Function called on knob change with | knob_number (1-68), value ([0..1]) |
	var <>on_knob_change;

	// Function called when button toggled with | button_number (1-8), value (0, 1) |
	var <>on_button_change;

	// Function called when the rotary knob at top is pressed with | value ([0..1]) |
	var <>on_rotary_knob_press;

	*new { | window_origin_point = nil |
		^super.new.init;
	}

	init { | window_origin_point = nil |
		var big_knob_size, rotary_knob_field_size, knob_field_knob_size, win_size,
		view_x, view_y, knob, text, button, label_size, knob_number, button_number,
		knob_field_view, rotary_knob_view, knob_button_view, knob_field_size, knob_button_field_size,
		bg_gray, button_size, pad_size = 4, knob_size = 48;

		// Compute sizes based on knob and padding size.
		label_size = Size(knob_size, knob_size / 2);
		button_size = knob_size / 2;
		knob_field_knob_size = Size((2 * pad_size) + knob_size, (3 * pad_size) + (1.5 * knob_size));
		knob_field_size = Size(
			(8 * knob_field_knob_size.width) + (9 * pad_size),
			(8 * knob_field_knob_size.height) + (9 * pad_size));
		big_knob_size = knob_size * 1.5;
		rotary_knob_field_size = Size(knob_field_size.width,
			big_knob_size + label_size.height + (2 * pad_size));
		knob_button_field_size = Size(knob_field_size.width,
			big_knob_size + button_size + label_size.height + (2 * pad_size));

		win_size = Size(
			knob_field_size.width + (2 * pad_size),
			knob_field_size.height + rotary_knob_field_size.height + knob_button_field_size.height + (4 * pad_size));
		knobs = List.new;
		buttons = List.new;

		bg_gray = Color.new(0.8, 0.8, 0.8);
		if (window_origin_point.isNil, { window_origin_point = Point(0, 0); });

		// Construct Window.
		win = Window("FakerFox PC44", Rect(
			window_origin_point.x,
			window_origin_point.y,
			win_size.width, win_size.height),
			false, false).front;

		// Construct rotary knob field, the big knob at the top.
		rotary_knob_view = CompositeView.new(win,
			Rect(pad_size, pad_size, rotary_knob_field_size.width, rotary_knob_field_size.height));
		rotary_knob_view.background = bg_gray;
		rotary_knob = Knob.new(rotary_knob_view, Rect(
			(rotary_knob_field_size.width / 2) - (big_knob_size / 2),
			rotary_knob_field_size.height - (pad_size + big_knob_size),
			big_knob_size,
			big_knob_size));
		rotary_knob.action = { | v | this.ui_rotary_knob_value = v.value; };
		text = StaticText.new(rotary_knob_view, Rect(
			(rotary_knob_field_size.width / 2) - (big_knob_size / 2),
			pad_size,
			big_knob_size,
			label_size.height));
		text.string = "69 - rotary";
		rotary_knob_button = Button.new(rotary_knob_view, Rect(
			(rotary_knob_field_size.width / 2) + (big_knob_size / 2) + (2 * pad_size),
			rotary_knob_field_size.height - (pad_size + (big_knob_size / 2) + (button_size / 2)),
			button_size,
			button_size));
		rotary_knob_button.action = { | v | this.onUIRotaryKnobSendButtonPressed(); };
		text = StaticText.new(rotary_knob_view, Rect(
			rotary_knob_button.bounds.left + (button_size / 2) - (label_size.width / 2),
			rotary_knob_button.bounds.bottom,
			label_size.width,
			label_size.height));
		text.string = "send";
		text.align = \center;
		button = Button.new(rotary_knob_view, Rect(
			button_size,
			(rotary_knob_field_size.height / 2) - (button_size / 2),
			button_size * 3,
			button_size));
		button.string = "snapshot";
		button.action = { this.sendUISnapshot(); };
		midi_button = Button.new(rotary_knob_view, Rect(
			rotary_knob_field_size.width - (button_size * 6),
			(rotary_knob_field_size.height / 2) - (button_size / 2),
			button_size * 5,
			button_size));
		midi_button.states = [["connnect MIDI"], ["disconnect MIDI"]];
		midi_button.action = { | v | this.onConnectMIDI(v.value == 1) };

		// Construct knob field, the 64 knobs occupying the main body of the UI.
		knob_field_view = CompositeView.new(win, Rect(
			pad_size,
			rotary_knob_view.bounds.bottom + pad_size,
			knob_field_size.width,
			knob_field_size.height));

		view_x = 0;
		view_y = 0;
		knob_number = 1;
		8.do({ | i |
			8.do({ | j |
				var view = CompositeView.new(knob_field_view, Rect(
					view_x, view_y, knob_field_knob_size.width, knob_field_knob_size.height));
				view.background = bg_gray;
				text = StaticText.new(view, Rect(pad_size, pad_size, label_size.width, label_size.height));
				text.string = knob_number;
				knob = Knob.new(view, Rect(pad_size, (knob_size / 2) + pad_size, knob_size, knob_size));
				knob.name = knob_number;
				knob.action = { | v | this.onUIKnobChange(v.name.asInteger, v.value); };
				knobs.add(knob);

				view_x = view_x + knob_field_knob_size.width + pad_size;
				if (j == 3, { view_x = view_x + (2 * pad_size); });
				knob_number = knob_number + 1;
			});
			view_x = 0;
			view_y = view_y + knob_field_knob_size.height + pad_size;
			if (i == 3, { view_y = view_y + (2 * pad_size); });
		});

		// Construct the knob button field, the 4 bigger knobs and 8 bottoms along the bottom
		// of the UI.
		knob_button_view = CompositeView.new(win, Rect(
			pad_size,
			knob_field_view.bounds.bottom + pad_size,
			knob_button_field_size.width,
			knob_button_field_size.height));
		knob_button_view.background = bg_gray;

		view_x = 0;
		view_y = 0;
		button_number = 1;
		4.do({ | i |
			var view = CompositeView.new(knob_button_view, Rect(
				view_x, view_y, (2 * knob_field_knob_size.width) + pad_size, knob_button_field_size.height));
			text = StaticText.new(view, Rect(
				knob_field_knob_size.width + (pad_size / 2) - (big_knob_size / 2),
				pad_size, label_size.width, label_size.height));
			text.string = knob_number;
			knob = Knob.new(view, Rect(
				text.bounds.left, text.bounds.bottom, big_knob_size, big_knob_size));
			knob.name = knob_number;
			knob.action = { | v | this.onUIKnobChange(v.name.asInteger, v.value); };
			knobs.add(knob);

			button = Button.new(view, Rect(
				knob.bounds.left - (button_size / 2), knob.bounds.bottom, button_size, button_size));
			button.states = [
				[button_number, Color.black, Color.new(0.4, 1.0, 0.4)],
				[button_number, Color.black, Color.new(0.7, 1.0, 0.7)]];
			button.name = button_number;
			button.action = { | v | this.onUIButtonChange(v.name.asInteger, v.value); };
			buttons.add(button);

			button_number = button_number + 1;
			button = Button.new(view, Rect(
				knob.bounds.right - (button_size / 2), knob.bounds.bottom, button_size, button_size));
			button.states = [
				[button_number, Color.white, Color.black],
				[button_number, Color.white, Color.gray]];
			button.name = button_number;
			button.action = { | v | this.onUIButtonChange(v.name.asInteger, v.value); };
			buttons.add(button);

			button_number = button_number + 1;
			knob_number = knob_number + 1;

			view_x = view_x + (knob_field_knob_size.width * 2) + (2 * pad_size);
			if (i == 1, { view_x = view_x + (2 * pad_size); });
		});

		if (this.isPhysicalDeviceConnected(), { this.onConnectMIDI(true); });
	}

	onUIKnobChange { | knob_number, value |
		this.on_knob_change.value(knob_number, value);
	}

	onUIButtonChange { | button_number, value |
		this.on_button_change.value(button_number, value);
	}

	onUIRotaryKnobSendButtonPressed {
		this.on_rotary_knob_press.value(this.ui_rotary_knob_value);
	}

	sendUISnapshot {
		knobs.do({ | e, i | this.onUIKnobChange(i + 1, e.value); });
		this.onUIRotaryKnobSendButtonPressed;
		buttons.do({ | e, i | this.onUIButtonChange(i + 1, e.value); });
	}

	getWindowSize {
		^win.bounds.size;
	}

	onConnectMIDI { | do_connect |
		if (do_connect, {
			var midi_port;
			midi_port = MIDIIn.findPort("Faderfox PC44", "Faderfox PC44");
			if (midi_port.notNil, {
				midi_func = MIDIFunc.cc({ | value, number, channel, source_id |
					var app_action = case
					// knobs number 1-68
					{ number >= 1 && number <= 68 } {
						{ knobs[number - 1].valueAction = value / 127.0; }
					}
					// rotary knob number 69, rotary press 70
					{ number == 69 || number == 70 } {
						{  }
					}
					// buttons number 71-78
					{ number >= 71 && number <= 78 } {
						{ buttons[number - 71].valueAction = value / 127.0; }
					};
					if (app_action.notNil, { Task.new(app_action, AppClock).start });
				}, srcID: midi_port.uid);
				midi_button.value = 1;
			}, {
				"MIDIIn unable to find Faderfox PC44".postln;
				midi_button.value = 0;
			});
		}, {
			midi_func.free;
		});
	}

	// Returns boolean, if true, PC44 is detected.
	isPhysicalDeviceConnected {
		^(MIDIIn.findPort("Faderfox PC44", "Faderfox PC44").notNil);
	}

	free {
		win.close;
		if (midi_func.notNil, { midi_func.free; });
	}
}