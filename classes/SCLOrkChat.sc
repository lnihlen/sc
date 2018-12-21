// Headless chat server. Sits on a port, respoding to a couple of different messages
// '/chatSignIn' - [ nickName, receivePort ]
//   Registers a new client. Server will respond with an unique userId at
//   /chatSignInComplete on provided port.
//
// '/chatGetAllClients' - [ receivePort ]
//   Will respond with a list of all signed in clients and their userId at
//   /chatSetAllClients.
//
// '/chatPing - [ userId ]
//   Needs to be called every n seconds or client will be considered to have timed out.
//   Server will respond at /chatPong with a timeOut in seconds before Ping needs to
//   be provided again.
//
// '/chatSendMessage' -
//   [ senderId, \recipients, recipientId0, ..., \message, type, contents ]
//   Send a chat message. RecipientId can be 0 to send to all connected clients.
//   Will send an \echo type message back to /chatReceive on sending client.
//
// '/chatChangeNickName' - [ userId, nickName ]
//   Updates nickname to provided one. Will inform other clients at chatChangeClient.
//
// '/chatSignOut' - [ userId, receivePort ]
//   Tidy cleanup, server won't respond, but will remove user.
SCLOrkChatServer {
	const <defaultListenPort = 7707;
	const clientPingTimeout = 5.0;

	var listenPort;

	// Map of individual NetAddr objects to userIds.
	var <userIdMap;
	// Map of userIds to nicknames.
	var <nickNameMap;
	var userSerial;

	var signInOscFunc;
	var getAllClientsOscFunc;
	var pingOscFunc;
	var sendMessageOscFunc;
	var changeNickNameOscFunc;
	var signOutOscFunc;

	*new { | listenPort = 7707 |
		^super.newCopyArgs(listenPort).init;
	}

	init {
		userIdMap = Dictionary.new;
		nickNameMap = Dictionary.new;
		userSerial = 0;

		signInOscFunc = OSCFunc.new({ | msg, time, addr |
			var nickName = msg[1];
			var clientPort = msg[2];
			var clientAddr = NetAddr.new(addr.ip, clientPort);
			var userId = userIdMap.atFail(clientAddr, {
				userSerial = userSerial + 1;
				userSerial;
			});
			// Update user maps.
			userIdMap.put(clientAddr, userId);
			nickNameMap.put(userId, nickName);

			clientAddr.sendMsg('/chatSignInComplete', userId);

			// Send new client announcement to all connected clients.
			this.prSendAll(this.prChangeClient(\add, userId, nickName));
		},
		path: '/chatSignIn',
		recvPort: listenPort
		);

		getAllClientsOscFunc = OSCFunc.new({ | msg, time, addr |
			var receivePort = msg[1];
			var clientAddr = NetAddr.new(addr.ip, receivePort);
			var clientArray = ['/chatSetAllClients',
				nickNameMap.size] ++ nickNameMap.getPairs;
			clientAddr.sendRaw(clientArray.asRawOSC);
		},
		path: '/chatGetAllClients',
		recvPort: listenPort
		);

		pingOscFunc = OSCFunc.new({ | msg, time, addr |
			/* TODO */
		},
		path: '/chatPing',
		recvPort: listenPort
		);

		//   [ senderId, \recipients, recipientId0, ..., \message, type, contents ]
		sendMessageOscFunc = OSCFunc.new({ | msg, time, addr |
			var senderId, recipients, index, sendMessage;
			senderId = msg[1];
			recipients = Array.new(msg.size - 6);
			index = 3;
			while ({ msg[index] != \message }, {
				recipients.add(msg[index]);
				index = index + 1;
			});
			sendMessage = this.prChatMessage(
				senderId, recipients, msg[index + 1], msg[index + 2]);
			// If first recipient is 0 we send to all.
			if (recipients[0] == 0, {
				this.prSendAll(sendMessage);
			}, {
				recipients.do({ | userId, index |
					userIdMap.at(userId).sendRaw(sendMessage);
				});
			});
		},
		path: '/chatSendMessage',
		recvPort: listenPort
		);

		// '/chatChangeNickName' - [ userId, nickName ]
		changeNickNameOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId, nickName;
			userId = msg[1];
			nickName = msg[2];
			nickNameMap.put(userId, nickName);
			this.prSendAll(this.prChangeClient(\rename, userId, nickName));
		},
		path: '/chatChangeNickName',
		recvPort: listenPort
		);

		signOutOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId, receivePort, clientAddr;
			userId = msg[1];
			receivePort = msg[2];
			clientAddr = NetAddr.new(addr.ip, receivePort);
			userIdMap.remove(clientAddr);
			nickNameMap.remove(userId);
		},
		path: '/chatSignOut',
		recvPort: listenPort
		);

		^this;
	}

	prSendAll { | msgArray |
		userIdMap.keys.do({ | clientAddr, index |
			clientAddr.sendRaw(msgArray);
		});
	}

	prChangeClient { | type, userId, nickName |
		^['/chatChangeClient',
			type,
			userId,
			nickName].asRawOSC;
	}

	prChatMessage { | senderId, recipients, type, contents  |
		var message = Array.new(recipients.size + 6);
		message.add('/chatReceive');
		message.add(senderId);
		message.add(\recipients);
		recipients.do({ | recipient, index |
			message.add(recipient);
		});
		message.add(\message);
		message.add(type);
		message.add(contents);
		^message.asRawOSC;
	}
}

