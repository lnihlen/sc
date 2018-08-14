(
var win, big_knob_size, rotary_knob_field_size, knob_field_knob_size, win_size,
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

bg_gray = Color.new(0.8, 0.8, 0.8);

// Construct Window.
win = Window("FakerFox PC44", Rect(10, win_size.height + 10, win_size.width, win_size.height), false, false).front;

// Construct rotary knob field, the big knob at the top.
rotary_knob_view = CompositeView.new(win,
	Rect(pad_size, pad_size, rotary_knob_field_size.width, rotary_knob_field_size.height));
rotary_knob_view.background = bg_gray;
knob = Knob.new(rotary_knob_view, Rect(
	(rotary_knob_field_size.width / 2) - (big_knob_size / 2),
	rotary_knob_field_size.height - (pad_size + big_knob_size),
	big_knob_size,
	big_knob_size));
text = StaticText.new(rotary_knob_view, Rect(
	(rotary_knob_field_size.width / 2) - (big_knob_size / 2),
	pad_size,
	big_knob_size,
	label_size.height));
text.string = "rotary";
button = Button.new(rotary_knob_view, Rect(
	(rotary_knob_field_size.width / 2) + (big_knob_size / 2) + (2 * pad_size),
	rotary_knob_field_size.height - (pad_size + (big_knob_size / 2) + (button_size / 2)),
	button_size,
	button_size));
text = StaticText.new(rotary_knob_view, Rect(
	button.bounds.left + (button_size / 2) - (label_size.width / 2),
	button.bounds.bottom,
	label_size.width,
	label_size.height));
text.string = "send";
text.align = \center;

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
	button = Button.new(view, Rect(
		knob.bounds.left - (button_size / 2), knob.bounds.bottom, button_size, button_size));
	button.states = [
		[button_number, Color.black, Color.new(0.4, 1.0, 0.4)],
		[button_number, Color.black, Color.new(0.7, 1.0, 0.7)]];
	button_number = button_number + 1;
	button = Button.new(view, Rect(
		knob.bounds.right - (button_size / 2), knob.bounds.bottom, button_size, button_size));
	button.states = [
		[button_number, Color.white, Color.black],
		[button_number, Color.white, Color.gray]];
	button_number = button_number + 1;

	knob_number = knob_number + 1;
	view_x = view_x + (knob_field_knob_size.width * 2) + (2 * pad_size);
	if (i == 1, { view_x = view_x + (2 * pad_size); });
});
)