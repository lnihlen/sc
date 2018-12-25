(
OSCFunc.trace(false);
~longString = "I had a bit of time today to work on setting up the studio, enough so that I could get the computer going and make some noise with the modular synth gear I have plus supercollider. Then it was time to dig in to the sclork homework. I made my first Quark repository, for the SCLOrkChat program. One of the things I had noticed was that I was building up increasingly large comment blocks at the top of each object, so learning the syntax of the Supercollider help files";
~shorty = "s";
~chatServer = SCLOrkChatServer.new;
~uiChatClient = SCLOrkChatClient.new(
	NetAddr.new("127.0.0.1", SCLOrkChatServer.defaultListenPort));
~uiChatClient.onConnected = {
	~botChatClient = SCLOrkChatClient.new(
		NetAddr.new("127.0.0.1", SCLOrkChatServer.defaultListenPort), 7709);
	~botChatClient.onConnected = {
		SystemClock.play({
			var len = ~longString.size.rand;
			~botChatClient.sendMessage(SCLOrkChatMessage.new(
				~botChatClient.userId, [ 0 ], \plain, ~longString[..len]));
			^5;
		});
		SystemClock.play({
			~botChatClient.sendMessage(SCLOrkChatMessage.new(
				~botChatClient.userId, [ 0 ], \plain, ~shorty));
			^10.rand;
		});
	};
	~botChatClient.connect("bot");
};
~sclorkChat = SCLOrkChat.new("Luke", false, ~uiChatClient);
)

(
var len;
len = ~longString.size.rand;
len.postln;
)

(
OSCFunc.trace(true);
~chatServer = SCLOrkChatServer.new;
~chatClient = SCLOrkChatClient.new(
	NetAddr.new("127.0.0.1", SCLOrkChatServer.defaultListenPort));
~chatClient.onConnected = { | val |
	var msg = SCLOrkChatMessage.new(~chatClient.userId, [ 0 ], \plain,
	"Test test test woohoo.", "luken");
	~chatClient.sendMessage(msg);
};
~chatClient.onMessageReceived = { | msg |
	msg.postln;
};
~chatClient.onUserChanged = { | type, userId, nickName |
	[ "userChange:", type, userId, nickName ].postln;
};
~chatClient.connect("luken");
)
QuarksGui.new