// Client code. Sets up shop on a listening port, gets responses from the server.
// '/chatSignInComplete - [ userId ]
//   Server providing unique userId and acknowledging sign-in.
//
// '/chatSetAllClients - [ n, id0, nickname0, id1, nickname1, ... ]
//   Server providing a list of n active clients in pairs of (id, nickname)
//
// '/chatPong - [ userId, timeOut ]
//   Server providing a keepalive notification, and a timeout deadline for the next
//   ping from the client.
//
// '/chatChangeClient - [ changeType, userId, nickname ]
//   Server updating state of one of the other clients. changeType is one of \add,
//   \remove, or \rename.
//
// '/chatReceive -
//    [ senderId, \recipients, recipientId0, .., \message, type, contents]
//   Server sending a chat message from another client back. recipientId can be 0
//   to indicate that this was a broadcast message.
//
SCLOrkChatClient {
	const defaultListenPort = 7705;

	var <nickName;  // self-assigned nickname, can be changed.
	var serverNetAddr;
	var listenPort;

	var <userDictionary;  // map of userIds to values.
	var <isConnected;  // state of connection to server.
	var <userId;  // server-assigned userId.

	var signInCompleteOscFunc;
	var setAllClientsOscFunc;
	var pongOscFunc;
	var changeClientOscFunc;
	var receiveOscFunc;

	*new { | nickName, serverNetAddr, listenPort = 7705 |
		^super.newCopyArgs(nickName, serverNetAddr, listenPort).init;
	}

	init {
		userDictionary = Dictionary.new;
		isConnected = false;

		signInCompleteOscFunc = OSCFunc.new({ | msg, time, addr |
			// TODO: Can double-check isConnected to be false here, throw
			// error if receiving duplicate signInComplete message.
			isConnected = true;
			userId = msg[1];
			// Send a request for a list of all connected clients,
			// as well as our first ping message.
			serverNetAddr.sendMsg('/chatGetAllClients', userId);
			serverNetAddr.sendMsg('/chatPing', userId);
		},
		path: '/chatSignInComplete',
		recvPort: listenPort
		);

		setAllClientsOscFunc = OSCFunc.new({ | msg, time, addr |
			var num, id;
			userDictionary.clear;
			num = msg[1];
			msg.do({ | i, item |
				if (i >= 2, {
					if (i % 2 == 0, {
						id = item;
					}, {
						userDictionary.put(id, item);
					});
				});
			});
			// TODO: Can double-check size and throw error if mismatch.
		},
		path: '/chatSetAllClients',
		recvPort: listenPort
		);

		pongOscFunc = OSCFunc.new({ | msg, time, addr |
			var serverTimeout = msg[2];

			// TODO: cmd-period survival - maybe just send
			// a fresh ping?
			SystemClock.sched(serverTimeout - 1, {
				serverNetAddr.sendMsg('/chatPing', userId);
			});
		},
		path: '/chatPong',
		recvPort: listenPort
		);

		changeClientOscFunc = OSCFunc.new({ | msg, time, addr |
			var changeType, id, nickname;
			changeType = msg[1];
			id = msg[2];
			nickname = msg[3];
			switch (changeType,
				\add, {
					userDictionary.put(id, nickname);
				},
				\remove, {
					userDictionary.removeAt(id);
				},
				\rename, {
					userDictionary.put(id, nickname);
				},
				{ "unknown change ordered to client user dict.".postln; });
		},
		path: '/chatChangeClient',
		recvPort: listenPort
		);

		//    [ senderId, \recipients, recipientId0, .., \message, type, contents]
		receiveOscFunc = OSCFunc.new({ | msg, time, addr |
			var chatMessage, index;
			chatMessage = ChatMessage.new;
			index = 2;
			chatMessage.senderId = msg[1];
			chatMessage.recipientIds = Array.new(msg.size - 5);
			while ({ msg[index] != \message }, {
				chatMessage.recipientIds = chatMessage.recipientIds.add(msg[index]);
				index = index + 1;
			});
			chatMessage.type = msg[index + 1];
			chatMessage.contents = msg[index + 2];
		},
		path: '/chatReceive',
		recvPort: listenPort
		);

		// Now that handlers are set up we can send the sign-in message to
		// the chat server, registering us for future callbacks.
		serverNetAddr.sendMsg('/chatSignIn', nickName, listenPort);
	}

	free {
		serverNetAddr.sendMsg('/chatSignOut', userId, listenPort);

		signInCompleteOscFunc.free;
		setAllClientsOscFunc.free;
		pongOscFunc.free;
		changeClientOscFunc.free;
		receiveOscFunc.free;
	}

	nickname_ { | newNick |
		nickName = newNick;
		// TODO
	}


}


