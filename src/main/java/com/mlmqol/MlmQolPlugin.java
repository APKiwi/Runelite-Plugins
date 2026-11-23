package com.mlmqol;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "MLM QOL",
		description = "Quality of life for the Motherlode Mine: stop mining when the sack is full, and flag a stopped hopper",
		tags = {"motherlode", "mlm", "mining", "sack", "pay", "dirt", "skilling", "notification", "afk", "idle"},
		configName = "MlmQolPlugin"
)
public class MlmQolPlugin extends Plugin
{
	static final String CONFIG_GROUP = "mlm-qol";

	private static final Set<Integer> MOTHERLODE_MAP_REGIONS = ImmutableSet.of(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);
	private static final Set<Integer> ORE_VEINS = ImmutableSet.of(ObjectID.MOTHERLODE_ORE_SINGLE, ObjectID.MOTHERLODE_ORE_LEFT,
		ObjectID.MOTHERLODE_ORE_MIDDLE, ObjectID.MOTHERLODE_ORE_RIGHT);
	private static final Set<MenuAction> OBJECT_OPTIONS = ImmutableSet.of(MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION);
	// Interface clicks (eating, dropping, tabs) don't interrupt mining, so they are absent.
	private static final Set<MenuAction> MANUAL_STOP_ACTIONS = ImmutableSet.<MenuAction>builder()
		.addAll(OBJECT_OPTIONS)
		.add(MenuAction.WALK)
		.add(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		.add(MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
			MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION)
		.add(MenuAction.WIDGET_TARGET_ON_NPC)
		.add(MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION,
			MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION,
			MenuAction.GROUND_ITEM_FIFTH_OPTION)
		.add(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
		.add(MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_SECOND_OPTION, MenuAction.PLAYER_THIRD_OPTION,
			MenuAction.PLAYER_FOURTH_OPTION, MenuAction.PLAYER_FIFTH_OPTION, MenuAction.PLAYER_SIXTH_OPTION,
			MenuAction.PLAYER_SEVENTH_OPTION, MenuAction.PLAYER_EIGHTH_OPTION)
		.add(MenuAction.WIDGET_TARGET_ON_PLAYER)
		.build();
	private static final Set<Integer> BABY_MOLES = ImmutableSet.of(NpcID.MOLE_PET, NpcID.MOLE_PET_NAKED,
		NpcID.MOLE_BABY_01, NpcID.MOLE_BABY_02, NpcID.MOLE_BABY_03);

	private static final String MINE_OPTION = "Mine";
	private static final String DEPOSIT_OPTION = "Deposit";
	private static final int STRUT_COUNT = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HopperOverlay hopperOverlay;

	@Inject
	private BabyMoleOverlay babyMoleOverlay;

	@Inject
	private MlmQolConfig config;

	@Inject
	private Notifier notifier;

	private final SackTracker sackTracker = new SackTracker();
	private final MiningIdleTracker miningIdleTracker = new MiningIdleTracker();

	@Getter(AccessLevel.PACKAGE)
	private final Set<TileObject> hoppers = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<TileObject> brokenStruts = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> babyMoles = new HashSet<>();

	private boolean inMlm;
	private boolean sackFull;
	private boolean overfillNotified;
	@Getter(AccessLevel.PACKAGE)
	private boolean machineryStopped;

	@Provides
	MlmQolConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MlmQolConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(hopperOverlay);
		overlayManager.add(babyMoleOverlay);

		clientThread.invokeLater(() ->
		{
			inMlm = checkInMlm();
			if (inMlm)
			{
				primeTracker();
				scanScene();
			}
			sackFull = inMlm && isSackFull();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(hopperOverlay);
		overlayManager.remove(babyMoleOverlay);
		inMlm = false;
		sackFull = false;
		overfillNotified = false;
		sackTracker.reset();
		miningIdleTracker.reset();
		clearSceneObjects();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameState == GameState.LOADING)
		{
			clearSceneObjects();
			miningIdleTracker.reset();

			boolean wasInMlm = inMlm;
			inMlm = checkInMlm();
			if (inMlm && !wasInMlm)
			{
				primeTracker();
				updateSackFull();
			}
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			inMlm = false;
			sackFull = false;
			sackTracker.reset();
			miningIdleTracker.reset();
			clearSceneObjects();
		}
	}

	// The hopper's object type isn't assumed, so every spawn type is watched.

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		trackObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		untrackObject(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		trackObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		untrackObject(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		trackObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		untrackObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		trackObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		untrackObject(event.getGroundObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (inMlm && BABY_MOLES.contains(event.getNpc().getId()))
		{
			babyMoles.add(event.getNpc());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		babyMoles.remove(event.getNpc());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (inMlm && event.getActor() == client.getLocalPlayer())
		{
			miningIdleTracker.onAnimationChanged(event.getActor().getAnimation());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (inMlm && MANUAL_STOP_ACTIONS.contains(event.getMenuAction()))
		{
			miningIdleTracker.onManualStop();
		}
	}

	private void trackObject(TileObject object)
	{
		if (!inMlm || object == null)
		{
			return;
		}

		switch (object.getId())
		{
			case ObjectID.MOTHERLODE_HOPPER:
				hoppers.add(object);
				break;
			case ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN:
				brokenStruts.add(object);
				updateMachineryState();
				break;
		}
	}

	private void untrackObject(TileObject object)
	{
		if (!inMlm || object == null)
		{
			return;
		}

		hoppers.remove(object);
		if (brokenStruts.remove(object))
		{
			updateMachineryState();
		}
	}

	private void scanScene()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		for (Tile[][] plane : worldView.getScene().getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}

					for (GameObject gameObject : tile.getGameObjects())
					{
						trackObject(gameObject);
					}

					trackObject(tile.getWallObject());
					trackObject(tile.getDecorativeObject());
					trackObject(tile.getGroundObject());
				}
			}
		}

		for (NPC npc : worldView.npcs())
		{
			if (BABY_MOLES.contains(npc.getId()))
			{
				babyMoles.add(npc);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (inMlm && (event.getVarbitId() == VarbitID.MOTHERLODE_SACK_TRANSMIT || event.getVarbitId() == VarbitID.MOTHERLODE_BIGGERSACK))
		{
			sackTracker.refreshSack(client);
			updateSackFull();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()))
		{
			updateSackFull();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (inMlm && event.getContainerId() == InventoryID.INV)
		{
			int carried = sackTracker.getCarriedPayDirt();
			sackTracker.refreshInventory(client);
			updateSackFull();
			checkOverfill(sackTracker.getCarriedPayDirt() > carried);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!inMlm)
		{
			return;
		}

		// Pay-dirt on a stopped belt is stuck, not lost.
		if (!machineryStopped)
		{
			sackTracker.onTick();
		}

		updateSackFull();
		checkMiningStopped();
		checkOverfill(false);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!inMlm)
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (!OBJECT_OPTIONS.contains(entry.getType()))
		{
			return;
		}

		int objectId = event.getIdentifier();
		String option = Text.removeTags(entry.getOption());

		if (ORE_VEINS.contains(objectId) && MINE_OPTION.equals(option) && sackFull && config.blockMining()
			|| objectId == ObjectID.MOTHERLODE_HOPPER && DEPOSIT_OPTION.equals(option) && isDepositBlocked())
		{
			entry.setDeprioritized(true);
		}
	}

	private boolean isDepositBlocked()
	{
		return config.blockDepositWhenBlocked() && machineryStopped
			&& (hasPayDirtInTransit() || sackTracker.isCommittedFull(config.blockAtCount()));
	}

	boolean shouldFlagHopper()
	{
		return machineryStopped || hasPayDirtInTransit();
	}

	boolean hasPayDirtInTransit()
	{
		return sackTracker.getInTransit() > 0;
	}

	private void updateMachineryState()
	{
		boolean stopped = brokenStruts.size() >= STRUT_COUNT;
		if (stopped != machineryStopped)
		{
			machineryStopped = stopped;
			log.debug("Machinery stopped: {}", stopped);
		}
	}

	private void clearSceneObjects()
	{
		hoppers.clear();
		brokenStruts.clear();
		babyMoles.clear();
		machineryStopped = false;
	}

	private void primeTracker()
	{
		sackTracker.reset();
		sackTracker.refreshSack(client);
		sackTracker.refreshInventory(client);
		sackTracker.prime();
	}

	private void updateSackFull()
	{
		if (!inMlm)
		{
			return;
		}

		boolean full = isSackFull();
		if (full == sackFull)
		{
			return;
		}

		sackFull = full;
		int limit = sackTracker.limit(config.blockAtCount());
		log.debug("Sack full: {} ({} deposited, {} on the belt, {} carried, full load {}, limit {})", full,
			sackTracker.getDeposited(), sackTracker.getInTransit(), sackTracker.getCarriedPayDirt(),
			sackTracker.getFullLoad(), limit);

		if (!config.warnInChat())
		{
			return;
		}

		if (!full)
		{
			sendChatMessage("Your sack has room again.");
		}
		else if (sackTracker.isFull(config.blockAtCount()))
		{
			sendChatMessage("Sack limit reached (" + sackTracker.getUsed() + "/" + limit
				+ "), empty it before mining more.");
		}
		else
		{
			sendChatMessage("Sack only has room for " + (limit - sackTracker.getCommitted()) + " more, less than a full load ("
				+ sackTracker.getFullLoad() + "), empty it before mining more.");
		}
	}

	private boolean isSackFull()
	{
		int blockAt = config.blockAtCount();
		return sackTracker.isFull(blockAt) || config.blockOnFullLoad() && sackTracker.isFullLoadTooBig(blockAt);
	}

	private void checkOverfill(boolean gainedPayDirt)
	{
		int used = sackTracker.getUsed();
		int limit = sackTracker.limit(config.blockAtCount());
		boolean roomToMine = sackTracker.getFullLoad() > sackTracker.getCarriedPayDirt();
		boolean overfilling = miningIdleTracker.isMining() && used >= limit && roomToMine;
		if (gainedPayDirt && used >= limit && (roomToMine || used > limit) || overfilling && !overfillNotified)
		{
			notifier.notify(config.sackFullNotification(), "Your sack is full, stop mining.");
		}
		overfillNotified = overfilling;
	}

	private void checkMiningStopped()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			miningIdleTracker.reset();
		}
		else if (miningIdleTracker.onTick(local.getAnimation()))
		{
			notifier.notify(config.miningStoppedNotification(), "You've stopped mining.");
		}
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(message).build())
			.build());
	}

	private boolean checkInMlm()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING)
		{
			return false;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}

		for (int region : worldView.getMapRegions())
		{
			if (!MOTHERLODE_MAP_REGIONS.contains(region))
			{
				return false;
			}
		}

		return true;
	}
}
