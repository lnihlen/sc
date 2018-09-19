

// You can create as many Ndefs as you like
// You only need to start playing them once
// Then do not kill anything with Ctrl+Period
Ndef(\a).play;
Ndef(\b).play;



// Plug someting into Ndef(\a)
Ndef(\a, { SinOsc.ar(freq: 43 * 7, mul: 0.1) });
// Change freq or mul above, run line again to hear the change

// Note that freq here is defined as a fundamental freq (43) multiplied by a partial number (from 1 to 32, you choose, as defined by the score of In a Large Open Space)
// For the actual piece, we only change the multiplier partial
// For now, feel free to change whatever to have fun...


// Change crossfade time, keep trying new numbers above
Ndef(\a).fadeTime = 5;



// Change timbre (choose oscillator SinOsc, Saw, Pulse...)
Ndef(\a, { Saw.ar(freq: 43 * 6, mul: 0.1) });


// Modulate mul (amplitude) with another oscillator (SinOsc, Saw, LFSaw, Pulse, LFPulse...)
// Note that range lets you choose a min and max for the amp contour
Ndef(\a, { Saw.ar(freq: 43 * 4, mul: LFSaw.ar(3).range(0, 0.3)) });


Ndef(\a, { Saw.ar(freq: 43 * 4, mul: Pulse.ar(5).range(0, 0.3)) });


// How about a MouseX to control that pulse speed?
Ndef(\a, { Saw.ar(freq: 43 * 4, mul: Pulse.ar(MouseX.kr(1, 15)).range(0, 0.3)) });



// How about an rrand to choose new partials for you? (a new one everytime you run the line again)
Ndef(\a, { Pulse.ar(freq: 43 * rrand(1, 32), mul: SinOsc.ar(MouseX.kr(1, 15)).range(0, 0.3)) });

// Remember, no need to control+period anything with Ndefs! If you do, you need to .play them again

// Now plug something into Ndef(\b), which has been empty up to now
// a bit more elaborate:

(
Ndef(\b, {
	var freq1 = 43 * rrand(1, 32);
	var freq2 = 43 * rrand(1, 32);
	var snd = SinOsc.ar(
		freq: [freq1, freq2], // <- turns synth into stereo, with 2 freqs!!
		mul: [LFSaw, SinOsc].choose.ar(freq: MouseX.kr(8, 1)).range(0, 0.4)
	);
	snd = Mix.ar(snd); // <- we need to mix it down to MONO since we only use 1 speaker in SCLOrk!
});
)

Ndef(\b).fadeTime = 5;