// Wrapper class around message format, for (de)-serializing messages
// from/to OSC messages, with access to data members.
ChatMessage {
	// Server-assigned unique sender identifier.
	var <>senderId;

	// Array of recipient Ids, if [ 0 ] it's a broadcast message.
	var <>recipientIds;

	// One of:
	//   \plain - normal chat message, broadcast
	//   \director - special director formatting
	//   \system - a system message
	//   \shout - a message with special highlighting (blinking until clicked)
	//   \code - source code sharing
	//   \echo - repeating what you said in the chat window, for context
	//   \whisper - targeted message, only some recipients.
	var <>type;

	// Plaintext contents of chat message, string.
	var <>contents;

	// Human-readable mapping of sender name, string. (not part of wire message)
	var <>senderName;

	*new {
		^super.new.init;
	}

	init {
	}

	// TODO: toOscMessage;
}

ChatMessageView : View {
	var labelStaticText;
	var actionButton;
	var contentsTextView;

	*new { | chatMessage, messageIndex |
		^super.new.init(chatMessage, messageIndex);
	}

	init { | chatMessage, messageIndex |
		this.layout = HLayout.new(
			VLayout.new(
				labelStaticText = StaticText.new(),
				actionButton = Button.new(),
				nil
			),
			contentsTextView = StaticText.new(),
			nil
		);

		// Wire up functionality common to all messages.
		labelStaticText.string = chatMessage.senderName ++ ":";
		contentsTextView.string = chatMessage.contents;

		// Styleize the item based on message type.
		switch (chatMessage.type,
			\plain, {
				actionButton.visible = false;
				if ((messageIndex % 2) == 0, {
					this.background = Color.new(0.9, 0.9, 0.9);
				}, {
					this.background = Color.new(0.8, 0.8, 0.8);
				});
			},
			\director, {
				actionButton.visible = false;
			},
			\system, {
				actionButton.visible = false;
			},
			\shout, {
				actionButton.visible = false;
			},
			\code, {
				actionButton.visible = true;
				actionButton.string = "Copy";
			},
			{ "ChatItemView got unknown chatMessage.type!".postln; }
		);
	}
}

SCLOrkChat {
	const chatUiUpdatePeriodSeconds = 0.2;

	var nickName;
	var isDirector;
	var quitTasks;

	var window;
	var chatItemScrollView;
	var sendTextField;

	var chatMessageQueue;
	var chatMessageQueueSemaphore;
	var autoScroll;
	var chatMessageIndex;
	var updateChatUiTask;

	*new { | name = nil, asDirector = false |
		^super.new.init(name, asDirector);
	}

	init { | name, asDirector |
		nickName = name;
		isDirector = asDirector;
		quitTasks = false;

		this.prConstructUiElements();
		this.prConnectChatUiUpdateLogic();
		window.front;
	}

	prConstructUiElements {
		var scrollCanvas;

		// By default we occupy the right quarter of the screen.
		window = Window.new("chat",
			Rect.new(Window.screenBounds.right, 0,
				Window.screenBounds.width / 4,
				Window.screenBounds.height)
		);
		window.alwaysOnTop = true;
		// window.userCanClose = false;
		window.layout = VLayout.new(
			HLayout.new(
				chatItemScrollView = ScrollView.new()
			),
			HLayout.new(
				sendTextField = TextField.new()
			)
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout(nil);
		chatItemScrollView.canvas = scrollCanvas;
	}

	prEnqueueChatMessage { | chatMessage |
		chatMessageQueueSemaphore.wait;
		chatMessageQueue.add(chatMessage);
		chatMessageQueueSemaphore.signal;
	}

	prConnectChatUiUpdateLogic {
		chatMessageQueue = RingBuffer.new(16);
		chatMessageQueueSemaphore = Semaphore.new(1);
		autoScroll = true;
		chatMessageIndex = 0;

		updateChatUiTask = SkipJack.new({
			var addedElements = false;
			chatMessageQueueSemaphore.wait;
			while ({ chatMessageQueue.size > 0 }, {
				var chatMessage, chatMessageView;

				chatMessage = chatMessageQueue.pop;
				chatMessageQueueSemaphore.signal;

				chatMessageView = ChatMessageView.new(chatMessage, chatMessageIndex);
				chatMessageIndex = chatMessageIndex + 1;
				chatItemScrollView.canvas.layout.add(chatMessageView);
				addedElements = true;

				chatMessageQueueSemaphore.wait;
			});
			chatMessageQueueSemaphore.signal;

			// Wait a short while before scrolling the view to the bottom, or the
			// new layout dimensions will not have been computed, so the view won't
			// always make it to the new bottom when it scrolls.
			if (addedElements and: { autoScroll }, {
				AppClock.sched(chatUiUpdatePeriodSeconds / 2, {
					chatItemScrollView.visibleOrigin = Point.new(0,
						chatItemScrollView.canvas.bounds.height -
						chatItemScrollView.bounds.height
					);
				});
			});
		},
		dt: chatUiUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "UpdateChatUiTask",
		clock: AppClock,
		autostart: true
		);
	}
}