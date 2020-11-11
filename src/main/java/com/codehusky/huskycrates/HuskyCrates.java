package com.codehusky.huskycrates;

import com.codehusky.huskycrates.commands.HuskyCommandManager;
import com.codehusky.huskycrates.crate.CrateUtilities;
import com.codehusky.huskycrates.crate.PhysicalCrate;
import com.codehusky.huskycrates.crate.VirtualCrate;
import com.codehusky.huskycrates.crate.config.CrateReward;
import com.codehusky.huskycrates.crate.db.DBReader;
import com.codehusky.huskyui.StateContainer;
import com.codehusky.huskyui.states.Page;
import com.codehusky.huskyui.states.element.Element;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityStatue;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.data.type.DyeColors;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import com.codehusky.huskycrates.lang.LangData;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by lokio on 12/28/2016.
 */

@Plugin(id = "huskycrates", name = "HuskyCrates", version = "1.8.1", description = "A CratesReloaded Replacement for Sponge? lol", dependencies = {@Dependency(id = "huskyui", version = "0.2.1")})
public class HuskyCrates {
    //@Inject
    public Logger logger;

    @Inject
    public PluginContainer pC;

    @Inject
    @ConfigDir(sharedRoot = false)
    public Path configDir;

    @Inject
    @DefaultConfig(sharedRoot = false)
    public ConfigurationLoader<CommentedConfigurationNode> crateConfig;
    public Scheduler scheduler;
    public CrateUtilities crateUtilities = new CrateUtilities(this);
    public String huskyCrateIdentifier = "☼1☼2☼3HUSKYCRATE-";
    public static HuskyCrates instance;
    public HuskyAPI huskyAPI;
    public LangData langData = new LangData();
    public Set<BlockType> validCrateBlocks = new HashSet<>();

    @Listener
    public void gameInit(GamePreInitializationEvent event) {
        logger = LoggerFactory.getLogger(pC.getName());
        instance = this;
        huskyAPI = new HuskyAPI();

        CommentedConfigurationNode conf;
        try {
            conf = crateConfig.load();
            if (!conf.getNode("lang").isVirtual()) {
                langData = new LangData(conf.getNode("lang"));
            } else
                logger.info("Using default lang settings.");

        } catch (Exception e) {
            crateUtilities.exceptionHandler(e);
        }
    }

    @Listener
    public void gameStarted(GameStartedServerEvent event) {
        HuskyCommandManager huskyCommandManager = new HuskyCommandManager();

        scheduler = Sponge.getScheduler();
        Sponge.getCommandManager().register(this, huskyCommandManager.getCrateSpec(), "crate");
        logger.info("Crates has been started.");
    }

