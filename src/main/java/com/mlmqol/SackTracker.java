package com.mlmqol;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

class SackTracker
{
	static final int SACK_SIZE = 108;
	static final int SACK_LARGE_SIZE = 189;
	private static final int INVENTORY_SIZE = 28;
	// In-transit pay-dirt that never reaches the sack is written off after this.
	private static final int IN_TRANSIT_STALE_TICKS = 50;

	@Getter
	private int deposited;
	@Getter
	private int capacity = SACK_SIZE;
	@Getter
	private int carriedPayDirt;
	@Getter
	private int fullLoad = INVENTORY_SIZE;
	@Getter
	private int inTransit;

	// Only pay-dirt that leaves the inventory after a deposit click is on the belt, the rest was dropped.
	@Setter
	private boolean depositing;
	private boolean primed;
	private int ticksSinceArrival;

	void refreshCapacity(Client client)
	{
		capacity = client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1 ? SACK_LARGE_SIZE : SACK_SIZE;
	}

	void refreshSack(Client client)
	{
		int previous = deposited;
		deposited = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
		refreshCapacity(client);

		if (primed && deposited > previous)
		{
			inTransit = Math.max(0, inTransit - (deposited - previous));
			ticksSinceArrival = 0;
		}
	}

	void refreshInventory(Client client)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		int previous = carriedPayDirt;
		carriedPayDirt = inventory == null ? 0 : inventory.count(ItemID.PAYDIRT);
		fullLoad = inventory == null ? INVENTORY_SIZE : INVENTORY_SIZE - inventory.count() + carriedPayDirt;

		if (primed && depositing && carriedPayDirt < previous)
		{
			inTransit += previous - carriedPayDirt;
			ticksSinceArrival = 0;
			depositing = false;
		}
	}

	void onTick()
	{
		if (inTransit > 0 && ++ticksSinceArrival >= IN_TRANSIT_STALE_TICKS)
		{
			inTransit = 0;
			ticksSinceArrival = 0;
		}
	}

	void prime()
	{
		primed = true;
	}

	void reset()
	{
		deposited = 0;
		capacity = SACK_SIZE;
		carriedPayDirt = 0;
		fullLoad = INVENTORY_SIZE;
		inTransit = 0;
		ticksSinceArrival = 0;
		depositing = false;
		primed = false;
	}

	int getUsed()
	{
		return getCommitted() + carriedPayDirt;
	}

	int getCommitted()
	{
		return deposited + inTransit;
	}

	int limit(int blockAt)
	{
		return Math.min(blockAt, capacity);
	}

	boolean isCommittedFull(int blockAt)
	{
		return getCommitted() >= limit(blockAt);
	}

	boolean isFull(int blockAt)
	{
		return getUsed() >= limit(blockAt);
	}

	boolean isFullLoadTooBig(int blockAt)
	{
		return getCommitted() + fullLoad > limit(blockAt);
	}
}
