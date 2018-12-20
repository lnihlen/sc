(
~ts = TempoClock.new(3.0);
~tc = TempoClock.new(2.0);

~ps = ProxyClockServer.new(~ts);
~pc = ProxyClockClient.new(~tc, NetAddr.new("127.0.0.1", 7703));

~ts.tempo.postln;
~tc.tempo.postln;



)
