// Class to manage and render a Chat window based on the Utopia quark for peer
// discovery, with some customization for SCLOrk.

// First draft Luke Nihlen, luke.nihlen@gmail.com, 28 October 2018.
SCLOrkChat {
	const chatVersionString = "v0.2";
	const peerListUpdatePeriodSeconds = 0.5;
	const chatTextUpdatePeriodSeconds = 0.2;
	const tempoUpdatePeriodSeconds = 0.1;
	const fontSizePoints = 16;
	// Typically make this some multiple of the fontSizePoints, so specify
	// it that way so the compiler doesn't freak out defining one constant
	// in terms of another.
	const peerListMaxWidthPointMultiple = 10;
	const windowDefaultWidthPointMultiple = 40;
	const minBeaconClockTempo = 10;
	const maxBeaconClockTempo = 180;

	// Utopia variables.
	var <addrBook;
	var me;
	var hail;
	var chatter;
	var <beaconClock = nil;

	// Windowing-specific variables.
	var directorMode;
	var window;
	var chatTextView;
	var peerListView;
	var beaconClockTempoKnob;
	var beaconClockTempoKnobValueLabel;
	var beaconClockSetTempoButton;
	var beaconClockFadeTempoButton;
	var beaconClockWarpTempoButton;
	var beaconClockTempoValueLabel;
	var beaconClockStatusUpdateTask;
	var restartClockButton;
	var sendTextField;
	var defaultFont;
	var boldFont;
	var italicsFont;

	// Variables to update the chat window in a consistent and thread-safe
	// manner.
	var updateChatTextTask;
	var chatTextQueueSemaphore;
	var chatTextQueue;
	var sentTextSerial;
	var textModePrepend;
	var sendersSerialDict;

	var updatePeersTask;
	var quitTasks;

	var currentPeerSet;
	var nextPeerSet;
	var leavingPeerSet;
	var arrivingPeerSet;
	var peerChange;

	*new { | name = nil, asDirector = false |
		^super.new.init(name, asDirector);
	}

	cmdPeriod {
		if (beaconClock.notNil, {
			beaconClock.stop.free;
			beaconClock = nil;
		});
	}

	init { | name = nil, asDirector = false |
		// Initialize Utopia objects first.
		addrBook = AddrBook.new;
		me = this.prGenerateUniquePeer(name, asDirector);
		hail = Hail.new(addrBook, me: me);
		chatter = Chatter.new(addrBook, post: false);
		directorMode = asDirector;
		CmdPeriod.add(this);
		quitTasks = false;
		currentPeerSet = Set.new;

		// Now construct user-facing objects and GUI.
		this.prConstructUIElements();
		this.prConnectChatTextUpdateLogic();
		this.prConnectPeerUpdateLogic();
		this.prConnectChatterToUI();
		this.prConnectBeaconClockToUI();
		this.prStartBeaconClock();

		window.front;
	}

	free {
		quitTasks = true;
		CmdPeriod.remove(this);

		updateChatTextTask.stop.free;
		updatePeersTask.stop.free;
		beaconClockStatusUpdateTask.stop.free;
		window.close;

		// Tear down Utopia Objects.
		beaconClock.stop;
		chatter.free;
		hail.free;
		addrBook.free;
		me.free;
	}

	// Given a Peer, convert to human readable string name.
	prHumanReadablePeer { | peer |
		^peer.name.asString.split($|)[0];
	}

	// Use username, hostname, epoch, and a large hardware random seed to try and
	// crock up a Peer name for Utopia that has near certain probability of being
	// unique within the AddrBook.
	prGenerateUniquePeer { | name = nil, asDirector = false |
		if (name.isNil, {
			name = Pipe.new("whoami", "r").getLine;
		});
		if (asDirector, {
			name = name + "[D]";
		});
		name = name ++ "|" ++ Pipe.new("uname -n", "r").getLine;
		name = name ++ "|" ++ Date.getDate.stamp ++ "|";
		File.use("/dev/urandom", "rb", { | file |
			8.do({
				var word = file.getInt32;
				name = name ++ word.asHexString;
			});
		});
		^Peer.new(name, NetAddr.localAddr);
	}

	// Call this method to add a line of text to the chatListView. It will enqueue
	// the data to be added to the UI on a UI-safe thread. Note that |text| and |who|
	// are assumed to be regular strings, already human-readable. Supported
	// values of |type| are:
	//
	// \echo:     chat you yourself typed
	// \normal:   normal text, a chat from another user
	// \system:   system announcements, like who logged or out
	// \director: chat from a user in director mode
	//
	prAddChatLine { | text, who, type = \normal |
		chatTextQueueSemaphore.wait;
		chatTextQueue.addFirst([text, who, type]);
		chatTextQueueSemaphore.signal;
	}

	prConstructUIElements {
		var beaconClockCompositeView;
		var beaconClockLabel;
		var bcWidth = peerListMaxWidthPointMultiple * fontSizePoints;
		var bcWidthPad = bcWidth - (2 * pad);
		var pad = 4;
		var bcHeight = fontSizePoints + (pad * 3);

		window = Window.new(
			"chat" + chatVersionString + "-" + this.prHumanReadablePeer(me),
			Rect.new(Window.screenBounds.right - windowDefaultWidthPointMultiple * fontSizePoints, 0,
				windowDefaultWidthPointMultiple * fontSizePoints,
				Window.screenBounds.height)
		);
		window.alwaysOnTop = true;
		window.userCanClose = false;
		window.layout = VLayout.new(
			HLayout.new(
				chatTextView = TextView.new(),
				peerListView = ListView.new().maxWidth_(bcWidth),
				beaconClockCompositeView = CompositeView.new().maxWidth_(
					bcWidth).minWidth_(bcWidth)
			),
			HLayout.new(
				sendTextField = TextField.new()
			)
		);

		beaconClockCompositeView.background = Color.new(0.9, 0.9, 0.9);

		// Construct BeaconClock view outside of layout structure, to avoid
		// having internal UI components stretched by the LayoutViews.
		beaconClockLabel = StaticText.new(beaconClockCompositeView,
			Rect.new(pad, pad, bcWidthPad, fontSizePoints));
		beaconClockLabel.string = "BeaconClock";
		beaconClockLabel.align = \center;
		beaconClockTempoValueLabel = StaticText.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockLabel.bounds.bottom + pad,
				bcWidthPad, bcHeight));
		beaconClockTempoValueLabel.string = "stopped";
		beaconClockTempoValueLabel.align = \center;
		beaconClockTempoValueLabel.background = Color.new(0, 0, 0);
		beaconClockTempoValueLabel.stringColor = Color.new(1, 1, 1);
		restartClockButton = Button.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockTempoValueLabel.bounds.bottom + pad,
				bcWidthPad, bcHeight));
		restartClockButton.string = "Restart Clock";
		beaconClockTempoKnob = Knob.new(beaconClockCompositeView,
			Rect.new(pad, restartClockButton.bounds.bottom + pad,
				bcWidthPad, bcWidth));
		beaconClockTempoKnobValueLabel = StaticText.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockTempoKnob.bounds.bottom - (2 * pad),  // Pull in a little.
				bcWidthPad, bcHeight - pad));
		beaconClockTempoKnobValueLabel.string = "unknown";
		beaconClockTempoKnobValueLabel.align = \center;
		beaconClockSetTempoButton = Button.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockTempoKnobValueLabel.bounds.bottom + pad,
				bcWidthPad, bcHeight));
		beaconClockSetTempoButton.string = "Set Tempo";
		beaconClockFadeTempoButton = Button.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockSetTempoButton.bounds.bottom + pad,
				bcWidthPad, bcHeight));
		beaconClockFadeTempoButton.string = "Fade Tempo";
		beaconClockWarpTempoButton = Button.new(beaconClockCompositeView,
			Rect.new(pad, beaconClockFadeTempoButton.bounds.bottom + pad,
				bcWidthPad, bcHeight));
		beaconClockWarpTempoButton.string = "Warp Tempo";

		defaultFont = Font.new(Font.defaultSansFace, fontSizePoints, false, false);
		boldFont = Font.new(Font.defaultSansFace, fontSizePoints, true, false);
		italicsFont = Font.new(defaultFont.name, fontSizePoints, false, true);

		chatTextView.editable = false;

		peerListView.selectionMode = \none;
		peerListView.font = defaultFont;

		sendTextField.font = defaultFont;
	}

	// Only call these Append methods on the AppClock thread, from the updateChatTextTask.
	prAppendChatColor { | text, color |
		var currentStringSize = chatTextView.string.size;
		chatTextView.setString(text, currentStringSize, 0);
		chatTextView.setStringColor(color, currentStringSize, text.size);
		chatTextView.setFont(defaultFont, currentStringSize, text.size);
	}

	prAppendChatBold { | text |
		var currentStringSize = chatTextView.string.size;
		chatTextView.setString(text, currentStringSize, 0);
		chatTextView.setStringColor(Color.black, currentStringSize, text.size);
		chatTextView.setFont(boldFont, currentStringSize, text.size);
	}

	prAppendChatItalics { | text |
		var currentStringSize = chatTextView.string.size;
		chatTextView.setString(text, currentStringSize, 0);
		chatTextView.setStringColor(Color.black, currentStringSize, text.size);
		chatTextView.setFont(italicsFont, currentStringSize, text.size);
	}

	prConnectChatTextUpdateLogic {
		chatTextQueue = List.new;
		chatTextQueueSemaphore = Semaphore.new(1);

		updateChatTextTask = SkipJack.new({
			chatTextQueueSemaphore.wait;
			while ({ chatTextQueue.size > 0 }, {
				var chatTextElement, text, who, type, currentStringSize;
				chatTextElement = chatTextQueue.pop;
				// We can release the queue mutex for now, as we have some UI to render,
				// no need to block other threads while we do that.
				chatTextQueueSemaphore.signal;

				text = chatTextElement[0];
				who = chatTextElement[1];
				type = chatTextElement[2];

				switch (type,
					\echo, {
						this.prAppendChatColor(who ++ ": " ++ text ++ "\n", Color.gray);
					},
					\normal, {
						this.prAppendChatBold(who ++ ": ");
						this.prAppendChatColor(text ++ "\n", Color.black);
					},
					\system, {
						this.prAppendChatItalics(text ++ "\n");
					},
					\director, {
						this.prAppendChatColor(who ++ ": " ++ text ++ "\n", Color.green);
					}
				).value;

				chatTextView.select(chatTextView.string.size, 0);

				chatTextQueueSemaphore.wait;
			});
			chatTextQueueSemaphore.signal;
		},
		dt: chatTextUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "ChatText",
		clock: AppClock,
		autostart: true);
	}

	prConnectPeerUpdateLogic {
		updatePeersTask = SkipJack.new({
			// Detect changes to our local set of Peers. We make one copy of addrBook at
			// the beginning of the set arithmetic, to avoid missing a race around a peer
			// leaving or arriving during the operations.
			nextPeerSet = addrBook.onlinePeers.asSet;
			leavingPeerSet = currentPeerSet - nextPeerSet;
			arrivingPeerSet = nextPeerSet - currentPeerSet;
			currentPeerSet = nextPeerSet;
			peerChange = false;

			// Make system announcements for each arrival and departure.
			leavingPeerSet.do({ | peer |
				peerChange = true;
				this.prAddChatLine("User" + this.prHumanReadablePeer(peer) +
					"has left the Hail.", nil, \system) });
			arrivingPeerSet.do({ | peer |
				peerChange = true;
				this.prAddChatLine("User" + this.prHumanReadablePeer(peer) +
					"has arrived in the Hail.", nil, \system) });

			if (peerChange, {
				var peerList = SortedList.new(currentPeerSet.size);
				currentPeerSet.do({ | peer |
					peerList.add(this.prHumanReadablePeer(peer));
				});
				peerListView.items = peerList.asArray;
			});
			// Wait to refresh the peer list again.
			peerListUpdatePeriodSeconds.wait;
		},
		dt: peerListUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "PeerList",
		clock: AppClock,
		autostart: true);// Clock functions can return a value for automatic rescheduling.
	}

	prConnectChatterToUI {
		// First wire up reception of messages, so we listen before we speak :).
		sendersSerialDict = Dictionary.new;

		chatter.addDependant({ | chatter, what, who, chat |
			// Supress local chat echo, as we add local chat to UI on send.
			if (who != me.name, {
				// We prepend a serial number to each message sent to workaround
				// a problem where multiple Chatter instances running on the same
				// computer each will receive one copy of all inbound messages for
				// all each instance running on the same computer. So if you have n
				// chat windows open on the same computer, you will recieve n copies
				// of each chat. I suspect this is due to the way that all of the
				// Chatter objects are binding to the same oscPath, but I haven't
				// researched in depth. On the reciever side we keep a dictionary
				// of received serial numbers, and only append new text when it has
				// a novel serial number.
				var serial, splitChat, shouldAdd, storedSerial;
				shouldAdd = false;

				splitChat = chat.asString.split($|);
				serial = splitChat[0].asInteger;
				storedSerial = sendersSerialDict.at(who);
				if (storedSerial.isNil, {
					shouldAdd = true;
				}, {
					shouldAdd = storedSerial < serial;
				});

				if (shouldAdd, {
					var mode;
					sendersSerialDict.put(who, serial);
					if (splitChat[1] == "D", {
						mode = \director;
					}, {
						mode = \normal;
					});
					// Remove serial number and director mode flags from what
					// gets printed.
					splitChat.removeAt(0);
					splitChat.removeAt(0);
					this.prAddChatLine(splitChat.join("|"),
						this.prHumanReadablePeer(addrBook.at(who)),
						mode);
				});
			});
		});

		sentTextSerial = 0;
		if (directorMode, { textModePrepend = "|D|" }, { textModePrepend = "|P|" });

		sendTextField.action_({ | v |
			this.prAddChatLine(v.string, this.prHumanReadablePeer(me), \echo);
			chatter.send(sentTextSerial.asString ++ textModePrepend ++ v.string);
			sentTextSerial = sentTextSerial + 1;
			v.string = ""
		});
	}

	prConvertKnobToTempo {
		^(minBeaconClockTempo + (beaconClockTempoKnob.value *
			(maxBeaconClockTempo - minBeaconClockTempo)));
	}

	prStartBeaconClock {
		if (beaconClock.isNil, {
			beaconClock = BeaconClock.new(addrBook);
		});

		beaconClock.play({
			Task.new({
				beaconClockTempoValueLabel.background = Color.new(1, 1, 1);
				beaconClockTempoValueLabel.stringColor = Color.new(0, 0, 0);
				0.1.wait;
				beaconClockTempoValueLabel.background = Color.new(0, 0, 0);
				beaconClockTempoValueLabel.stringColor = Color.new(1, 1, 1);
			}, AppClock).start;
			1;
		});
	}

	prConnectBeaconClockToUI {
		beaconClockTempoKnob.action = { | v |
			beaconClockTempoKnobValueLabel.string =
			this.prConvertKnobToTempo().asInteger.asString;
		};
		beaconClockTempoKnob.valueAction = 0.2;

		beaconClockSetTempoButton.action = { | v |
			beaconClock.setGlobalTempo(this.prConvertKnobToTempo() / 60.0);
		};

		beaconClockFadeTempoButton.action = { | v |
			beaconClock.fadeTempo(this.prConvertKnobToTempo() / 60.0);
		};

		beaconClockWarpTempoButton.action = { | v |
			beaconClock.warpTempo(this.prConvertKnobToTempo() / 60.0);
		};

		beaconClockStatusUpdateTask = SkipJack.new({
				if (beaconClock.notNil, {
					beaconClockTempoValueLabel.string =
					(beaconClock.tempo * 60.0).asInteger.asString;
				}, {
					beaconClockTempoValueLabel.string = "stopped";
				});
		},
		dt: tempoUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "PeerList",
		clock: AppClock,
		autostart: true);

		restartClockButton.action = { | v |
			this.prStartBeaconClock();
		};
	}
}
