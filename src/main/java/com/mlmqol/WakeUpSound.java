package com.mlmqol;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the bundled wake-up sound.
 *
 * <p>Playback opens an audio line and reads the clip, so it is handed to a single-threaded executor
 * rather than run on the client thread. One thread also means a burst of alerts queues up instead of
 * overlapping.
 */
@Slf4j
@Singleton
class WakeUpSound
{
	/** Resolved against this package, so it lives in {@code src/main/resources/com/mlmqol}. */
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

	/**
	 * @param volume 0 (silent, nothing plays) to 100 (the clip's own level)
	 */
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

	/** Maps a 0-100 volume onto the decibel gain {@link AudioPlayer} expects. */
	static float volumeToGain(int volume)
	{
		if (volume <= 0)
		{
			return -80f;
		}

		return 20f * (float) Math.log10(volume / 100f);
	}
}