    @Listener(order = Order.POST)
    public void postGameStart(GameStartedServerEvent event) {
        Sponge.getScheduler().createTaskBuilder().execute(task -> {
            logger.info("Initalizing config...");
            if (!crateUtilities.hasInitalizedVirtualCrates) {
                crateUtilities.generateVirtualCrates(crateConfig);
            }
            crateUtilities.hasInitalizedVirtualCrates = true; // doublecheck
            logger.info("Done initalizing config.");
            logger.info("Attempting legacy Physical Crates method (this will be removed in a later version)");
            CommentedConfigurationNode root;
            boolean convertFired = false;
            try {
                root = crateConfig.load();
                double max = root.getNode("positions").getChildrenList().size();
                double count = 0;
                for (VirtualCrate vc : crateUtilities.crateTypes.values()) {
                    if (vc.pendingKeys.size() > 0) {
                        logger.warn("legacy keys loaded! warn warn warn.");
                        if (!root.getNode("keys").isVirtual()) {
                            root.removeChild("keys");
                        }
                        convertFired = true;
                    }
                }
                if (!root.getNode("positions").isVirtual()) {
                    convertFired = true;
                    logger.warn("Legacy position data detected. Will convert.");
                    for (CommentedConfigurationNode node : root.getNode("positions").getChildrenList()) {
                        count++;
                        Location<World> location;
                        try {
                            location = node.getNode("location").getValue(TypeToken.of(Location.class));
                        } catch (InvalidDataException err2) {
                            logger.warn("Bug sponge developers about world UUIDs!");
                            location = new Location<>(Sponge.getServer().getWorld(node.getNode("location", "WorldName").getString()).get(), node.getNode("location", "X").getDouble(), node.getNode("location", "Y").getDouble(), node.getNode("location", "Z").getDouble());
                        }
                        if (!crateUtilities.physicalCrates.containsKey(location))
                            crateUtilities.physicalCrates.put(location, new PhysicalCrate(location, node.getNode("crateID").getString(), HuskyCrates.instance, node.getNode("location", "BlockType").getString().equals("minecraft:air")));
                        logger.info("(LEGACY) PROGRESS: " + Math.round((count / max) * 100) + "%");
                    }
                    root.removeChild("positions");
                }
                if (!root.getNode("users").isVirtual()) {
                    for (Object uuidPre : root.getNode("users").getChildrenMap().keySet()) {
                        for (Object crateIDPre : root.getNode("users", uuidPre, "keys").getChildrenMap().keySet()) {
                            String uuid = uuidPre.toString();
                            String crateID = crateIDPre.toString();
                            int amount = root.getNode("users", uuid, "keys", crateID).getInt(0);
                            HuskyCrates.instance.crateUtilities.crateTypes.get(crateID).virtualBalances.put(uuid, amount);
                        }
                    }
                    root.removeChild("users");
                }
                crateConfig.save(root);

            } catch (Exception e) {
                crateUtilities.exceptionHandler(e);
                if (event.getCause().root() instanceof Player) {
                    CommandSource cs = (CommandSource) event.getCause().root();
                    cs.sendMessage(Text.of(TextColors.GOLD, "HuskyCrates", TextColors.WHITE, ":", TextColors.RED, " An error has occured. Please check the console for more information."));
                }
                return;
            }
            logger.info("Done with legacy loading technique");
            logger.info("Running DB routine.");
            try {
                DBReader.dbInitCheck();
                if (convertFired) {
                    logger.info("Saving data.");
                    DBReader.saveHuskyData();
                    logger.info("Done saving data.");
                } else {
                    logger.info("Loading data.");
                    DBReader.loadHuskyData();
                    logger.info("Done loading data.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            logger.info("DB Data routine finished.");
            crateUtilities.startParticleEffects();

            logger.info("Initalization complete.");
            Sponge.getScheduler().createTaskBuilder().execute(() -> {
                try {
                    DBReader.dbInitCheck();
                    DBReader.saveHuskyData();
                    logger.info("Updated Database.");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }).interval(15, TimeUnit.MINUTES).delay(15, TimeUnit.MINUTES).async().submit(HuskyCrates.instance);
        }).delayTicks(1).submit(this);
    }

    @Listener
    public void gameReloaded(GameReloadEvent event) {
        try {
            DBReader.dbInitCheck();
            DBReader.saveHuskyData();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        langData = null;
        if (event.getCause().root() instanceof Player) {
            CommandSource cs = (CommandSource) event.getCause().root();
            cs.sendMessage(Text.of(TextColors.GOLD, "HuskyCrates", TextColors.WHITE, ":", TextColors.YELLOW, " Please check console to verify that any config modifications you've done are valid."));
        }

        langData = new LangData();
        CommentedConfigurationNode root;
        try {
            root = crateConfig.load();
            if (!root.getNode("lang").isVirtual())
                langData = new LangData(root.getNode("lang"));
            else
                logger.info("Using default lang settings.");
            crateUtilities.generateVirtualCrates(crateConfig);

        } catch (Exception e) {
            crateUtilities.exceptionHandler(e);
            if (event.getCause().root() instanceof Player) {
                CommandSource cs = (CommandSource) event.getCause().root();
                cs.sendMessage(Text.of(TextColors.GOLD, "HuskyCrates", TextColors.WHITE, ":", TextColors.RED, " An error has occured. Please check the console for more information."));
            }
            return;
        }
        try {

            DBReader.loadHuskyData();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        crateUtilities.startParticleEffects();
    }

    @Listener(order = Order.PRE)
    public void placeBlock(ChangeBlockEvent event) {
        if (event.getCause().root() instanceof Player) {
            Player plr = (Player) event.getCause().root();

            if (event instanceof ChangeBlockEvent.Place || event instanceof ChangeBlockEvent.Break) {
                Location<World> location = event.getTransactions().get(0).getOriginal().getLocation().get();
                BlockState blockState = location.getBlock();
                blockState.toContainer().set(DataQuery.of("rock"), 1);
                if (validCrateBlocks.contains(blockState.getType())) {
                    if (event instanceof ChangeBlockEvent.Place) {
                        if (plr.getItemInHand(HandTypes.MAIN_HAND).isPresent()) {
                            Optional<Object> tt = plr.getItemInHand(HandTypes.MAIN_HAND).get().toContainer().get(DataQuery.of("UnsafeData", "crateID"));
                            if (tt.isPresent()) {
                                String crateID = tt.get().toString();
                                if (!plr.hasPermission("huskycrates.tester")) {
                                    event.setCancelled(true);
                                    return;
                                }
                                if (!crateUtilities.physicalCrates.containsKey(location))
                                    crateUtilities.physicalCrates.put(location, new PhysicalCrate(location, crateID, this, false));
                                updatePhysicalCrates();
                            }
                        }
                    }
                } else if (event instanceof ChangeBlockEvent.Break) {
                    if (crateUtilities.physicalCrates.containsKey(location)) {
                        if (!plr.hasPermission("huskycrates.tester")) {
                            event.setCancelled(true);
                            return;
                        }
                        crateUtilities.flag = true;
                        crateUtilities.physicalCrates.get(location).as.remove();
                        crateUtilities.physicalCrates.remove(location);
                        updatePhysicalCrates();
                    }
                }
            }
        }
    }

    private boolean updating = false;

    public void updatePhysicalCrates() {
        if (updating)
            return;
        updating = true;
        try {
            DBReader.dbInitCheck();
            DBReader.saveHuskyData();
            DBReader.loadHuskyData();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        crateUtilities.flag = false;
        updating = false;
    }

    public void keyHandler(Player plr, int keyResult, VirtualCrate vc, Location<World> blk, String crateType) {
        if (keyResult == 1 || keyResult == 2) {
            if (!vc.freeCrate && keyResult == 1) {
                ItemStack inhand = plr.getItemInHand(HandTypes.MAIN_HAND).get();
                if (!plr.hasPermission("huskycrates.tester")) {
                    if (inhand.getQuantity() == 1)
                        plr.setItemInHand(HandTypes.MAIN_HAND, null);
                    else {
                        ItemStack tobe = inhand.copy();
                        tobe.setQuantity(tobe.getQuantity() - 1);
                        plr.setItemInHand(HandTypes.MAIN_HAND, tobe);
                    }
                } else {
                    plr.sendMessage(Text.of(TextColors.GRAY, "Since you are a tester, a key was not taken."));
                }
            } else if (keyResult == 2) {
                if (!plr.hasPermission("huskycrates.tester")) {
                    vc.takeVirtualKey(plr);
                    plr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                            vc.getLangData().formatter(vc.getLangData().vkeyUseNotifier, null, plr, vc, null, crateUtilities.physicalCrates.get(blk), null)
                    ));
                } else {
                    plr.sendMessage(Text.of(TextColors.GRAY, "Since you are a tester, a virtual key was not taken."));
                    plr.sendMessage(Text.of(TextColors.GRAY, "You can remove them manually by withdrawing your keys."));
                }
            }
            Task.Builder upcoming = scheduler.createTaskBuilder();
            crateUtilities.physicalCrates.get(blk).handleUse(plr);
            upcoming.execute(() -> crateUtilities.launchCrateForPlayer(crateType, plr, this)).delayTicks(1).submit(this);
            return;

        } else if (keyResult == -1) {
            plr.playSound(SoundTypes.BLOCK_IRON_DOOR_CLOSE, blk.getPosition(), 1);
            try {
                plr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                        vc.getLangData().formatter(vc.getLangData().freeCrateWaitMessage, null, plr, vc, null, crateUtilities.physicalCrates.get(blk), null)
                ));
            } catch (Exception e) {
                plr.sendMessage(Text.of(TextColors.RED, "Critical crate failure, contact the administrator. (Admins, check console!)"));
                e.printStackTrace();
            }
            return;
        } else if (keyResult == -2) {
            plr.sendMessage(Text.of(TextColors.RED, "Unfortunately, the key you attempted to use is a legacy key. Please contact a server administrator for details."));
            plr.sendMessage(Text.of(TextColors.RED, "This incident has been logged to the console."));
            String id = plr.getItemInHand(HandTypes.MAIN_HAND).get().toContainer().get(DataQuery.of("UnsafeData", "crateID")).get().toString();
            for (Player player : Sponge.getServer().getOnlinePlayers()) {
                if (player.hasPermission("huskycrates.adminlog")) {
                    player.sendMessage(Text.of(TextColors.DARK_RED, "[HuskyCrates][Legacy/Dupe] ", TextColors.RED, plr.getName() + " used a legacy " + id + " key"));
                    player.playSound(SoundTypes.ENTITY_CAT_HISS, player.getLocation().getPosition(), 1.0);
                }
            }
            logger.error("[DUPE LOG] " + plr.getName() + " attempted to use a legacy " + id + " key.");
            plr.setItemInHand(HandTypes.MAIN_HAND, null);
            return;
        } else if (keyResult == -3) {
            plr.sendMessage(Text.of(TextColors.RED, "You appear to have used a duplicated key. Fortunately, we caught you."));
            plr.sendMessage(Text.of(TextColors.RED, "This incident has been reported to admins and the console."));
            String id = plr.getItemInHand(HandTypes.MAIN_HAND).get().toContainer().get(DataQuery.of("UnsafeData", "crateID")).get().toString();
            for (Player player : Sponge.getServer().getOnlinePlayers()) {
                if (player.hasPermission("huskycrates.adminlog")) {
                    player.sendMessage(Text.of(TextColors.DARK_RED, "[HuskyCrates][Dupe] ", TextColors.RED, plr.getName() + " used a duplicated " + id + " key"));
                    player.playSound(SoundTypes.ENTITY_CAT_HISS, player.getLocation().getPosition(), 1.0);
                }
            }
            logger.error("[DUPE LOG] " + plr.getName() + " used a duplicated " + id + " key.");
            plr.setItemInHand(HandTypes.MAIN_HAND, null);
            return;
        }
        plr.playSound(SoundTypes.BLOCK_ANVIL_LAND, blk.getPosition(), 0.3);
        try {
            plr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                    vc.getLangData().formatter(vc.getLangData().noKeyMessage, null, plr, vc, null, null, null)
            ));
        } catch (Exception e) {
            plr.sendMessage(Text.of(TextColors.RED, "Critical crate failure, contact the administrator. (Admins, check console!)"));
            e.printStackTrace();
        }
    }

    @Listener
    public void crateInteract(InteractBlockEvent.Secondary.MainHand event) {
        if (!event.getTargetBlock().getLocation().isPresent())
            return;

        Location<World> blk = event.getTargetBlock().getLocation().get();
        if (validCrateBlocks.contains(blk.getBlockType())) {
            Player plr = (Player) event.getCause().root();

            if (crateUtilities.physicalCrates.containsKey(blk)) {
                String crateType = crateUtilities.physicalCrates.get(blk).vc.id;
                VirtualCrate vc = crateUtilities.getVirtualCrate(crateType);
                if (vc.crateBlockType == blk.getBlockType()) {
                    event.setCancelled(true);
                } else {
                    return;
                }
                keyHandler(plr, crateUtilities.isAcceptedKey(crateUtilities.physicalCrates.get(blk), plr.getItemInHand(HandTypes.MAIN_HAND), plr), vc, blk, crateType);
            }


        }
    }

    @Listener
    public void onCrateRightClick(InteractBlockEvent.Primary.MainHand event) {
        if (!(event.getCause().root() instanceof Player)) return;
        Player plr = (Player) event.getCause().root();
        if (!event.getTargetBlock().getLocation().isPresent()) return;
        Location<World> location = event.getTargetBlock().getLocation().get();
        if (crateUtilities.physicalCrates.containsKey(location)) {
            if (!plr.hasPermission("huskycrates.tester") || plr.hasPermission("huskycrates.tester") && plr.getGameModeData().get(Keys.GAME_MODE).get() != GameModes.CREATIVE) {
                event.setCancelled(true);
                listRewards(plr, crateUtilities.physicalCrates.get(location).vc);
            }
        }
    }

    public void listRewards(Player player, VirtualCrate vc) {
        if (!vc.showRewardsOnLeft) return;
        /* Home */
        StateContainer test = new StateContainer();
        Page.PageBuilder rewards = Page.builder();
        rewards.setAutoPaging(true);
        rewards.setTitle(TextSerializers.FORMATTING_CODE.deserialize(vc.displayName + " Rewards"));
        rewards.setEmptyStack(ItemStack.builder()
                .itemType(ItemTypes.STAINED_GLASS_PANE)
                .add(Keys.DYE_COLOR, DyeColors.BLACK)
                .add(Keys.DISPLAY_NAME, Text.of(TextColors.DARK_GRAY, "HuskyCrates")).build());
        for (Object[] e : vc.getItemSet()) {
            CrateReward rew = (CrateReward) e[1];
            rewards.addElement(new Element(rew.getDisplayItem()));
        }
        test.setInitialState(rewards.build("rewards"));
        test.launchFor(player);
    }

    @Listener
    public void entityMove(MoveEntityEvent event) {
        if (crateUtilities.physicalCrates.containsKey(event.getFromTransform().getLocation())) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void entityInteract(InteractEntityEvent.Secondary.MainHand event) {
        if (!(event.getCause().root() instanceof Player)) {
            return;
        }
        Player plr = (Player) event.getCause().root();
        if (plr.getItemInHand(HandTypes.MAIN_HAND).isPresent() && plr.hasPermission("huskycrates.wand")) {
            ItemStack hand = plr.getItemInHand(HandTypes.MAIN_HAND).get();
            if (hand.getType() == ItemTypes.BLAZE_ROD) {
                if (hand.toContainer().get(DataQuery.of("UnsafeData", "crateID")).isPresent()) {
                    Entity targetEntity = event.getTargetEntity();
                    if (!crateUtilities.physicalCrates.containsKey(targetEntity.getLocation())) {
                        event.getTargetEntity().offer(Keys.AI_ENABLED, false);
                        event.getTargetEntity().offer(Keys.IS_SILENT, true);
                        //Damn it Pixelmon
                        if (targetEntity instanceof EntityStatue) {
                            ((EntityStatue) targetEntity).setLabel("");
                        }
                        crateUtilities.physicalCrates.put(targetEntity.getLocation(),
                                new PhysicalCrate(targetEntity.getLocation(), hand.toContainer().get(DataQuery.of("UnsafeData", "crateID")).get().toString(), this, true));
                        updatePhysicalCrates();
                    } else {
                        event.getTargetEntity().offer(Keys.AI_ENABLED, true);
                        event.getTargetEntity().offer(Keys.IS_SILENT, false);
                        crateUtilities.physicalCrates.get(targetEntity.getLocation()).as.remove();
                        crateUtilities.physicalCrates.remove(targetEntity.getLocation());
                        updatePhysicalCrates();
                    }
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (crateUtilities.physicalCrates.containsKey(event.getTargetEntity().getLocation())) {
            String crateType = crateUtilities.physicalCrates.get(event.getTargetEntity().getLocation()).vc.id;
            VirtualCrate vc = crateUtilities.getVirtualCrate(crateType);
            event.setCancelled(true);
            int keyResult = crateUtilities.isAcceptedKey(crateUtilities.physicalCrates.get(event.getTargetEntity().getLocation()), plr.getItemInHand(HandTypes.MAIN_HAND), plr);
            keyHandler(plr, keyResult, vc, event.getTargetEntity().getLocation(), crateType);

        }

    }

    public HuskyAPI getHuskyAPI() {
        return this.huskyAPI;
    }

    public CrateUtilities getCrateUtilities() {
        return crateUtilities;
    }
}
