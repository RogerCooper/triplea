package games.strategy.triplea.delegate;

import static games.strategy.triplea.delegate.GameDataTestUtil.removeFrom;
import static games.strategy.triplea.delegate.MockDelegateBridge.advanceToStep;
import static games.strategy.triplea.delegate.MockDelegateBridge.newDelegateBridge;
import static games.strategy.triplea.delegate.MockDelegateBridge.whenGetRandom;
import static games.strategy.triplea.delegate.MockDelegateBridge.withValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.BattleTracker;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.battle.steps.retreat.OffensiveGeneralRetreat;
import games.strategy.triplea.util.TransportUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;

class MoveDelegateTest extends AbstractDelegateTestCase {
  private MoveDelegate delegate;
  private IDelegateBridge bridge;

  @BeforeEach
  void setupMoveDelegate() {
    bridge = newDelegateBridge(british);
    advanceToStep(bridge, "britishCombatMove");
    final InitializationDelegate initDel =
        (InitializationDelegate) gameData.getDelegate("initDelegate");
    initDel.setDelegateBridgeAndPlayer(bridge);
    initDel.start();
    initDel.end();
    delegate = new MoveDelegate();
    delegate.initialize("MoveDelegate", "MoveDelegate");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
  }

  @Test
  void testNotUnique() {
    final Route route = new Route(egypt, eastAfrica);
    final Unit unit = armour.create(british);
    final List<Unit> units = List.of(unit, unit);
    final String results = delegate.move(units, route);
    assertError(results);
  }

  @Test
  void testNotEnoughUnits() {
    final Route route = new Route(egypt, eastAfrica);
    final String results = delegate.move(armour.create(10, british), route);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(2, eastAfrica.getUnitCollection().size());
    assertError(results);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(2, eastAfrica.getUnitCollection().size());
  }

