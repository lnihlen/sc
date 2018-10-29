// Class to manage and render a Chat window based on the Utopia quark for peer
// discovery, with some customization for SCLOrk.

// First draft Luke Nihlen, luke.nihlen@gmail.com, 28 October 2018.
SCLOrkChat {
	// How many lines to keep in the chat log before deleting the older
	// elements.
	const peerListUpdatePeriodSeconds = 1;
	const chatTextUpdatePeriodSeconds = 0.2;
	const fontSizePoints = 18;
	// Typically make this some multiple of the fontSizePoints, so specify
	// it that way so the compiler doesn't freak out defining one constant
	// in terms of another.
	const peerListMaxWidthPointMultiple = 8;

	// Utopia variables.
	var <>addrBook;
	var me;
	var hail;
	var chatter;

	// Windowing-specific variables.
	var directorMode;
	var window;
	var chatTextView;
	var peerListView;
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
	var sendersSerialDict;

	// Variables to keep track of the incoming and outgoing users, for
	// appropriate animation of UI elements.
	var updatePeersTask;

	*new { | name = nil, asDirector = false |
		^super.new.init(name, asDirector);
	}

	init { | name = nil, asDirector = false |
		// Initialize Utopia objects first.
		addrBook = AddrBook.new;
		me = this.prGenerateUniquePeer(name);
		hail = Hail.new(addrBook, me: me);
		chatter = Chatter.new(addrBook, post: false);

		// Now construct user-facing objects and GUI.
		directorMode = asDirector;

		this.prConstructUIElements();
		this.prConnectChatTextUpdateLogic();
		this.prConnectPeerUpdateLogic();
		this.prConnectChatterToUI();

		window.front;
	}

	free {
		updateChatTextTask.stop.free;
		updatePeersTask.stop.free;
		window.close;

		// Tear down Utopia Objects.
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
	prGenerateUniquePeer { | name = nil |
		if (name.isNil, {
			name = Pipe.new("whoami", "r").getLine;
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
		window = Window.new("chat -" + this.prHumanReadablePeer(me));
		window.alwaysOnTop = true;
		window.userCanClose = false;
		window.layout = VLayout.new(
			HLayout.new(
				chatTextView = TextView.new(),
				peerListView = ListView.new().maxWidth_(
					peerListMaxWidthPointMultiple * fontSizePoints)
			),
			HLayout.new(
				sendTextField = TextField.new()
			)
		);

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

		updateChatTextTask = Task.new({
			while ({ true }, {
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
							// ??
						}
					).value;

					chatTextQueueSemaphore.wait;
				});
				chatTextQueueSemaphore.signal;

				chatTextUpdatePeriodSeconds.wait;
			});
		}, AppClock).start;
	}

	prConnectPeerUpdateLogic {
		updatePeersTask = Task.new({
			var currentPeerSet, nextPeerSet, leavingPeerSet, arrivingPeerSet;
			var peerChange;
			currentPeerSet = Set.new;

			while ({ true }, {
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
			});
		}, AppClock).start;
	}

	prConnectChatterToUI {
		// First wire up reception of messages, so we listen before we speak :).
		sendersSerialDict = Dictionary.new;

		chatter.addDependant({ | chatter, what, who, chat |
			// Supress local chat echo, as we add local chat to UI on send.
			if (who != me.name, {
				var serial, splitChat, shouldSend, storedSerial;
				shouldSend = false;

				splitChat = chat.asString.split($|);
				serial = splitChat[0].asInteger;

				storedSerial = sendersSerialDict.at(who);
				if (storedSerial.isNil, {
					shouldSend = true;
				}, {
					shouldSend = storedSerial < serial;
				});

				if (shouldSend, {
					sendersSerialDict.put(who, serial);
					splitChat.removeAt(0);
					this.prAddChatLine(splitChat.join("|"),
						this.prHumanReadablePeer(addrBook.at(who)),
						\normal);
				});
			});
		});

		sentTextSerial = 0;
		sendTextField.action_({ | v |
			this.prAddChatLine(v.string, this.prHumanReadablePeer(me), \echo);
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
			chatter.send(sentTextSerial.asString ++ "|" ++ v.string);
			sentTextSerial = sentTextSerial + 1;
			v.string = ""
		});
	}
}
