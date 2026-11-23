package com.mlmqol;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.Notification;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MlmQolPluginTest
{
	private static final int MLM_REGION = 14679;
	private static final int ELSEWHERE_REGION = 12850;
	private static final int MINING_ANIMATION = AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL;
	private static final int IDLE_ANIMATION = -1;
	private static final int IDLE_DELAY_TICKS = MiningIdleTracker.IDLE_GRACE_TICKS;
	private static final int SACK_SIZE = 108;
	private static final int SACK_LARGE_SIZE = 189;

	private Client client;
	private ClientThread clientThread;
	private ChatMessageManager chatMessageManager;
	private OverlayManager overlayManager;
	private HopperOverlay hopperOverlay;
	private BabyMoleOverlay babyMoleOverlay;
	private MlmQolConfig config;
	private Notifier notifier;
	private Player localPlayer;
	private ItemContainer inventory;
	private WorldView worldView;
	private Scene scene;

	private MlmQolPlugin plugin;
	private int otherItems;
	private int deprioritized;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		chatMessageManager = mock(ChatMessageManager.class);
		overlayManager = mock(OverlayManager.class);
		hopperOverlay = mock(HopperOverlay.class);
		babyMoleOverlay = mock(BabyMoleOverlay.class);
		config = mock(MlmQolConfig.class);
		notifier = mock(Notifier.class);
		localPlayer = mock(Player.class);
		inventory = mock(ItemContainer.class);
		worldView = mock(WorldView.class);
		scene = mock(Scene.class);

		when(config.blockMining()).thenReturn(true);
		when(config.blockAtCount()).thenReturn(SACK_LARGE_SIZE);
		when(config.warnInChat()).thenReturn(true);
		when(config.blockDepositWhenBlocked()).thenReturn(true);
		when(config.miningStoppedNotification()).thenReturn(Notification.ON);
		when(config.sackFullNotification()).thenReturn(Notification.ON);

		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(localPlayer.getAnimation()).thenReturn(IDLE_ANIMATION);
		when(worldView.getScene()).thenReturn(scene);
		when(scene.getTiles()).thenReturn(new Tile[0][0][0]);
		doReturn(npcSet()).when(worldView).npcs();

		deposited(0);
		upgraded(false);
		carrying(0);
		inRegion(MLM_REGION);

		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));

		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toInstance(client);
			binder.bind(ClientThread.class).toInstance(clientThread);
			binder.bind(ChatMessageManager.class).toInstance(chatMessageManager);
			binder.bind(OverlayManager.class).toInstance(overlayManager);
			binder.bind(HopperOverlay.class).toInstance(hopperOverlay);
			binder.bind(BabyMoleOverlay.class).toInstance(babyMoleOverlay);
			binder.bind(MlmQolConfig.class).toInstance(config);
			binder.bind(Notifier.class).toInstance(notifier);
		});
		plugin = injector.getInstance(MlmQolPlugin.class);
	}

	private void deposited(int count)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT)).thenReturn(count);
	}

	private void upgraded(boolean upgraded)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK)).thenReturn(upgraded ? 1 : 0);
	}

	private void carrying(int payDirt)
	{
		when(inventory.count(ItemID.PAYDIRT)).thenReturn(payDirt);
		when(inventory.count()).thenReturn(otherItems + payDirt);
	}

	private void inRegion(int region)
	{
		when(worldView.getMapRegions()).thenReturn(new int[]{region});
	}

	private void gameState(GameState gameState)
	{
		when(client.getGameState()).thenReturn(gameState);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(gameState);
		plugin.onGameStateChanged(event);
	}

	private void enterMlm()
	{
		inRegion(MLM_REGION);
		gameState(GameState.LOADING);
	}

	private void leaveMlm()
	{
		inRegion(ELSEWHERE_REGION);
		gameState(GameState.LOADING);
	}

	private void standingInTheMine()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		inRegion(MLM_REGION);
	}

	private void sceneContaining(GameObject... objects)
	{
		Tile tile = mock(Tile.class);
		when(tile.getGameObjects()).thenReturn(objects);
		when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
	}

	private void sackVarbitChanged()
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.MOTHERLODE_SACK_TRANSMIT);
		plugin.onVarbitChanged(event);
	}

	private void upgradeVarbitChanged()
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.MOTHERLODE_BIGGERSACK);
		plugin.onVarbitChanged(event);
	}

	private void inventoryChanged()
	{
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, inventory));
	}

	private void tick(int count)
	{
		for (int i = 0; i < count; i++)
		{
			plugin.onGameTick(new GameTick());
		}
	}

	private void animating(int animation)
	{
		when(localPlayer.getAnimation()).thenReturn(animation);
		AnimationChanged event = new AnimationChanged();
		event.setActor(localPlayer);
		plugin.onAnimationChanged(event);
	}

	private void mineThenStop()
	{
		animating(MINING_ANIMATION);
		tick(3);
		animating(IDLE_ANIMATION);
		tick(IDLE_DELAY_TICKS);
	}

	private void mineForSwings(int swings, int gapTicks)
	{
		for (int i = 0; i < swings; i++)
		{
			animating(MINING_ANIMATION);
			tick(1);
			animating(IDLE_ANIMATION);
			tick(gapTicks);
		}
	}

	private GameObject spawnGameObject(int id)
	{
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(id);
		GameObjectSpawned event = new GameObjectSpawned();
		event.setGameObject(object);
		plugin.onGameObjectSpawned(event);
		return object;
	}

	private void despawnGameObject(GameObject object)
	{
		GameObjectDespawned event = new GameObjectDespawned();
		event.setGameObject(object);
		plugin.onGameObjectDespawned(event);
	}

	private NPC spawnNpc(int id)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		plugin.onNpcSpawned(new NpcSpawned(npc));
		return npc;
	}

	private static IndexedObjectSet<NPC> npcSet(NPC... npcs)
	{
		List<NPC> list = Arrays.asList(npcs);
		return new IndexedObjectSet<NPC>()
		{
			@Override
			public NPC byIndex(int index)
			{
				return list.get(index);
			}

			@Override
			public Iterator<NPC> iterator()
			{
				return list.iterator();
			}
		};
	}

	private void breakBothStruts()
	{
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
	}

	private MenuEntry menuEntry(String option, int objectId, MenuAction type)
	{
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getOption()).thenReturn(option);
		when(entry.getIdentifier()).thenReturn(objectId);
		when(entry.getType()).thenReturn(type);
		when(entry.setDeprioritized(true)).thenAnswer(i ->
		{
			deprioritized++;
			return entry;
		});
		return entry;
	}

	private void click(String option, MenuAction type)
	{
		plugin.onMenuOptionClicked(new MenuOptionClicked(menuEntry(option, 0, type)));
	}

	private MenuEntry addMenuEntry(String option, int objectId)
	{
		MenuEntry entry = menuEntry(option, objectId, MenuAction.GAME_OBJECT_FIRST_OPTION);
		plugin.onMenuEntryAdded(new MenuEntryAdded(entry));
		return entry;
	}

	private void strandPayDirt(int payDirt)
	{
		carrying(payDirt);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
	}

	private void fillSack()
	{
		deposited(SACK_SIZE);
		sackVarbitChanged();
	}

	@Test
	public void mineIsRemovedFromAVeinOnceTheSackIsFull()
	{
		enterMlm();
		fillSack();

		MenuEntry entry = addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(entry).setDeprioritized(true);
	}

	@Test
	public void mineIsKeptWhileTheSackHasRoom()
	{
		enterMlm();
		deposited(107);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void allFourVeinVariantsAreBlocked()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_LEFT);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_MIDDLE);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_RIGHT);

		assertEquals(4, deprioritized);
	}

	@Test
	public void otherOptionsOnTheVeinAreLeftAlone()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Prospect", ObjectID.MOTHERLODE_ORE_SINGLE);
		addMenuEntry("Walk here", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void mineOnSomethingThatIsNotAVeinIsLeftAlone()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ROCKFALL_1);

		assertEquals(0, deprioritized);
	}

	@Test
	public void nonObjectMenuActionsAreLeftAlone()
	{
		enterMlm();
		fillSack();

		MenuEntry entry = menuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
		plugin.onMenuEntryAdded(new MenuEntryAdded(entry));

		assertEquals(0, deprioritized);
	}

	@Test
	public void blockingCanBeTurnedOff()
	{
		when(config.blockMining()).thenReturn(false);
		enterMlm();
		fillSack();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void nothingIsBlockedOutsideTheMine()
	{
		enterMlm();
		fillSack();
		leaveMlm();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void collectingFromTheSackUnblocksMining()
	{
		enterMlm();
		fillSack();

		deposited(0);
		sackVarbitChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void depositingBlocksImmediatelyRatherThanWhenTheBeltCatchesUp()
	{
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		carrying(0);
		inventoryChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertTrue(deprioritized > 0);
	}

	@Test
	public void carriedPayDirtCountsTowardsTheBlockCount()
	{
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(1, deprioritized);
	}

	@Test
	public void aLowerBlockCountStopsMiningEarly()
	{
		when(config.blockAtCount()).thenReturn(50);
		enterMlm();
		deposited(50);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(1, deprioritized);
	}

	@Test
	public void aFullLoadThatWontFitBlocksMining()
	{
		when(config.blockOnFullLoad()).thenReturn(true);
		otherItems = 1;
		carrying(0);
		enterMlm();
		deposited(55);
		sackVarbitChanged();
		strandPayDirt(27);

		MenuEntry entry = addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(entry).setDeprioritized(true);
	}

	@Test
	public void aFullLoadThatFitsKeepsMining()
	{
		when(config.blockOnFullLoad()).thenReturn(true);
		otherItems = 1;
		carrying(0);
		enterMlm();
		deposited(54);
		sackVarbitChanged();
		strandPayDirt(27);

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void fullLoadBlockingIsOffByDefault()
	{
		otherItems = 1;
		carrying(0);
		enterMlm();
		deposited(90);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void aFullLoadThatWontFitIsAnnounced()
	{
		when(config.blockOnFullLoad()).thenReturn(true);
		otherItems = 1;
		carrying(0);
		enterMlm();
		deposited(90);
		sackVarbitChanged();

		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chatMessageManager).queue(message.capture());
		assertTrue(message.getValue().getRuneLiteFormattedMessage().contains("room for 18 more, less than a full load (27)"));
	}

	@Test
	public void aStoppedBeltAloneFlagsTheHopper()
	{
		enterMlm();
		breakBothStruts();

		assertTrue("nothing is on the belt, but the wheel still needs repairing", plugin.shouldFlagHopper());
		assertFalse(plugin.hasPayDirtInTransit());
	}

	@Test
	public void payDirtOnARunningBeltAloneFlagsTheHopper()
	{
		enterMlm();
		strandPayDirt(27);

		assertTrue(plugin.hasPayDirtInTransit());
		assertTrue(plugin.shouldFlagHopper());
		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void payDirtInTransitIsReportedWhetherOrNotTheBeltIsMoving()
	{
		enterMlm();
		strandPayDirt(27);
		assertTrue(plugin.hasPayDirtInTransit());

		breakBothStruts();

		assertTrue("still in transit, now going nowhere", plugin.hasPayDirtInTransit());
		assertTrue(plugin.isMachineryStopped());
	}

	@Test
	public void payDirtInTransitStopsBeingReportedOnceItArrives()
	{
		enterMlm();
		strandPayDirt(27);

		deposited(27);
		sackVarbitChanged();

		assertFalse(plugin.hasPayDirtInTransit());
		assertFalse(plugin.shouldFlagHopper());
	}

	@Test
	public void aQuietRunningBeltFlagsNothing()
	{
		enterMlm();
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);

		assertFalse("one broken strut is not a stopped belt", plugin.shouldFlagHopper());
	}

	@Test
	public void depositIsRemovedWhenTheBeltIsStoppedAndTheSackIsFull()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(entry).setDeprioritized(true);
	}

	@Test
	public void depositIsRemovedWhenAStrutBreaksBeforeYourLoadArrives()
	{
		enterMlm();
		deposited(20);
		sackVarbitChanged();
		strandPayDirt(27);

		breakBothStruts();

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(entry).setDeprioritized(true);
	}

	@Test
	public void depositIsRemovedWhenYouDepositIntoAnAlreadyStoppedBelt()
	{
		enterMlm();
		deposited(20);
		sackVarbitChanged();
		breakBothStruts();

		strandPayDirt(27);

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(entry).setDeprioritized(true);
	}

	@Test
	public void depositIsKeptOnAStoppedBeltWithNothingOnItAndRoomToSpare()
	{
		enterMlm();
		breakBothStruts();
		deposited(50);
		sackVarbitChanged();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void depositIsRemovedOnAStoppedBeltWithAFullSackAndNothingStranded()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(1, deprioritized);
	}

	@Test
	public void carriedPayDirtDoesNotCountTowardsHidingDeposit()
	{
		enterMlm();
		breakBothStruts();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void strandedPayDirtCountsTowardsHidingDeposit()
	{
		enterMlm();
		breakBothStruts();
		deposited(81);
		sackVarbitChanged();
		strandPayDirt(27);

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(1, deprioritized);
	}

	@Test
	public void depositIsKeptWhileTheBeltRuns()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void depositFollowsTheOnlyWhenBlockedCondition()
	{
		enterMlm();
		breakBothStruts();
		deposited(50);
		sackVarbitChanged();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);
		assertEquals(0, deprioritized);

		strandPayDirt(27);

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);
		assertEquals(1, deprioritized);
	}

	@Test
	public void payDirtOnARunningBeltDoesNotHideDeposit()
	{
		enterMlm();
		strandPayDirt(27);
		assertTrue(plugin.hasPayDirtInTransit());

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void blockingDepositCanBeTurnedOffWithoutLosingTheHighlight()
	{
		when(config.blockDepositWhenBlocked()).thenReturn(false);
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
		assertTrue("the hopper is still flagged, just not blocked", plugin.shouldFlagHopper());
	}

	@Test
	public void otherHopperOptionsAreLeftAlone()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Examine", ObjectID.MOTHERLODE_HOPPER);
		addMenuEntry("Walk here", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void depositElsewhereIsLeftAlone()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_SACK);

		assertEquals(0, deprioritized);
	}

	@Test
	public void aStoppedBeltDoesNotBlockMiningByItself()
	{
		enterMlm();
		breakBothStruts();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void aFullSackDoesNotHideDepositByItself()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		assertEquals(0, deprioritized);
	}

	@Test
	public void theDefaultBlockCountFollowsTheUpgrade()
	{
		enterMlm();
		deposited(SACK_SIZE);
		sackVarbitChanged();
		upgraded(true);
		upgradeVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(0, deprioritized);

		deposited(SACK_LARGE_SIZE);
		sackVarbitChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(1, deprioritized);
	}

	@Test
	public void theUpgradedSackDoesNotBlockAtTheSmallCapacity()
	{
		when(config.blockAtCount()).thenReturn(SACK_LARGE_SIZE);
		upgraded(true);
		enterMlm();
		deposited(SACK_SIZE);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(0, deprioritized);
	}

	@Test
	public void oneBrokenStrutDoesNotStopTheBelt()
	{
		enterMlm();
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void bothBrokenStrutsStopTheBelt()
	{
		enterMlm();
		breakBothStruts();

		assertTrue(plugin.isMachineryStopped());
	}

	@Test
	public void repairingEitherStrutRestartsTheBelt()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		assertTrue(plugin.isMachineryStopped());

		despawnGameObject(broken);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void brokenStrutsAreExposedForHighlighting()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		GameObject working = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertTrue(plugin.getBrokenStruts().contains(broken));
		assertFalse("a working strut is not something to point at", plugin.getBrokenStruts().contains(working));
	}

	@Test
	public void aRepairedStrutStopsBeingHighlighted()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);

		despawnGameObject(broken);

		assertTrue(plugin.getBrokenStruts().isEmpty());
	}

	@Test
	public void theHopperIsTrackedWhateverKindOfObjectItIs()
	{
		enterMlm();
		WallObject hopper = mock(WallObject.class);
		when(hopper.getId()).thenReturn(ObjectID.MOTHERLODE_HOPPER);
		WallObjectSpawned event = new WallObjectSpawned();
		event.setWallObject(hopper);

		plugin.onWallObjectSpawned(event);

		assertTrue(plugin.getHoppers().contains(hopper));
	}

	@Test
	public void aBabyMoleInTheMineIsTracked()
	{
		enterMlm();

		NPC mole = spawnNpc(NpcID.MOLE_PET);

		assertTrue(plugin.getBabyMoles().contains(mole));
	}

	@Test
	public void aBabyMoleOutsideTheMineIsNeverTracked()
	{
		leaveMlm();

		spawnNpc(NpcID.MOLE_PET);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void otherNpcsAreIgnored()
	{
		enterMlm();

		spawnNpc(NpcID.MOLE_GIANT);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void aDespawningBabyMoleIsForgotten()
	{
		enterMlm();
		NPC mole = spawnNpc(NpcID.MOLE_PET);

		plugin.onNpcDespawned(new NpcDespawned(mole));

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void leavingTheMineForgetsTheBabyMole()
	{
		enterMlm();
		spawnNpc(NpcID.MOLE_PET);

		leaveMlm();

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void loggingOutForgetsTheBabyMole()
	{
		enterMlm();
		spawnNpc(NpcID.MOLE_PET);

		gameState(GameState.LOGIN_SCREEN);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void enablingThePluginInTheMinePicksUpABabyMoleAlreadyThere()
	{
		standingInTheMine();
		NPC mole = mock(NPC.class);
		when(mole.getId()).thenReturn(NpcID.MOLE_PET);
		doReturn(npcSet(mole)).when(worldView).npcs();

		plugin.startUp();

		assertTrue(plugin.getBabyMoles().contains(mole));
	}

	@Test
	public void sceneObjectsAreIgnoredOutsideTheMine()
	{
		leaveMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());
	}

	@Test
	public void leavingTheMineForgetsTheScene()
	{
		enterMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);
		assertTrue(plugin.isMachineryStopped());

		leaveMlm();

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());
	}

	@Test
	public void loggingOutForgetsTheScene()
	{
		enterMlm();
		breakBothStruts();
		assertTrue(plugin.isMachineryStopped());

		gameState(GameState.LOGIN_SCREEN);

		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void strandedPayDirtIsNotWrittenOffWhileTheBeltIsStopped()
	{
		enterMlm();
		breakBothStruts();
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();

		tick(60);

		assertTrue(plugin.hasPayDirtInTransit());
	}

	@Test
	public void strandedPayDirtIsWrittenOffOnceTheBeltRunsAgain()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
		tick(60);

		despawnGameObject(broken);
		tick(60);

		assertFalse(plugin.hasPayDirtInTransit());
	}

	@Test
	public void reachingTheLimitIsAnnouncedOnce()
	{
		enterMlm();
		fillSack();

		sackVarbitChanged();
		tick(5);

		verify(chatMessageManager).queue(any(QueuedMessage.class));
	}

	@Test
	public void havingRoomAgainIsAnnounced()
	{
		enterMlm();
		fillSack();
		deposited(0);
		sackVarbitChanged();

		verify(chatMessageManager, times(2)).queue(any(QueuedMessage.class));
	}

	@Test
	public void chatWarningsCanBeTurnedOff()
	{
		when(config.warnInChat()).thenReturn(false);
		enterMlm();
		fillSack();

		verify(chatMessageManager, never()).queue(any(QueuedMessage.class));
	}

	@Test
	public void enablingThePluginNextToAFullSackDoesNotWarn()
	{
		standingInTheMine();
		deposited(SACK_SIZE);

		plugin.startUp();

		verify(chatMessageManager, never()).queue(any(QueuedMessage.class));
	}

	@Test
	public void enablingThePluginNextToAFullSackStillBlocksMining()
	{
		standingInTheMine();
		deposited(SACK_SIZE);

		plugin.startUp();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		assertEquals(1, deprioritized);
	}

	@Test
	public void enablingThePluginInTheMinePicksUpTheSceneStraightAway()
	{
		standingInTheMine();
		GameObject hopper = mock(GameObject.class);
		when(hopper.getId()).thenReturn(ObjectID.MOTHERLODE_HOPPER);
		sceneContaining(hopper);

		plugin.startUp();

		assertTrue(plugin.getHoppers().contains(hopper));
	}

	@Test
	public void enablingThePluginInTheMinePicksUpBrokenStruts()
	{
		standingInTheMine();
		GameObject strutA = mock(GameObject.class);
		when(strutA.getId()).thenReturn(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		GameObject strutB = mock(GameObject.class);
		when(strutB.getId()).thenReturn(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		sceneContaining(strutA, strutB);

		plugin.startUp();

		assertTrue(plugin.isMachineryStopped());
	}

	@Test
	public void theOverlayIsRegisteredAndCleanedUp() throws Exception
	{
		plugin.startUp();
		verify(overlayManager).add(hopperOverlay);

		plugin.shutDown();
		verify(overlayManager).remove(hopperOverlay);
	}

	@Test
	public void shutDownForgetsEverything() throws Exception
	{
		enterMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);
		fillSack();

		plugin.shutDown();

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(0, deprioritized);
	}

	@Test
	public void reEnteringTheMineStartsFromAKnownState()
	{
		enterMlm();
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
		breakBothStruts();
		assertTrue(plugin.hasPayDirtInTransit());

		leaveMlm();
		enterMlm();

		assertFalse("stale in-transit pay-dirt from the last visit must not carry over",
			plugin.hasPayDirtInTransit());
	}

	@Test
	public void aConfigChangeReEvaluatesBlockingImmediately()
	{
		enterMlm();
		deposited(60);
		sackVarbitChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(0, deprioritized);

		when(config.blockAtCount()).thenReturn(50);
		ConfigChanged configChanged = new ConfigChanged();
		configChanged.setGroup(MlmQolPlugin.CONFIG_GROUP);
		configChanged.setKey(MlmQolConfig.BLOCK_AT_COUNT);
		configChanged.setNewValue("50");
		plugin.onConfigChanged(configChanged);

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(1, deprioritized);
	}

	@Test
	public void anUnrelatedInventoryContainerIsIgnored()
	{
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);

		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, inventory));
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(0, deprioritized);

		inventoryChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		assertEquals(1, deprioritized);
	}

	@Test
	public void notifiesWhenMiningStops()
	{
		enterMlm();
		mineThenStop();

		verify(notifier).notify(eq(Notification.ON), anyString());
	}

	@Test
	public void staysQuietWhileMining()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(20);

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void staysQuietThroughTheGapsBetweenSwings()
	{
		enterMlm();

		mineForSwings(30, IDLE_DELAY_TICKS - 1);

		verify(notifier, never()).notify(any(Notification.class), anyString());

		tick(IDLE_DELAY_TICKS);
		verify(notifier).notify(any(Notification.class), anyString());
	}

	@Test
	public void notifiesOncePerStop()
	{
		enterMlm();
		mineThenStop();
		tick(20);

		verify(notifier, times(1)).notify(any(Notification.class), anyString());

		mineThenStop();
		verify(notifier, times(2)).notify(any(Notification.class), anyString());
	}

	@Test
	public void fillingTheSackWhileMiningNotifies()
	{
		enterMlm();
		deposited(100);
		sackVarbitChanged();
		animating(MINING_ANIMATION);

		carrying(8);
		inventoryChanged();

		verify(notifier).notify(eq(Notification.ON), contains("sack is full"));
	}

	@Test
	public void miningPastAFullLoadBlockNotifiesAtTheBlockCount()
	{
		when(config.blockOnFullLoad()).thenReturn(true);
		otherItems = 2;
		carrying(0);
		enterMlm();
		deposited(105);
		sackVarbitChanged();
		animating(MINING_ANIMATION);
		tick(1);
		verify(notifier, never()).notify(any(Notification.class), contains("sack is full"));

		carrying(3);
		inventoryChanged();

		verify(notifier).notify(eq(Notification.ON), contains("sack is full"));
	}

	@Test
	public void miningIntoAFullSackNotifiesOncePerSession()
	{
		enterMlm();
		fillSack();
		animating(MINING_ANIMATION);
		tick(5);
		verify(notifier, times(1)).notify(any(Notification.class), contains("sack is full"));

		animating(IDLE_ANIMATION);
		tick(IDLE_DELAY_TICKS);
		animating(MINING_ANIMATION);
		tick(1);

		verify(notifier, times(2)).notify(any(Notification.class), contains("sack is full"));
	}

	@Test
	public void aFullSackWhileNotMiningDoesNotNotify()
	{
		enterMlm();
		fillSack();

		verify(notifier, never()).notify(any(Notification.class), contains("sack is full"));
	}

	@Test
	public void aFullInventoryDoesNotNotifyTheSackIsFull()
	{
		otherItems = 1;
		carrying(0);
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		animating(MINING_ANIMATION);

		carrying(27);
		inventoryChanged();

		verify(notifier, never()).notify(any(Notification.class), contains("sack is full"));
	}

	@Test
	public void theConfiguredNotificationIsPassedToTheNotifier()
	{
		when(config.miningStoppedNotification()).thenReturn(Notification.OFF);
		enterMlm();
		mineThenStop();

		verify(notifier).notify(eq(Notification.OFF), anyString());
	}

	@Test
	public void miningOutsideTheMineIsIgnored()
	{
		inRegion(ELSEWHERE_REGION);
		gameState(GameState.LOADING);
		mineThenStop();

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void leavingTheMineMidSwingDoesNotAlert()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		animating(IDLE_ANIMATION);
		leaveMlm();
		tick(20);

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void walkingAwayDoesNotAlert()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		click("Walk here", MenuAction.WALK);
		animating(IDLE_ANIMATION);
		tick(20);

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void clickingAnotherObjectDoesNotAlert()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		click("Deposit", MenuAction.GAME_OBJECT_FIRST_OPTION);
		animating(IDLE_ANIMATION);
		tick(20);

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void aSwingOutlivingTheClickDoesNotAlert()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		click("Walk here", MenuAction.WALK);
		animating(MINING_ANIMATION);
		tick(1);
		animating(IDLE_ANIMATION);
		tick(20);

		verify(notifier, never()).notify(any(Notification.class), anyString());
	}

	@Test
	public void theNextVeinStillAlerts()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		click("Mine", MenuAction.GAME_OBJECT_FIRST_OPTION);
		animating(IDLE_ANIMATION);
		tick(5);
		verify(notifier, never()).notify(any(Notification.class), anyString());

		mineThenStop();
		verify(notifier).notify(any(Notification.class), anyString());
	}

	@Test
	public void anInterfaceClickStillAlerts()
	{
		enterMlm();
		animating(MINING_ANIMATION);
		tick(3);

		click("Eat", MenuAction.CC_OP);
		click("Toggle Run", MenuAction.CC_OP_LOW_PRIORITY);
		click("Examine", MenuAction.EXAMINE_OBJECT);
		click("Cancel", MenuAction.CANCEL);

		animating(IDLE_ANIMATION);
		tick(IDLE_DELAY_TICKS);

		verify(notifier).notify(any(Notification.class), anyString());
	}
}
