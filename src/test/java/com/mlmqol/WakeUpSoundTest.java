package com.mlmqol;

import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.runelite.client.audio.AudioPlayer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the two things about the sound that can be checked without a sound card: the clip is on the
 * classpath in a format Java can decode, and the volume mapping behaves.
 */
public class WakeUpSoundTest
{
	@Test
	public void clipIsBundledAndDecodable() throws IOException, UnsupportedAudioFileException
	{
		assertNotNull("wake_up.wav is missing from the plugin resources",
			MlmQolPlugin.class.getResource("wake_up.wav"));

		try (AudioInputStream audio = AudioSystem.getAudioInputStream(
			MlmQolPlugin.class.getResourceAsStream("wake_up.wav")))
		{
			assertTrue(audio.getFormat().getSampleRate() > 0);
		}
	}

	@Test
	public void volumeMapsToGain()
	{
		// Full volume leaves the clip at its own level, and halving it drops roughly 6 dB.
		assertEquals(0f, WakeUpSound.volumeToGain(100), 0.001f);
		assertEquals(-6.02f, WakeUpSound.volumeToGain(50), 0.01f);
		assertTrue(WakeUpSound.volumeToGain(1) < WakeUpSound.volumeToGain(50));
	}

	@Test
	public void silentAndStoppedPlaybackDoesNothing() throws Exception
	{
		AudioPlayer audioPlayer = mock(AudioPlayer.class);
		WakeUpSound sound = new WakeUpSound(audioPlayer);

		// Not started yet.
		sound.play(100);

		sound.start();
		sound.play(0);
		sound.shutDown();

		// And nothing after shutdown either.
		sound.play(100);

		verify(audioPlayer, never()).play(any(Class.class), anyString(), anyFloat());
	}
}