  @Test
  void testCantMoveEnemy() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 1);
    final Route route = new Route(algeria, libya);
    assertEquals(1, algeria.getUnitCollection().size());
    assertEquals(0, libya.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(1, algeria.getUnitCollection().size());
    assertEquals(0, libya.getUnitCollection().size());
  }

  @Test
  void testSimpleMove() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(egypt, eastAfrica);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(2, eastAfrica.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(4, eastAfrica.getUnitCollection().size());
  }

  @Test
  void testSimpleMoveLength2() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(egypt, eastAfrica, kenya);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(0, kenya.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(2, kenya.getUnitCollection().size());
  }

  @Test
  void testCanReturnToCarrier() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 3);
    final Route route = new Route(congoSeaZone, southAtlantic, antarticSea);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testLandOnCarrier() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 2);
    final Route route = new Route(egypt, eastAfrica, kenya, mozambiqueSeaZone, redSea);
    // extra movement to force landing
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(6, redSea.getUnitCollection().size());
  }

  @Test
  void testCantLandWithNoCarrier() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 2);
    final Route route = new Route(egypt, eastAfrica, kenya, redSea, mozambiqueSeaZone);
    // extra movement to force landing
    // no carriers
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
  }

  @Test
  void testNotEnoughCarrierCapacity() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 5);
    final Route route = new Route(egypt, eastAfrica, kenya, mozambiqueSeaZone, redSea);
    // exact movement to force landing
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
  }

  @Test
  void testLandMoveToWaterWithNoTransports() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(egypt, eastMediteranean);
    // exact movement to force landing
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(0, eastMediteranean.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(0, eastMediteranean.getUnitCollection().size());
  }

  @Test
  void testSeaMove() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(carrier, 2);
    final Route route = new Route(redSea, mozambiqueSeaZone);
    // exact movement to force landing
    assertEquals(4, redSea.getUnitCollection().size());
    assertEquals(0, mozambiqueSeaZone.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(2, redSea.getUnitCollection().size());
    assertEquals(2, mozambiqueSeaZone.getUnitCollection().size());
  }

  @Test
  void testSeaCantMoveToLand() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(carrier, 2);
    final Route route = new Route(redSea, egypt);
    // exact movement to force landing
    assertEquals(4, redSea.getUnitCollection().size());
    assertEquals(18, egypt.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(4, redSea.getUnitCollection().size());
    assertEquals(18, egypt.getUnitCollection().size());
  }

  @Test
  void testLandMoveToWaterWithTransportsFull() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    map.put(infantry, 2);
    final Route route = new Route(equatorialAfrica, congoSeaZone);
    // exact movement to force landing
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(11, congoSeaZone.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(11, congoSeaZone.getUnitCollection().size());
  }

  @Test
  void testAirCanFlyOverWater() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(bomber, 2);
    final Route route = new Route(egypt, redSea, syria);
    // exact movement to force landing
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testLandMoveToWaterWithTransportsEmpty() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(egypt, redSea);
    // exact movement to force landing
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, redSea.getUnitCollection().size());
    final Collection<Unit> units = GameDataTestUtil.getUnits(map, route.getStart());
    final Map<Unit, Unit> unitsToTransports =
        TransportUtils.mapTransports(route, units, route.getEnd().getUnits());
    final String results =
        delegate.performMove(new MoveDescription(units, route, unitsToTransports));
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(6, redSea.getUnitCollection().size());
  }

  @Test
  void testBlitzWithArmour() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(egypt, libya, algeria);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(1, algeria.getUnitCollection().size());
    assertEquals(libya.getOwner(), japanese);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(3, algeria.getUnitCollection().size());
    assertEquals(libya.getOwner(), british);
  }

  @Test
  void testCant2StepBlitzWithNonBlitzingUnits() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    Route route = new Route(egypt, libya);
    // Disable canBlitz attachment
    gameData.performChange(
        ChangeFactory.attachmentPropertyChange(UnitAttachment.get(armour), "false", "canBlitz"));
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Validate move happened
    assertEquals(1, libya.getUnitCollection().size());
    assertEquals(libya.getOwner(), british);
    // Try to move 2nd space
    route = new Route(libya, algeria);
    // Fail because not 'canBlitz'
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testCantBlitzNuetral() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(equatorialAfrica, westAfrica, algeria);
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(1, algeria.getUnitCollection().size());
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(1, algeria.getUnitCollection().size());
  }

  @Test
  void testOverrunNeutral() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final Route route = new Route(equatorialAfrica, westAfrica);
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(0, westAfrica.getUnitCollection().size());
    assertEquals(GamePlayer.NULL_PLAYERID, westAfrica.getOwner());
    assertEquals(35, british.getResources().getQuantity(pus));
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(2, equatorialAfrica.getUnitCollection().size());
    assertEquals(2, westAfrica.getUnitCollection().size());
    assertEquals(westAfrica.getOwner(), british);
    assertEquals(32, british.getResources().getQuantity(pus));
  }

  @Test
  void testAirCanOverFlyEnemy() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(bomber, 2);
    final Route route = new Route(egypt, libya, algeria, equatorialAfrica);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testOverrunNeutralMustStop() {
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    Route route = new Route(equatorialAfrica, westAfrica);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    map = new IntegerMap<>();
    map.put(armour, 2);
    route = new Route(westAfrica, equatorialAfrica);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testMultipleMovesExceedingMovementLimit() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 2);
    Route route = new Route(eastAfrica, kenya);
    assertEquals(2, eastAfrica.getUnitCollection().size());
    assertEquals(0, kenya.getUnitCollection().size());
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(0, eastAfrica.getUnitCollection().size());
    assertEquals(2, kenya.getUnitCollection().size());
    route = new Route(kenya, egypt);
    assertEquals(2, kenya.getUnitCollection().size());
    assertEquals(18, egypt.getUnitCollection().size());
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    assertEquals(2, kenya.getUnitCollection().size());
    assertEquals(18, egypt.getUnitCollection().size());
  }

  @Test
  void testMovingUnitsWithMostMovement() {
    // move 2 tanks to equatorial africa
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    Route route = new Route(egypt, equatorialAfrica);
    assertEquals(18, egypt.getUnitCollection().size());
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(16, egypt.getUnitCollection().size());
    assertEquals(6, equatorialAfrica.getUnitCollection().size());
    // now move 2 tanks out of equatorial africa to east africa
    // only the tanks with movement 2 can make it,
    // this makes sure that the correct units are moving
    route = new Route(equatorialAfrica, egypt, eastAfrica);
    assertEquals(6, equatorialAfrica.getUnitCollection().size());
    assertEquals(2, eastAfrica.getUnitCollection().size());
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertEquals(4, equatorialAfrica.getUnitCollection().size());
    assertEquals(4, eastAfrica.getUnitCollection().size());
  }

  @Test
  void testTransportsMustStayWithUnits() {
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    Route route = new Route(egypt, redSea);
    final Collection<Unit> units = GameDataTestUtil.getUnits(map, route.getStart());
    final Map<Unit, Unit> unitsToTransports =
        TransportUtils.mapTransports(route, units, route.getEnd().getUnits());
    String results = delegate.performMove(new MoveDescription(units, route, unitsToTransports));
    assertValid(results);
    map = new IntegerMap<>();
    map.put(transport, 2);
    route = new Route(redSea, indianOcean);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testUnitsStayWithTransports() {
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    Route route = new Route(egypt, redSea);
    final Collection<Unit> units = GameDataTestUtil.getUnits(map, route.getStart());
    final Map<Unit, Unit> unitsToTransports =
        TransportUtils.mapTransports(route, units, route.getEnd().getUnits());
    String results = delegate.performMove(new MoveDescription(units, route, unitsToTransports));
    assertValid(results);
    map = new IntegerMap<>();
    map.put(armour, 2);
    route = new Route(redSea, indianOcean);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testUnload() {
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 2);
    final Route route = new Route(congoSeaZone, equatorialAfrica);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testTransportCantLoadUnloadAfterBattle() {
    bridge = newDelegateBridge(russians);
    advanceToStep(bridge, "russianCombatMove");
    westEurope.setOwner(russians);
    // Attacking force
    final List<Unit> attackTransports = transport.create(1, russians);
    final List<Unit> attackList = bomber.create(2, russians);
    attackList.addAll(attackTransports);
    whenGetRandom(bridge).thenAnswer(withValues(1, 1));
    final IBattle battle = mock(IBattle.class);
    when(battle.getTerritory()).thenReturn(westEurope);
    final DiceRoll roll =
        DiceRoll.rollDice(
            attackList,
            false,
            russians,
            bridge,
            battle.getTerritory(),
            TerritoryEffectHelper.getEffects(balticSeaZone));
    assertEquals(2, roll.getHits());
    advanceToStep(bridge, "russianNonCombatMove");
    // Test the move
    final Collection<Unit> moveInf = infantry.create(2, russians);
    final Route route = new Route(karelia, balticSeaZone, westEurope);
    // Once loaded, shouldn't be able to unload
    final String results = delegate.move(moveInf, route);
    assertError(results);
  }

  @Test
  void testLoadUnloadLoadMoveTransports() {
    bridge = newDelegateBridge(japanese);
    advanceToStep(bridge, "japaneseCombatMove");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    // Set up the test
    removeFrom(manchuria, manchuria.getUnits());
    manchuria.setOwner(russians);
    removeFrom(japanSeaZone, japanSeaZone.getUnits());
    gameData.performChange(ChangeFactory.addUnits(japanSeaZone, transport.create(3, japanese)));
    gameData.performChange(ChangeFactory.addUnits(japan, infantry.create(3, japanese)));
    // Perform the first load
    final Route load = new Route(japan, japanSeaZone);
    final List<Unit> transports =
        CollectionUtils.getMatches(japanSeaZone.getUnits(), Matches.unitIsOfType(transport));
    final List<Unit> infantry1 =
        CollectionUtils.getNMatches(japan.getUnits(), 1, Matches.unitIsOfType(infantry));
    String results =
        delegate.performMove(
            new MoveDescription(infantry1, load, Map.of(infantry1.get(0), transports.get(0))));
    assertNull(results);
    assertEquals(
        infantry1,
        CollectionUtils.getNMatches(japanSeaZone.getUnits(), 1, Matches.unitIsOfType(infantry)));
    // Perform the first unload
    final Route unload = new Route(japanSeaZone, manchuria);
    results = delegate.move(infantry1, unload);
    assertNull(results);
    // Load another trn
    final Route route2 = new Route(japan, japanSeaZone);
    final List<Unit> infantry2 =
        CollectionUtils.getNMatches(japan.getUnits(), 1, Matches.unitIsOfType(infantry));
    results =
        delegate.performMove(
            new MoveDescription(infantry2, route2, Map.of(infantry2.get(0), transports.get(1))));
    assertNull(results);
    // Move remaining units
    final Route route3 = new Route(japanSeaZone, sfeSeaZone);
    final Collection<Unit> remainingTransports =
        CollectionUtils.getMatches(
            japanSeaZone.getUnits(),
            Matches.unitHasNotMoved().and(Matches.unitWasNotLoadedThisTurn()));
    results = delegate.move(remainingTransports, route3);
    assertNull(results);
  }

  @Test
  void testUnloadedCantMove() {
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 2);
    Route route = new Route(congoSeaZone, equatorialAfrica);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    map = new IntegerMap<>();
    // only 2 originally, would have to move the 2 we just unloaded as well
    map.put(infantry, 4);
    route = new Route(equatorialAfrica, egypt);
    // units were unloaded, shouldn't be able to move any more
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testUnloadingTransportsCantMove() {
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 4);
    Route route = new Route(congoSeaZone, equatorialAfrica);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    map = new IntegerMap<>();
    map.put(transport, 2);
    route = new Route(congoSeaZone, westAfricaSeaZone);
    // the transports unloaded so they cant move
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testTransportsCanSplit() {
    // move 1 armour to red sea
    Route route = new Route(egypt, redSea);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    Collection<Unit> units = GameDataTestUtil.getUnits(map, route.getStart());
    Map<Unit, Unit> unitsToTransports =
        TransportUtils.mapTransports(route, units, route.getEnd().getUnits());
    String results = delegate.performMove(new MoveDescription(units, route, unitsToTransports));
    assertValid(results);
    // move two infantry to red sea
    route = new Route(eastAfrica, redSea);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    units = GameDataTestUtil.getUnits(map, route.getStart());
    unitsToTransports = TransportUtils.mapTransports(route, units, route.getEnd().getUnits());
    results = delegate.performMove(new MoveDescription(units, route, unitsToTransports));
    assertValid(results);
    // try to move 1 transport to indian ocean with 1 tank
    route = new Route(redSea, indianOcean);
    map = new IntegerMap<>();
    map.put(armour, 1);
    map.put(transport, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // move the other transport to west compass
    route = new Route(redSea, westCompass);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    map.put(transport, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testUseTransportsWithLowestMovement() {
    // move transport south
    Route route = new Route(congoSeaZone, angolaSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // move transport back
    route = new Route(angolaSeaZone, congoSeaZone);
    map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // move the other transport south, should figure out that only 1 can move and will choose that
    // one
    route = new Route(congoSeaZone, angolaSeaZone);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    map.put(transport, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testCanOverrunNeutralWithoutFunds() {
    assertEquals(35, british.getResources().getQuantity(pus));
    final Change makePoor = ChangeFactory.changeResourcesChange(british, pus, -35);
    bridge.addChange(makePoor);
    assertEquals(0, british.getResources().getQuantity(pus));
    // try to take over South Africa, cant because we cant afford it
    final Route route = new Route(egypt, kenya, southAfrica);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testAirViolateNeutrality() {
    final Route route = new Route(egypt, kenya, southAfrica);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 2);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testNeutralConquered() {
    // take over neutral
    final Route route = new Route(equatorialAfrica, westAfrica);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    assertTrue(DelegateFinder.battleDelegate(gameData).getBattleTracker().wasConquered(westAfrica));
    assertFalse(DelegateFinder.battleDelegate(gameData).getBattleTracker().wasBlitzed(westAfrica));
  }

  @Test
  void testMoveTransportsTwice() {
    // move transports
    Route route = new Route(congoSeaZone, southAtlantic);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 2);
    map.put(transport, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // move again
    route = new Route(southAtlantic, angolaSeaZone);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testCantMoveThroughConqueredNeutral() {
    // take over neutral
    Route route = new Route(equatorialAfrica, westAfrica);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // make sure we cant move through it by land
    route = new Route(equatorialAfrica, westAfrica, algeria);
    map = new IntegerMap<>();
    map.put(armour, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
    // make sure we can still move units to the territory
    route = new Route(equatorialAfrica, westAfrica);
    map = new IntegerMap<>();
    map.put(armour, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // make sure air can though
    route = new Route(congoSeaZone, westAfricaSeaZone, westAfrica, equatorialAfrica);
    map = new IntegerMap<>();
    map.put(fighter, 3);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testCanBlitzThroughConqueredEnemy() {
    // take over empty enemy
    Route route = new Route(equatorialAfrica, libya);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // make sure we can still blitz through it
    route = new Route(equatorialAfrica, libya, algeria);
    map = new IntegerMap<>();
    map.put(armour, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testAirCantLandInConquered() {
    // take over empty neutral
    Route route = new Route(egypt, kenya, southAfrica);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // move carriers to ensure they can't go anywhere
    route = new Route(congoSeaZone, westAfricaSea, northAtlantic);
    Collection<Unit> units =
        new ArrayList<>(
            CollectionUtils.getMatches(
                gameData.getMap().getTerritory(congoSeaZone.toString()).getUnits(),
                Matches.unitIsCarrier()));
    results = delegate.move(units, route);
    assertValid(results);
    // move carriers to ensure they can't go anywhere
    route = new Route(redSea, eastMediteranean, blackSea);
    units =
        new ArrayList<>(
            CollectionUtils.getMatches(
                gameData.getMap().getTerritory(redSea.toString()).getUnits(),
                Matches.unitIsCarrier()));
    results = delegate.move(units, route);
    assertValid(results);
    // make sure the place cant use it to land
    // the only possibility would be newly conquered south africa
    route = new Route(congoSeaZone, southAtlantic, angolaSeaZone, southAfricaSeaZone);
    map = new IntegerMap<>();
    map.put(fighter, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testMoveAndTransportUnload() {
    // this was causing an exception
    Route route = new Route(congoSeaZone, westAfricaSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    route = new Route(westAfricaSeaZone, westAfrica);
    map = new IntegerMap<>();
    map.put(infantry, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testTakeOverAfterOverFlight() {
    // this was causing an exception
    Route route = new Route(egypt, libya);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(bomber, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    route = new Route(libya, algeria);
    // planes cannot leave a battle zone, but the territory was empty so no battle occurred
    map = new IntegerMap<>();
    map.put(bomber, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testBattleAdded() {
    final Route route = new Route(egypt, libya);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(bomber, 1);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testLargeMove() {
    // was causing an error
    final Route route = new Route(egypt, libya, algeria);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(bomber, 6);
    map.put(fighter, 6);
    map.put(armour, 6);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testAmphibiousAssaultAfterNavalBattle() {
    // move to take on brazil navy
    Route route = new Route(congoSeaZone, southBrazilSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 2);
    map.put(infantry, 4);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // try to unload transports
    route = new Route(southBrazilSeaZone, brazil);
    map = new IntegerMap<>();
    map.put(infantry, 4);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    final IBattle inBrazil =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(brazil);
    final IBattle inBrazilSea =
        DelegateFinder.battleDelegate(gameData)
            .getBattleTracker()
            .getPendingBattle(southBrazilSeaZone);
    assertNotNull(inBrazilSea);
    assertNotNull(inBrazil);
    assertEquals(
        DelegateFinder.battleDelegate(gameData)
            .getBattleTracker()
            .getDependentOn(inBrazil)
            .iterator()
            .next(),
        inBrazilSea);
  }

  @Test
  void testReloadTransportAfterRetreatAmphibious() {
    bridge = newDelegateBridge(british);
    advanceToStep(bridge, "britishCombatMove");
    Route route = new Route(northSea, balticSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    // Move from the NorthSea to the BalticSea and validate the move
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Unload transports into Finland and validate
    route = new Route(balticSeaZone, finlandNorway);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Get the attacking land units that will retreat and their number
    final List<Unit> retreatingLandUnits =
        new ArrayList<>(
            finlandNorway.getUnitCollection().getMatches(Matches.enemyUnit(germans, gameData)));
    final int retreatingLandSizeInt = retreatingLandUnits.size();
    // Get the defending land units that and their number
    final List<Unit> defendingLandUnits =
        new ArrayList<>(
            finlandNorway.getUnitCollection().getMatches(Matches.enemyUnit(british, gameData)));
    final int defendingLandSizeInt = defendingLandUnits.size();
    // Set up the battles and the dependent battles
    final IBattle inFinlandNorway =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(finlandNorway);
    final IBattle inBalticSeaZone =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(balticSeaZone);
    assertNotNull(balticSeaZone);
    assertNotNull(finlandNorway);
    assertEquals(
        DelegateFinder.battleDelegate(gameData)
            .getBattleTracker()
            .getDependentOn(inFinlandNorway)
            .iterator()
            .next(),
        inBalticSeaZone);
    // Add some defending units in case there aren't any
    final List<Unit> defendList = transport.create(1, germans);
    final List<Unit> defendSub = submarine.create(1, germans);
    defendList.addAll(defendSub);
    // fire the defending transport then the submarine (both miss)
    whenGetRandom(bridge).thenAnswer(withValues(1, 2));
    // Execute the battle and verify no hits
    final DiceRoll roll =
        DiceRoll.rollDice(
            defendList,
            true,
            germans,
            bridge,
            mock(Territory.class),
            TerritoryEffectHelper.getEffects(balticSeaZone));
    assertEquals(0, roll.getHits());
    // Get total number of units in Finland before the retreat
    final int preCountInt = finlandNorway.getUnitCollection().size();
    // Retreat from the Baltic
    final OffensiveGeneralRetreat offensiveGeneralRetreat =
        new OffensiveGeneralRetreat((BattleState) inBalticSeaZone, (BattleActions) inBalticSeaZone);
    offensiveGeneralRetreat.execute(mock(ExecutionStack.class), bridge);
    // Get the total number of units that should be left
    final int postCountInt = preCountInt - retreatingLandSizeInt;
    // Compare the number of units in Finland to begin with the number after retreating
    assertEquals(defendingLandSizeInt, postCountInt);
  }

  @Test
  void testReloadTransportAfterDyingAmphibious() {
    bridge = newDelegateBridge(british);
    advanceToStep(bridge, "britishCombatMove");
    Route route = new Route(northSea, balticSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    // Move from the NorthSea to the BalticSea and validate the move
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Unload transports into Finland and validate
    route = new Route(balticSeaZone, finlandNorway);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Get the attacking land units that will retreat and their number
    final List<Unit> retreatingLandUnits =
        new ArrayList<>(
            finlandNorway.getUnitCollection().getMatches(Matches.enemyUnit(germans, gameData)));
    final int retreatingLandSizeInt = retreatingLandUnits.size();
    // Get the defending land units that and their number
    final List<Unit> defendingLandUnits =
        new ArrayList<>(
            finlandNorway.getUnitCollection().getMatches(Matches.enemyUnit(british, gameData)));
    final int defendingLandSizeInt = defendingLandUnits.size();
    // Set up the battles and the dependent battles
    final IBattle inFinlandNorway =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(finlandNorway);
    final IBattle inBalticSeaZone =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(balticSeaZone);
    assertNotNull(balticSeaZone);
    assertNotNull(finlandNorway);
    assertEquals(
        DelegateFinder.battleDelegate(gameData)
            .getBattleTracker()
            .getDependentOn(inFinlandNorway)
            .iterator()
            .next(),
        inBalticSeaZone);
    // Add some defending units in case there aren't any
    final List<Unit> defendList = transport.create(1, germans);
    final List<Unit> defendSub = submarine.create(1, germans);
    defendList.addAll(defendSub);
    // fire the defending transport then the submarine (One hit)
    whenGetRandom(bridge).thenAnswer(withValues(0, 2));
    // Execute the battle and verify no hits
    final DiceRoll roll =
        DiceRoll.rollDice(
            defendList,
            true,
            germans,
            bridge,
            mock(Territory.class),
            TerritoryEffectHelper.getEffects(balticSeaZone));
    assertEquals(1, roll.getHits());
    // Get total number of units in Finland before the retreat
    final int preCountInt = finlandNorway.getUnitCollection().size();
    // Retreat from the Baltic
    final OffensiveGeneralRetreat offensiveGeneralRetreat =
        new OffensiveGeneralRetreat((BattleState) inBalticSeaZone, (BattleActions) inBalticSeaZone);
    offensiveGeneralRetreat.execute(mock(ExecutionStack.class), bridge);
    // Get the total number of units that should be left
    final int postCountInt = preCountInt - retreatingLandSizeInt;
    // Compare the number of units in Finland to begin with the number after retreating
    assertEquals(defendingLandSizeInt, postCountInt);
  }

  @Test
  void testReloadTransportAfterRetreatAllied() {
    bridge = newDelegateBridge(british);
    advanceToStep(bridge, "britishCombatMove");
    Route route = new Route(northSea, balticSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    // Move from the NorthSea to the BalticSea and validate the move
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Unload transports into Finland and validate
    route = new Route(balticSeaZone, karelia);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Get the attacking land units that will retreat and their number
    final List<Unit> retreatingLandUnits =
        new ArrayList<>(
            karelia.getUnitCollection().getMatches(Matches.isUnitAllied(russians, gameData)));
    final int retreatingLandSizeInt = retreatingLandUnits.size();
    // Get the defending land units that and their number
    retreatingLandUnits.addAll(
        karelia.getUnitCollection().getMatches(Matches.isUnitAllied(british, gameData)));
    final List<Unit> defendingLandUnits = new ArrayList<>();
    final int defendingLandSizeInt = defendingLandUnits.size();
    // Set up the battles and the dependent battles
    final IBattle inBalticSeaZone =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(balticSeaZone);
    assertNotNull(balticSeaZone);
    // Add some defending units in case there aren't any
    final List<Unit> defendList = transport.create(1, germans);
    final List<Unit> defendSub = submarine.create(1, germans);
    defendList.addAll(defendSub);
    // fire the defending transport then the submarine (both miss)
    whenGetRandom(bridge).thenAnswer(withValues(1, 2));
    // Execute the battle and verify no hits
    final DiceRoll roll =
        DiceRoll.rollDice(
            defendList,
            true,
            germans,
            bridge,
            mock(Territory.class),
            TerritoryEffectHelper.getEffects(balticSeaZone));
    assertEquals(0, roll.getHits());
    // Get total number of units in Finland before the retreat
    final int preCountInt = karelia.getUnitCollection().size();
    // Retreat from the Baltic
    final OffensiveGeneralRetreat offensiveGeneralRetreat =
        new OffensiveGeneralRetreat((BattleState) inBalticSeaZone, (BattleActions) inBalticSeaZone);
    offensiveGeneralRetreat.execute(mock(ExecutionStack.class), bridge);
    // Get the total number of units that should be left
    final int postCountInt = preCountInt - retreatingLandSizeInt;
    // Compare the number of units in Finland to begin with the number after retreating
    assertEquals(defendingLandSizeInt, postCountInt);
  }

  @Test
  void testReloadTransportAfterDyingAllied() {
    bridge = newDelegateBridge(british);
    advanceToStep(bridge, "britishCombatMove");
    Route route = new Route(northSea, balticSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(transport, 1);
    map.put(infantry, 2);
    // Move from the NorthSea to the BalticSea and validate the move
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Unload transports into Finland and validate
    route = new Route(balticSeaZone, karelia);
    map = new IntegerMap<>();
    map.put(infantry, 2);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // Get the attacking land units that will retreat and their number
    final List<Unit> retreatingLandUnits =
        new ArrayList<>(
            karelia.getUnitCollection().getMatches(Matches.isUnitAllied(russians, gameData)));
    final int retreatingLandSizeInt = retreatingLandUnits.size();
    // Get the defending land units that and their number
    final List<Unit> defendingLandUnits = new ArrayList<>();
    retreatingLandUnits.addAll(
        karelia.getUnitCollection().getMatches(Matches.isUnitAllied(british, gameData)));
    final int defendingLandSizeInt = defendingLandUnits.size();
    // Set up the battles and the dependent battles
    final IBattle inBalticSeaZone =
        DelegateFinder.battleDelegate(gameData).getBattleTracker().getPendingBattle(balticSeaZone);
    assertNotNull(balticSeaZone);
    // Add some defending units in case there aren't any
    final List<Unit> defendList = transport.create(1, germans);
    final List<Unit> defendSub = submarine.create(1, germans);
    defendList.addAll(defendSub);
    // fire the defending transport then the submarine (One hit)
    whenGetRandom(bridge).thenAnswer(withValues(0, 2));
    // Execute the battle and verify no hits
    final DiceRoll roll =
        DiceRoll.rollDice(
            defendList,
            true,
            germans,
            bridge,
            mock(Territory.class),
            TerritoryEffectHelper.getEffects(balticSeaZone));
    assertEquals(1, roll.getHits());
    // Get total number of units in Finland before the retreat
    final int preCountInt = karelia.getUnitCollection().size();
    // Retreat from the Baltic
    final OffensiveGeneralRetreat offensiveGeneralRetreat =
        new OffensiveGeneralRetreat((BattleState) inBalticSeaZone, (BattleActions) inBalticSeaZone);
    offensiveGeneralRetreat.execute(mock(ExecutionStack.class), bridge);
    // Get the total number of units that should be left
    final int postCountInt = preCountInt - retreatingLandSizeInt;
    // Compare the number of units in Finland to begin with the number after retreating
    assertEquals(defendingLandSizeInt, postCountInt);
  }

  @Test
  void testAirToWater() {
    final Route route = new Route(egypt, eastMediteranean);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 3);
    map.put(bomber, 3);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testNonCombatAttack() {
    advanceToStep(bridge, "britishNonCombatMove");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    final Route route = new Route(equatorialAfrica, algeria);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testNonCombatAttackNeutral() {
    advanceToStep(bridge, "britishNonCombatMove");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    final Route route = new Route(equatorialAfrica, westAfrica);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testNonCombatMoveToConquered() {
    // take over libya
    Route route = new Route(equatorialAfrica, libya);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    // go to non combat
    advanceToStep(bridge, "britishNonCombatMove");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    // move more into libya
    route = new Route(equatorialAfrica, libya);
    map = new IntegerMap<>();
    map.put(armour, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testAaCantMoveToConquered() {
    bridge = newDelegateBridge(japanese);
    advanceToStep(bridge, "japaneseCombatMove");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    final Route route = new Route(congo, kenya);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(armour, 2);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    final BattleTracker tracker = DelegateFinder.battleDelegate(gameData).getBattleTracker();
    assertTrue(tracker.wasBlitzed(kenya));
    assertTrue(tracker.wasConquered(kenya));
    map.clear();
    map.put(aaGun, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testBlitzConqueredNeutralInTwoSteps() {
    Route route = new Route(equatorialAfrica, westAfrica);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    final BattleTracker tracker = DelegateFinder.battleDelegate(gameData).getBattleTracker();
    assertFalse(tracker.wasBlitzed(westAfrica));
    assertTrue(tracker.wasConquered(westAfrica));
    map.clear();
    map.put(armour, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    route = new Route(westAfrica, algeria);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertError(results);
  }

  @Test
  void testBlitzFactory() {
    // create a factory to be taken
    final Collection<Unit> factCollection = factory.create(1, japanese);
    final Change addFactory = ChangeFactory.addUnits(libya, factCollection);
    bridge.addChange(addFactory);
    final Route route = new Route(equatorialAfrica, libya);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(infantry, 1);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    final BattleTracker tracker = DelegateFinder.battleDelegate(gameData).getBattleTracker();
    assertTrue(tracker.wasBlitzed(libya));
    assertTrue(tracker.wasConquered(libya));
    final Unit factory = factCollection.iterator().next();
    assertEquals(factory.getOwner(), british);
  }

  @Test
  void testAirCanLandOnLand() {
    final Route route = new Route(egypt, eastMediteranean, blackSea);
    final IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 1);
    final String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testAirDifferingRouts() {
    // move one air unit 3 spaces, and a second 2,
    // this was causing an exception when the validator tried to find if they could both land
    // EW: I don't know why this test is failing or what it is supposed to do...
    Route route = new Route(congoSeaZone, southAtlantic, antarticSea, angolaSeaZone);
    IntegerMap<UnitType> map = new IntegerMap<>();
    map.put(fighter, 1);
    String results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
    route = new Route(congoSeaZone, southAtlantic, antarticSea, angolaSeaZone);
    map = new IntegerMap<>();
    map.put(fighter, 1);
    results = delegate.move(GameDataTestUtil.getUnits(map, route.getStart()), route);
    assertValid(results);
  }

  @Test
  void testRoute() {
    final Route route = gameData.getMap().getRoute(angola, russia, Matches.always());
    assertNotNull(route);
    assertEquals(route.getEnd(), russia);
  }
}
