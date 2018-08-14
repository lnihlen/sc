(
s.boot;
s.doWhenBooted({
	MIDIClient.init;
	MIDIIn.connectAll;
	OortSequencedSong.setup(s);
	o = OortShow.new;
});
)
