package com.mlmqol;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

@Slf4j
@Singleton
class WakeUpSound
{
	private static final String SOUND_FILE = "wake_up.wav";

	private final AudioPlayer audioPlayer;
	private ExecutorService executor;

	@Inject
	WakeUpSound(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
	}

	void start()
	{
		executor = Executors.newSingleThreadExecutor();
	}

	void shutDown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	void play(int volume)
	{
		if (executor == null || executor.isShutdown() || volume <= 0)
		{
			return;
		}

		float gain = volumeToGain(volume);
		executor.submit(() ->
		{
			try
			{
				audioPlayer.play(MlmQolPlugin.class, SOUND_FILE, gain);
			}
			catch (Exception e)
			{
				log.debug("Failed to play {}", SOUND_FILE, e);
			}
		});
	}

	static float volumeToGain(int volume)
	{
		return volume <= 0 ? -80f : 20f * (float) Math.log10(volume / 100f);
	}
}
