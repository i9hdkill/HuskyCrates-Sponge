package com.codehusky.huskycrates.crate;

import com.codehusky.huskycrates.crate.db.DBReader;
import com.flowpowered.math.vector.Vector3d;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleOptions;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Color;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import com.codehusky.huskycrates.HuskyCrates;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Random;
import java.util.UUID;

/**
 * Created by lokio on 1/2/2017.
 */

public class PhysicalCrate {
    public Location<World> location;
    private String crateId;
    public VirtualCrate vc;
    public Entity ent = null;
    private HuskyCrates huskyCrates;
    public boolean isEntity = false;
    double randomTimeOffset = new Random().nextDouble()*2000;
    public void handleUse(Player player){
        if(vc.lastUsed.containsKey(player.getUniqueId())){
            vc.lastUsed.remove(player.getUniqueId());
        }
        vc.lastUsed.put(player.getUniqueId(), LocalDateTime.now());
        try {
            DBReader.saveHuskyData();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public PhysicalCrate(Location<World> crateLocation, String crateId, HuskyCrates huskyCrates, boolean isEntity){
        this.location = crateLocation;
        this.crateId = crateId;
        this.huskyCrates = huskyCrates;
        this.vc = huskyCrates.crateUtilities.getVirtualCrate(crateId);
        this.isEntity = isEntity;
        if(crateLocation != null)
            createHologram();
    }

    public void createHologram() {
        if(location.getExtent().getChunk(location.getChunkPosition()).isPresent()) {
            if (location.getExtent().getChunk(location.getChunkPosition()).get().isLoaded()) {
                if (ent == null || !ent.isLoaded()) {
                    if(isEntity){
                        Collection<Entity> potential = location.getExtent().getNearbyEntities(location.getPosition(),0.2);
                        if(potential.size() != 0){
                            ent = (Entity) potential.toArray()[0];
                            ent.offer(Keys.HAS_GRAVITY, false);
                            ent.offer(Keys.CUSTOM_NAME_VISIBLE, true);
                            String name = "&cERROR, CHECK CONSOLE!";
                            try {
                                name = huskyCrates.crateUtilities.getVirtualCrate(crateId).displayName;
                            } catch (Exception e) {
                                //e.printStackTrace();
                            }
                            ent.offer(Keys.DISPLAY_NAME, TextSerializers.FORMATTING_CODE.deserialize(name));
                        }
                    } else {

                        ent = location.getExtent().createEntity(EntityTypes.ARMOR_STAND, location.getPosition().add(0.5, 1, 0.5));
                        ent.setCreator(UUID.fromString(huskyCrates.armorStandIdentifier));
                        ent.offer(Keys.HAS_GRAVITY, false);
                        ent.offer(Keys.INVISIBLE, true);
                        ent.offer(Keys.ARMOR_STAND_MARKER, true);
                        ent.offer(Keys.CUSTOM_NAME_VISIBLE, true);
                        String name = "&cERROR, CHECK CONSOLE!";
                        try {
                            name = huskyCrates.crateUtilities.getVirtualCrate(crateId).displayName;
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                        ent.offer(Keys.DISPLAY_NAME, TextSerializers.FORMATTING_CODE.deserialize(name));
                        boolean worked = location.getExtent().spawnEntity(ent);
                        if (!worked) {
                            ent = null;
                            return;
                        }
                    }
                }
            }
        }
    }

    void runParticles() {
        try {
            createHologram();
            double time = randomTimeOffset + (Sponge.getServer().getRunningTimeTicks() * 0.25);
            double size = 0.8;

            double x = Math.sin(time) * size;
            double y = Math.sin(time * 2) * 0.2 - 0.45;
            if(isEntity)
                y+=1;
            double z = Math.cos(time) * size;
            Color clr1 = Color.ofRgb(100,100,100);
            Color clr2 = Color.ofRgb(255, 0, 0);
            if(vc.getOptions().containsKey("clr1")){
                clr1 = (Color) vc.getOptions().get("clr1");
            }
            if(vc.getOptions().containsKey("clr2")){
                clr2 = (Color) vc.getOptions().get("clr2");
            }
            ent.getWorld().spawnParticles(
                    ParticleEffect.builder()
                            .type(ParticleTypes.REDSTONE_DUST)
                            .option(ParticleOptions.COLOR, clr1)
                            .build(),
                    ent.getLocation()
                            .getPosition()
                            .add(x, y, z));

            x = Math.cos(time + 10) * size;
            y = Math.sin(time * 2 + 10) * 0.2 - 0.55;
            if(isEntity)
                y+=1;
            z = Math.sin(time + 10) * size;
            ent.getWorld().spawnParticles(
                    ParticleEffect.builder()
                            .type(ParticleTypes.REDSTONE_DUST)
                            .option(ParticleOptions.COLOR, clr2)
                            .build(),
                    ent.getLocation()
                            .getPosition()
                            .add(x, y, z));
        }catch (Exception e){

        }
    }
}
