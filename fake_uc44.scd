(
var win, slider, button, slider_height, button_size, slider_channel_view, bg_gray,
slider_channel_view_size, view_x, slider_number, knob_number, text, knob, knob_size,
rotary_knob_view, rotary_knob_view_size, rotary_value_label_size;

var pad_size = 2;
var slider_width = 48;

bg_gray = Color.new(0.8, 0.8, 0.8);
slider_height = slider_width * 4;
knob_size = slider_width;
button_size = slider_width / 2;
rotary_value_label_size = Size(slider_width, (slider_width * 3) / 4);
rotary_knob_view_size = Size(
	slider_width + (pad_size * 2),
	rotary_value_label_size.height + knob_size + button_size + (4 * pad_size));
slider_channel_view_size = Size(
	slider_width + (pad_size * 2),
	slider_height + (4 * button_size) + (pad_size * 4));

win = Window("FakerFox UC44", Rect(
	10,
	10,
	(slider_channel_view_size.width * 16) + (17 * pad_size),
	slider_channel_view_size.height + (3 * pad_size) + rotary_knob_view_size.height)).front;

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
	button = Button.new(rotary_knob_view, Rect(
		pad_size,
		knob.bounds.bottom + pad_size,
		knob_size,
		button_size));
	button.states = [["send %".postf(knob_number), Color.black, Color.white]];

	knob_number = knob_number + 1;
	view_x = view_x + rotary_knob_view_size.width + pad_size;
});

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
		[slider_number, Color.white, Color.black],
		[slider_number, Color.white, Color.gray]];

	button = Button.new(slider_channel_view, Rect(
		button.bounds.left,
		button.bounds.bottom + pad_size + (button_size / 2),
		button_size,
		button_size));
	button.states = [
		[slider_number, Color.black, Color.new(0.4, 1.0, 0.4)],
		[slider_number, Color.black, Color.new(0.7, 1.0, 0.7)]];

	slider = Slider.new(slider_channel_view, Rect(
		pad_size,
		button.bounds.bottom + (pad_size * 2) + button_size,
		slider_width,
		slider_height));

	slider_number = slider_number + 1;
	view_x = view_x + slider_channel_view_size.width + pad_size;
});

)