package com.mlmqol;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.AnimationID;

/**
 * Watches the local player's animation to spot the moment mining stops.
 *
 * <p>Only the wall-mining animations are matched, since every ore vein in the Motherlode Mine is a
 * wall object and swinging at one plays the "_WALL" variant of the pickaxe animation.
 *
 * <p>A skilling animation is not held continuously: it is restarted each swing and clears in between,
 * so the player regularly shows no animation at all while still mining. Mining is therefore only
 * called off once {@link #IDLE_GRACE_TICKS} pass with no swing. Anything that is not a mining
 * animation — the hopper deposit, for instance — counts towards that grace just as no animation does,
 * since it means mining has stopped.
 *
 * <p>Only stops the player didn't ask for are reported. {@link #onManualStop()} drops the swing being
 * tracked, so walking off or clicking something else goes unremarked — there is nothing to be told
 * about a stop you just made yourself.
 */
@Slf4j
class MiningIdleTracker
{
	/**
	 * Game ticks without a swing before mining is treated as having stopped. Long enough to ride out
	 * the gap between swings, short enough that the alert still feels immediate.
	 */
	static final int IDLE_GRACE_TICKS = 3;


	private static final Set<Integer> MINING_ANIMATIONS = ImmutableSet.of(
		AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL,
		AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_3A_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,
		AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL,
		AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL,
		AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL);

	private boolean mining;
	private int idleTicks;
	/**
	 * Set by a manual stop, and held until a tick passes with no swing. The animation of the swing
	 * being abandoned can outlive the click that abandoned it, and that leftover must not be mistaken
	 * for mining the player is still doing.
	 */
	private boolean ignoringSwings;

	static boolean isMiningAnimation(int animation)
	{
		return MINING_ANIMATIONS.contains(animation);
	}

	/** Arms the tracker when the animation is a swing at a vein. */
	void onAnimationChanged(int animation)
	{
		if (isMiningAnimation(animation))
		{
			arm();
		}
	}

	/**
	 * Called when the player does something that stops mining of their own accord — walking off, or
	 * clicking another vein, the hopper, a ladder. Drops the swing being tracked so no alert follows.
	 * Mining starts being tracked again from the next swing.
	 */
	void onManualStop()
	{
		if (mining)
		{
			log.debug("Mining stopped by the player, not alerting");
		}

		mining = false;
		idleTicks = 0;
		ignoringSwings = true;
	}

	/**
	 * @param animation the local player's current animation
	 * @return true on the single tick mining is called off, so a caller can alert exactly once
	 */
	boolean onTick(int animation)
	{
		boolean swinging = isMiningAnimation(animation);

		if (ignoringSwings)
		{
			// Wait for the abandoned swing's animation to clear before trusting one again.
			ignoringSwings = swinging;
			return false;
		}

		if (swinging)
		{
			if (mining && idleTicks > 0)
			{
				// How long the gaps between swings really run, for when IDLE_GRACE_TICKS needs revisiting.
				log.debug("Mining resumed after {} idle tick(s)", idleTicks);
			}

			arm();
			return false;
		}

		if (!mining)
		{
			return false;
		}

		if (++idleTicks < IDLE_GRACE_TICKS)
		{
			return false;
		}

		log.debug("Mining stopped after {} idle tick(s)", idleTicks);
		mining = false;
		idleTicks = 0;
		return true;
	}

	/** @return true while a swing is recent enough that mining has not been called off */
	boolean isMining()
	{
		return mining;
	}

	void reset()
	{
		mining = false;
		idleTicks = 0;
		ignoringSwings = false;
	}

	/**
	 * Takes a swing as the mining to watch. A swing seen on a tick arms the tracker just as the
	 * animation event does, so mining is picked up even when no event is waiting — on enabling the
	 * plugin mid-swing, for instance.
	 *
	 * <p>Nothing arms inside the ignore window: the animation of an abandoned swing carries on being
	 * reported after the click that abandoned it, and arming on that would alert for a stop the player
	 * made on purpose.
	 */
	private void arm()
	{
		if (ignoringSwings)
		{
			return;
		}

		mining = true;
		idleTicks = 0;
	}
}
