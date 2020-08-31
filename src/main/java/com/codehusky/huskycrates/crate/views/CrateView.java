package com.codehusky.huskycrates.crate.views;

import com.codehusky.huskycrates.HuskyCrates;
import com.codehusky.huskycrates.crate.CrateCommandSource;
import com.codehusky.huskycrates.crate.VirtualCrate;
import com.codehusky.huskycrates.exceptions.RandomItemSelectionFailureException;
import com.codehusky.huskycrates.lang.LangData;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.text.serializer.TextSerializers;
import com.codehusky.huskycrates.crate.config.CrateReward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by lokio on 12/29/2016.
 */
public class CrateView {
    HuskyCrates plugin;
    VirtualCrate vc;
    Player ourplr;
    ArrayList<Object[]> items;
    public Inventory getInventory(){
        return null;
    }
    //empty class for organization and such
    public void scrambleRewards(){
        ArrayList<Object[]> scrambled = new ArrayList<>();
        ArrayList<Object[]> toSift = (ArrayList<Object[]>) items.clone();
        while(toSift.size() > 0){
            //System.out.println(toSift.size());
            int pick;
            if(toSift.size() == 1) pick = 0;
            else pick = new Random().nextInt(toSift.size()-1);
            scrambled.add(toSift.get(pick));
            toSift.remove(pick);
        }
        items = scrambled;
    }
    public int itemIndexSelected() throws RandomItemSelectionFailureException {
        double random = new Random().nextFloat()*vc.getMaxProb();
        double cummProb = 0;
        for(int i = 0; i < items.size(); i++) {
            cummProb += ((double) items.get(i)[0]);
            if (random <= cummProb) {
                return i;
            }
        }
        throw new RandomItemSelectionFailureException();
    }
    public void handleReward(CrateReward giveToPlayer){
        for(Object reward : giveToPlayer.getRewards()) {
            //System.out.println(reward);
            if (reward instanceof String) {
                Sponge.getCommandManager().process(new CrateCommandSource(), reward.toString().replace("%p", ourplr.getName()));
            } else {
                //System.out.println(giveToPlayer.getReward().treatAsSingle());

                ourplr.getInventory().offer(((ItemStack) reward).copy());
            }
        }
        boolean mult = false;
        LangData thisData = giveToPlayer.getLangData();
        if (!giveToPlayer.treatAsSingle() && giveToPlayer.getRewards().size() == 1 && giveToPlayer.getRewards().get(0) instanceof ItemStack) {
            if (((ItemStack) giveToPlayer.getRewards().get(0)).getQuantity() > 1) {
                   
                ourplr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                        thisData.formatter(thisData.rewardMessage, ((ItemStack) giveToPlayer.getRewards().get(0)).getQuantity() + "", ourplr, vc, giveToPlayer, null, null)
                ));
                if (giveToPlayer.shouldAnnounce()) {
                    Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(
                            thisData.formatter(thisData.rewardAnnounceMessage, ((ItemStack) giveToPlayer.getRewards().get(0)).getQuantity() + "", ourplr, vc, giveToPlayer, null, null)
                    ));
                }
                mult = true;
            }
        }
        if (!mult) {
            String[] vowels = {"a", "e", "i", "o", "u"};
            if (Arrays.asList(vowels).contains(giveToPlayer.getRewardName().substring(0, 1).toLowerCase())) {
                ourplr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                        thisData.formatter(thisData.rewardMessage, "an", ourplr, vc, giveToPlayer, null, null)
                ));
                if (giveToPlayer.shouldAnnounce()) {
                    Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(
                            thisData.formatter(thisData.rewardAnnounceMessage, "an", ourplr, vc, giveToPlayer, null, null)
                    ));
                }
            } else {
                ourplr.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
                        thisData.formatter(thisData.rewardMessage, "a", ourplr, vc, giveToPlayer, null, null)
                ));
                if (giveToPlayer.shouldAnnounce()) {
                    Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(
                            thisData.formatter(thisData.rewardAnnounceMessage, "a", ourplr, vc, giveToPlayer, null, null)
                    ));
                }
            }
        }

    }
}
