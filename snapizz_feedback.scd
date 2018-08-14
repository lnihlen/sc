// From SC slack, 11 Aug 2018

(
{
    var snd, string, delay;
    string = { |freq|
        var delay;
        delay = freq.reciprocal;
        Pluck.ar(SinOsc.ar(Line.ar(1000, 50, 0.01)) * Env.perc(0.001, 0.01).ar, Impulse.ar(0), delay, delay, 5, 0.5)
    };
    snd = string.(100) + string.(150) + string.(200);
    snd = snd + (HPF.ar(LocalIn.ar(1), 3000) * -50.dbamp);
    snd = (snd * 40).tanh;
    snd = RLPF.ar(snd, 3000, 0.5);
    snd = (snd * 40).tanh;
    snd = RLPF.ar(snd, 500, 0.5);
    snd = (snd * 40).tanh;
    snd = BHiShelf.ar(snd, 3200, 1, -3.0);
    snd = LeakDC.ar(snd);
    LocalOut.ar(snd);
    snd = DelayC.ar(snd, 0.1, SinOsc.kr(2, [0, 1pi]).range(0, 1e-4));
    // uncomment for reverb
    //snd = snd + (NHHall.ar(snd, 1) * -5.dbamp);
    snd * -20.dbamp;
}.play();
)

Quarks.gui;

// First version, less interesting transition to feedback
(
{
  var snd, string, delay;
  string = { |freq|
    var delay;
    delay = freq.reciprocal;
    Pluck.ar(SinOsc.ar(Line.ar(1000, 50, 0.01)) * Env.perc(0.001, 0.01).ar, Impulse.ar(0), delay, delay, 8, 0.3)
  };
  snd = string.(100) + string.(150) + string.(200);
  snd = snd + (LocalIn.ar(1) * -50.dbamp);
  snd = (snd * 40).tanh;
  snd = RLPF.ar(snd, 3000, 0.5);
  snd = (snd * 40).tanh;
  snd = RLPF.ar(snd, 500, 0.5);
  snd = (snd * 40).tanh;
  snd = BHiShelf.ar(snd, 3200, 1, -3.0);
  LocalOut.ar(snd);
  snd = DelayC.ar(snd, 0.1, SinOsc.kr(2, [0, 1pi]).range(0, 1e-4));
  // uncomment for reverb
  //snd = snd + (NHHall.ar(snd, 1) * -5.dbamp);
  snd * -20.dbamp;
}.play;
)