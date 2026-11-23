package com.mlmqol;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.AnimationID;

@Slf4j
class MiningIdleTracker
{
	// The animation clears between swings, so mining only counts as stopped after this many idle ticks.
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

	@Getter
	private boolean mining;
	private int idleTicks;
	// An abandoned swing's animation can outlive the click that abandoned it.
	private boolean ignoringSwings;

	static boolean isMiningAnimation(int animation)
	{
		return MINING_ANIMATIONS.contains(animation);
	}

	void onAnimationChanged(int animation)
	{
		if (isMiningAnimation(animation))
		{
			arm();
		}
	}

	void onManualStop()
	{
		mining = false;
		idleTicks = 0;
		ignoringSwings = true;
	}

	boolean onTick(int animation)
	{
		boolean swinging = isMiningAnimation(animation);
		if (ignoringSwings)
		{
			ignoringSwings = swinging;
			return false;
		}

		if (swinging)
		{
			arm();
			return false;
		}

		if (!mining || ++idleTicks < IDLE_GRACE_TICKS)
		{
			return false;
		}

		log.debug("Mining stopped after {} idle ticks", idleTicks);
		mining = false;
		idleTicks = 0;
		return true;
	}

	void reset()
	{
		mining = false;
		idleTicks = 0;
		ignoringSwings = false;
	}

	private void arm()
	{
		if (!ignoringSwings)
		{
			mining = true;
			idleTicks = 0;
		}
	}
}
