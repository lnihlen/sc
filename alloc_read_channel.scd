Server.supernova;
s.boot;

b = Buffer.new(s);
b.allocReadChannel("/Users/luken/src/audio/field_recordings/17 June 2018/airplane_sfo_to_lon_cont.WAV", channels: []);
b.numChannels;
x = { PlayBuf.ar(2, b, BufRateScale.kr(b), loop: 1) * 0.5 }.play;

b.numChannels;

z = Buffer.new(s);
z.allocReadChannel(Platform.resourceDir +/+ "sounds/a11wlk01.wav", channels: [0]);
z.numChannels;
y = { PlayBuf.ar(1, z, BufRateScale.kr(z), loop: 1) * 0.5 }.play;

z.free; x.free; b.free; y.free;

s.quit;

b = Buffer.new(s);
b.allocReadChannel(Platform.resourceDir +/+ "sounds/a11wlk01.wav", channels: []);
x = { PlayBuf.ar(1, b, BufRateScale.kr(b), loop: 1) * 0.5 }.play;
x.free;
