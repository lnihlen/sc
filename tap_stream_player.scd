(
~pattern = 	Pbind(
	\scale, Scale.chromatic,
	\octave, 4,
	\root, 0,  // key of C
	\amp, 1.0,
	\degree, Pseq([-1,   0,   \rest, 0,   -1,   0,   \rest, 0,   -1,   0,    \rest, 0,   1,   0,   -1 ], inf),
	\dur,    Pseq([0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,  1.0, 0.25, 0.5, 0.25,   0.5, 1.0, 0.5, 1.0], inf)
);
~stream = ~pattern.asStream;
~player = TapStreamPlayer.new(~stream);

~win = Window("Tap Stream Player", Rect(128, 128, 34, 34)).front;
~button = Button.new(~win, Rect(2, 2, 32, 32));
~button.string = "tap";
~button.action = { ~player.tap(); };

~playing = false;
~player.period_changed = { | p, s |
	if (~playing.not, {
		"starting".postln;
		~player.start;
		~playing = true;
	});
	"new period: % %\n".postf(p, s);
};

)

(
a = (
	\scale: Scale.chromatic,
	\octave: 4,
	\root: 0,
	\amp: 1.0,
	\degree: 1,
	\dur: 10.0
);
b = a.copy;
b.dur = 5.0;
a.play;
)