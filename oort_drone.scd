// Oort Droner
//
// ==================================== Setup
(
s.options.memSize = 16384;
s.boot;
s.doWhenBooted({
MIDIIn.connectAll(false);

DroneMixer.setup(s);

~drone_mixer = DroneMixer.new;

~pc44 = FaderFoxPC44.new;
~pc44.on_button_change = { | button_number, value |
	switch (button_number,
			1, { if (value == 1, { ~drone_mixer.play; }, { ~drone_mixer.pause; }); }
	).value;
};
~pc44.on_knob_change = { | knob_number, value |
		if (knob_number <= 64, {
			var drone_channel, knob_index;
			if (knob_number <= 32, {
				drone_channel = (knob_number - 1) % 8;
				knob_index = ((knob_number - 1) / 8).asInt;
			}, {
				drone_channel = ((knob_number - 1) % 8) + 8;
				knob_index = ((knob_number - 32) / 8).asInt;
			});
			if (drone_channel < ~drone_mixer.numberOfChannels, {
				switch (knob_index,
					0, { ~drone_mixer.setChannelDuration(drone_channel, value); },
					1, { ~drone_mixer.setChannelPeriod(drone_channel, value); },
					2, { ~drone_mixer.setChannelTone(drone_channel, 0, value); },
					3, { ~drone_mixer.setChannelTone(drone_channel, 1, value); }
				).value;
			});
		}, {
			switch (knob_number,
				65, { ~drone_mixer.setMasterVolume(value); },
				66, { ~drone_mixer.setReverbMix(value); },
				67, { ~drone_mixer.setDelayTime(value); },
				68, { ~drone_mixer.setDelayMix(value); }
			).value;
		});
};

~uc44 = FaderFoxUC44.new(Point(~pc44.getWindowSize.width + 2, 0));
~uc44.on_slider_change = { | slider_number, value |
	if (slider_number <= ~drone_mixer.numberOfChannels, {
		~drone_mixer.setChannelAmp(slider_number - 1, value);
	});
};
~uc44.on_green_button_change = { | button_number, value |
	if (button_number <= ~drone_mixer.numberOfChannels, {
		// Button should be "off" to mute channel.
		~drone_mixer.setChannelMuted(button_number - 1, value == 0);
	});
};
});
)

// ==================================== Teardown
(
~uc44.free;
~pc44.free;
~drone_mixer.free;

DroneMixer.teardown;
)