package com.mlmqol;

import net.runelite.api.gameval.AnimationID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the "you stopped mining" edge: the tracker has to fire exactly once per stop, sit quiet
 * while swings continue, and stay quiet until another swing re-arms it.
 */
public class MiningIdleTrackerTest
{
	private static final int MINING = AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL;
	/** Any animation that is not a swing at a vein — here, picking something up. */
	private static final int OTHER_ANIMATION = AnimationID.HUMAN_PICKUPTABLE;
	private static final int IDLE = -1;
	private static final int GRACE_TICKS = MiningIdleTracker.IDLE_GRACE_TICKS;

	private MiningIdleTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new MiningIdleTracker();
	}

	/** Runs the grace period out, returning true if the stop fired within it. */
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

		// One stop, one alert — the following ticks must stay quiet.
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

		// The animation clears between swings, so a run of idle ticks inside the grace period followed
		// by another swing is ordinary mining, not a stop.
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

		// The animation of the abandoned swing can still be showing on the next tick. Picking it back
		// up would alert when it finally clears, which is exactly what the manual stop ruled out.
		assertFalse(tracker.onTick(MINING));
		assertFalse(tracker.isMining());
		assertFalse(tickUntilStop(IDLE));
	}

	@Test
	public void aSwingReportedAfterAManualStopIsIgnored()
	{
		tracker.onAnimationChanged(MINING);
		tracker.onManualStop();

		// The animation of the abandoned swing is reported after the click that abandoned it has been
		// handled, which must not put the tracker back on a swing the player already walked away from.
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

		// Walking to the next vein clears the animation, and the swing that follows is tracked again.
		assertFalse(tracker.onTick(IDLE));
		tracker.onAnimationChanged(MINING);
		assertTrue(tracker.isMining());

		assertTrue(tickUntilStop(IDLE));
	}

	@Test
	public void aSwingSeenOnATickArmsTheTracker()
	{
		// No animation event — as when the plugin is enabled with a swing already underway.
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
