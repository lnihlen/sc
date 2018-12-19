(
~sc = SCLOrkChat.new("Luke");

SystemClock.play({
	var msg = ChatMessage.new;
	msg.type = \plain;
	msg.senderId = 0;
	msg.senderName = "Ligetty";
	msg.contents = "Spent a sort of frustrating, unproductive day up on Santa Clara University campus working on sclork code. Unfortunately I ran against a bug in my network timing code pretty hard for most of the day. Then my laptop crashed in a creative and interesting way, requiring me to literally unplug it and let the battery run down to empty in order to recover it. Since it was only about 15 minutes until the end of the hack session that kind of just put the cherry on the top of the poo pile so I packed it up.

The problem I’m encountering when trying to make my own clock is that the items that I am scheduling, when it’s time to have them execute, I’m supposed to call the awake method, but this method typically yields a value with the ^ operator. I’m running my tasks on the SystemClock and so the yield is treated like an argument to SystemClock asking for a reschedule there instead of on my clock. I don’t know how to intercept those values, and can’t seem to find any reason why the code in Clock.sc works and my code doesn’t.

The less awesome (but perhaps honestly better) solution is to kind of follow the BeaconClock pattern by inheriting from TempoClock and then periodically adjusting the beats value on the parent object to reflect the remote clock.

We had a work holiday party for just the Movies team, and it was cool to bring +1s so Hilary drove over the hill and met me there. It was a good reminder of the fact that I genuinely like everyone on my current team. Super chill, friendly party. We left early because that’s how I roll at parties but I left feeling lucky to work with so many excellent people.";
	~sc.prEnqueueChatMessage(msg);
	^3;
});
)
