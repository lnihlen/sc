// Solitary Bees concept melodies 22 Sep 2018

(
// Maybe a_mod starts with the 16th at -1, and there's a regular one that doesn't.
// B timing is a mess right now, need to play with drums to figure out.
// C??

var a_deg = Pseq([  -1,    0, 3, 1, 2, -1]);
var a_dur = Pseq([0.25, 3.75, 1, 1, 1,  1]);

var b_deg = Pseq([  -1,   0,   0,   -1,    0,     3,    2,    1,    0,   2,    2,   -2,    -2, -1,     0]);
var b_dur = Pseq([0.25, 0.5, 0.5, 0.25, 0.25,  0.25, 0.25, 0.25, 0.25, 1.5, 0.25,  1.0,  0.25,  1,  1.75]);

Pbind(
	\scale,  Scale.minor,
	\degree, Pseq([a_deg, a_deg, b_deg, a_deg, b_deg, a_deg]),
	\dur,    Pseq([a_dur, a_dur, b_dur, a_dur, b_dur, a_dur]),
	\octave, 4,
	\root,   0
).play(TempoClock.new(80 / 60));
)


(
// Notes are a little funny here but rhythym feels right.
var a_deg = Pseq([  11,   11, \rest,  10,    10,   10, \rest,  9,    8,    8,    8,     8,    5,    3,   2,     3, \rest]);
var a_dur = Pseq([0.25, 0.25,   0.5,   1,  0.25, 0.25,   0.5,  1,  0.5, 0.25, 0.25,  0.25, 0.25, 0.25, 0.75, 0.25,  1.25]);

var b_deg = Pseq([   3,   5, \rest,   2,   5]);
var b_dur = Pseq([0.75, 0.5,   1.0, 0.5, 0.5]);

Pbind(
	\scale,  Scale.minor,
	\degree, Pseq([a_deg, b_deg]),
	\dur,    Pseq([a_dur, b_dur]),
	\octave, 4,
	\root,   0
).play(TempoClock.new(90 / 60));

)


LID.findAvailable;