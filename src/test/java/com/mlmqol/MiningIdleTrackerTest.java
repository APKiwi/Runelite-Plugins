package com.mlmqol;

import net.runelite.api.gameval.AnimationID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiningIdleTrackerTest
{
	private static final int MINING = AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL;
	private static final int OTHER_ANIMATION = AnimationID.HUMAN_PICKUPTABLE;
	private static final int IDLE = -1;
	private static final int GRACE_TICKS = MiningIdleTracker.IDLE_GRACE_TICKS;

	private MiningIdleTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new MiningIdleTracker();
	}

	private boolean tickUntilStop(int animation)
	{
		for (int i = 0; i < GRACE_TICKS; i++)
		{
			if (tracker.onTick(animation))
			{
				return true;
			}
		}

		return false;
	}

	@Test
	public void doesNotFireWithoutMining()
	{
		assertFalse(tickUntilStop(IDLE));
		assertFalse(tracker.isMining());
	}

	@Test
	public void firesOnceWhenMiningStops()
	{
		tracker.onAnimationChanged(MINING);
		assertTrue(tracker.isMining());

		assertTrue(tickUntilStop(IDLE));
		assertFalse(tracker.isMining());

		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void staysQuietWhileSwingsContinue()
	{
		tracker.onAnimationChanged(MINING);

		for (int i = 0; i < 20; i++)
		{
			assertFalse(tracker.onTick(MINING));
		}

		assertTrue(tracker.isMining());
	}

	@Test
	public void gapsBetweenSwingsDoNotFire()
	{
		tracker.onAnimationChanged(MINING);

		for (int swing = 0; swing < 10; swing++)
		{
			for (int i = 0; i < GRACE_TICKS - 1; i++)
			{
				assertFalse("alerted mid-mining on a gap of " + (i + 1) + " tick(s)",
					tracker.onTick(IDLE));
			}

			assertFalse(tracker.onTick(MINING));
			assertTrue(tracker.isMining());
		}

		assertTrue(tickUntilStop(IDLE));
	}

	@Test
	public void theStopTakesTheWholeGrace()
	{
		tracker.onAnimationChanged(MINING);

		for (int i = 0; i < GRACE_TICKS - 1; i++)
		{
			assertFalse(tracker.onTick(IDLE));
		}

		assertTrue(tracker.onTick(IDLE));
	}

	@Test
	public void anotherAnimationCountsAsStopped()
	{
		tracker.onAnimationChanged(MINING);
		assertTrue(tickUntilStop(OTHER_ANIMATION));
	}

	@Test
	public void rearmsOnTheNextSwing()
	{
		tracker.onAnimationChanged(MINING);
		assertTrue(tickUntilStop(IDLE));

		tracker.onAnimationChanged(MINING);
		assertTrue(tracker.isMining());
		assertTrue(tickUntilStop(IDLE));
	}

	@Test
	public void resetDisarmsTheTracker()
	{
		tracker.onAnimationChanged(MINING);
		tracker.reset();

		assertFalse(tracker.isMining());
		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void aManualStopDoesNotFire()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();

		assertFalse(tracker.isMining());
		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void aSwingOutlivingAManualStopIsIgnored()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();

		assertFalse(tracker.onTick(MINING));
		assertFalse(tracker.isMining());
		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void aSwingReportedAfterAManualStopIsIgnored()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();

		tracker.onAnimationChanged(MINING);
		assertFalse(tracker.isMining());

		assertFalse(tracker.onTick(IDLE));
		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void theNextVeinIsTrackedAfterAManualStop()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();

		assertFalse(tracker.onTick(IDLE));
		tracker.onAnimationChanged(MINING);
		assertTrue(tracker.isMining());

		assertTrue(tickUntilStop(IDLE));
	}

	@Test
	public void aSwingSeenOnATickArmsTheTracker()
	{
		assertFalse(tracker.onTick(MINING));
		assertTrue(tracker.isMining());

		assertTrue(tickUntilStop(IDLE));
	}

	@Test
	public void resetClearsAManualStop()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();
		tracker.reset();

		tracker.onAnimationChanged(MINING);
		assertTrue(tracker.isMining());
		assertTrue(tickUntilStop(IDLE));
	}
}
