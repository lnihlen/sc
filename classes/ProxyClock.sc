// A set of client/server proxy clocks. Setting some attributes on the
// server will propagate those attributes to all clients. Note that this
// does not provide actual synchronization of the clocks, only their
// other attributes. For synchronization see SyncTempoClock.sc.
//
// Forwarded attributes/actions:
//   * stop - will stop server proxy clock as well as all connected client
//            clocks. See also stopAtBeat();
//   * tempo - will set tempo on all connected clocks
//   * permanent - will set permanent status on client clocks as well
//   * beatsPerBar - assumed to be called on the containing clock, will
//            set beatsPerBar on server and all clients, modulo propagation
//            delay. If the clocks on server and client are all synchronized
//            in beat, consider using setMeterAtBeat(), which can set
//            beatsPerBar on all clients predictably at a provided beat.
//   * setMeterAtBeat() - for synchronized clocks will work, for
//            unsynchronized clocks unpredictable things may occur.
//   * setTempoAtBeat(), setTempoAtSec() - same as above.
//   * fadeTempo, fadeTempoAtBeat()
//
// Using "has-a" instead of "is-a" to allow flexibility in terms of which
// clock the Proxy maintains. Provides many methods that it forwards to
// the clock for convenience.
ProxyClockServer {
	var <clock;
	var listenPort;

	var clientNetAddrSet;
	var <permanent;

	var registerOscFunc;


	*new { | clock, listenPort = 7703 |
		^super.newCopyArgs(clock, listenPort).init;
	}

	init {
		clientNetAddrSet = Set.new;

		// message format: verb (\add or \remove), port.
		registerOscFunc = OSCFunc.new({ | msg, time, addr |
			var verb, port, netAddr;
			["server", msg, time, addr].postln;
			verb = msg[1];
			port = msg[2];
			netAddr = NetAddr.new(addr.ip, port);
			if (verb == \add, {
				"adding".postln;
				clientNetAddrSet.add(netAddr);
				// Send current tempo and meter values to the new
				// client.
				netAddr.sendRaw(this.prTempoMessage(0.0, clock.tempo));
				netAddr.sendRaw(
					this.prBeatsPerBarMessage(0.0, clock.beatsPerBar));
			}, {
				// verb is assumed to be \remove.
				clientNetAddrSet.remove(netAddr);
			});
		},
		path: '/registerClock',
		recvPort: listenPort
		);
	}

	stop {
		this.prSendAll(this.prStopMessage(0.0));
		clock.stop;
	}

	stopAtBeat { | beat |
		this.prSendAll(this.prStopMessage(beat));
		clock.schedAbs(beat, { clock.stop; });
	}

	tempo {
		^clock.tempo;
	}

	tempo_ { | newTempo |
		this.prSendAll(this.prTempoMessage(0.0, newTempo));
		clock.tempo = newTempo;
	}

	permanent_ { | val |
		/* TODO */
	}

	beatDur {
		^clock.beatDur;
	}

	beatsPerBar {
		^clock.beatsPerBar;
	}

	beatsPerBar_ { | newMeter |
		this.prSendAll(this.prBeatsPerBarMessage(0.0, newMeter));
		clock.sched(0, { clock.beatsPerBar_ = newMeter; });
	}

	setMeterAtBeat { | newMeter, beats |
		this.prSendAll(this.prBeatsPerBarMessage(beats, newMeter));
		clock.setMeterAtBeat(newMeter, beats);
	}

	setTempoAtBeat { | newTempo, beats |
		this.prSendAll(this.prTempoMessage(beats, newTempo));
		clock.setTempoAtBeat(newTempo, beats);
	}

	prSendAll { | msgArray |
		clientNetAddrSet.do({ | netAddr, index |
			netAddr.sendRaw(msgArray);
		});
	}

	prStopMessage { | beat |
		^['/proxyClockControl',
			\stop,
			beat.high32Bits,
			beat.low32Bits].asRawOSC;
	}

	prTempoMessage { | beat, newTempo |
		^['/proxyClockControl',
			\tempo,
			beat.high32Bits,
			beat.low32Bits,
			newTempo.high32Bits,
			newTempo.low32Bits].asRawOSC;
	}

	prBeatsPerBarMessage { | beat, newMeter |
		^['/proxyClockControl',
			\beatsPerBar,
			beat.high32Bits,
			beat.low32Bits,
			newMeter.high32Bits,
			newMeter.low32Bits].asRawOSC;
	}
}

ProxyClockClient {
	var <clock;
	var serverNetAddr;
	var listenPort;

	var controlOscFunc;

	*new { | clock, serverNetAddr, listenPort = 7702 |
		^super.newCopyArgs(clock, serverNetAddr, listenPort).init;
	}

	init {
		// message format: verb, beat64hi, beat64low, [ optional args ]
		controlOscFunc = OSCFunc.new({ | msg, time, addr |
			var verb, beat;
			[ "client", msg, time, addr ].postln;
			verb = msg[1];
			beat = Float.from64Bits(msg[2], msg[3]);
			// Different behavior depending on if beat is for *now* or
			// for some future date.
			if (beat <= 0.0, {
				switch (verb,
					\stop, { this.stop(); },
					\tempo, {
						clock.tempo = Float.from64Bits(msg[4], msg[5]);
					},
					\permanent, {
						this.permanent_ = msg[4];
					},
					\beatsPerBar, {
						var newMeter = Float.from64Bits(msg[4], msg[5]);
						clock.sched(0, { clock.beatsPerBar = newMeter });
					},
					\fadeTempo, {
					/* TODO */
					},
					/* default */ {
						"Invalid verb sent to ProxyClockClient.".postln;
				});
			}, {
				switch (verb,
					\stop, { clock.schedAbs(beat, { this.stop(); }); },
					\tempo, {
						var newTempo = Float.from64Bits(msg[4], msg[5]);
						clock.setTempoAtBeat(beat, newTempo);
					},
					\tempoAtSec, {
						var newTempo = Float.from64Bits(msg[4], msg[5]);
						clock.setTempoAtSec(beat, newTempo);
					},
					\beatsPerBar, {
						var newMeter = Float.from64Bits(msg[4], msg[5]);
						clock.schedAbs(beat, {
							clock.beatsPerBar = newMeter;
						});
					},
					\fadeTempo, {
						/* TODO */
					},
					/* default */ {
						"Invalid verb sent to ProxyClockClient.".postln;
				});
			});
		},
		path: '/proxyClockControl',
		recvPort: listenPort
		);

		// Register with the server.
		serverNetAddr.sendMsg('/registerClock', \add, listenPort);
	}

	free {
	}

	stop {
	}
}

