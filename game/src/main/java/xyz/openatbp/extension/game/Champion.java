package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.util.concurrent.TimeUnit;

public class Champion {

    @Deprecated
    public static void attackChampion(ATBPExtension parentExt, User player, int damage){ //Used for melee attacks TODO: Move over to one function as this likely does not work with multiplayer
        ExtensionCommands.damageActor(parentExt,player, String.valueOf(player.getId()),damage);
        ISFSObject stats = player.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(player.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,player,updateData);
        }else{
            handleDeath(parentExt,player); //TODO: Implement player death
        }

    }

    public static void attackChampion(ATBPExtension parentExt, User u, User target, int damage){ //Used for ranged attacks
        ExtensionCommands.damageActor(parentExt,u, String.valueOf(target.getId()),damage);
        ISFSObject stats = target.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(target.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }else{
            handleDeath(parentExt,u);
        }
    }


    //TODO: Implement player death
    private static void handleDeath(ATBPExtension parentExt, User player){

    }

    public static void rangedAttackChampion(ATBPExtension parentExt, Room room, String attacker, String target, int damage){
        //Handles the damage after the projectile animation is finished
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,target),500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){ //Skins don't have their own projectile effect so we have to pull from the base
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            //TODO: Make more accurate emit & hit locations
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,target,"Bip001","Bip001",(float)0.5);
        }
    }

    public static void attackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles attacking the tower
        boolean towerDown = tower.damage(damage); // Returns true if tower is destroyed
        boolean notify = System.currentTimeMillis()-tower.getLastHit() >= 1000*5; //Returns true if we should notify players of a tower being hit
        for(User u : room.getUserList()){
            if(notify) ExtensionCommands.towerAttacked(parentExt,u, tower.getTowerNum());
            if(towerDown){ // Tower is dead
                handleTowerDeath(parentExt,u,tower,attacker);
            }
            float maxHealth = tower.getMaxHealth();
            float currentHealth = tower.getHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", tower.getId());
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        if(notify) tower.triggerNotification(); //Resets tower notification time
    }

    private static void handleTowerDeath(ATBPExtension parentExt, User u, Tower tower, String attacker){ //Handles tower death
        ExtensionCommands.towerDown(parentExt,u, tower.getTowerNum());
        ExtensionCommands.knockOutActor(parentExt,u,tower.getId(),attacker,100);
        ExtensionCommands.destroyActor(parentExt,u,tower.getId());
        String actorId = "tower2a";
        if(tower.getTowerNum() == 0 || tower.getTowerNum() == 3 ) actorId = "tower1a";
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),actorId,tower.getId()+"_destroyed",1000*60*15,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),"tower_destroyed_explosion",tower.getId()+"_destroyed_explosion",1000,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        Room room = u.getLastJoinedRoom();
        ISFSObject scoreObj = room.getVariable("score").getSFSObjectValue();
        int teamA = scoreObj.getInt("purple");
        int teamB = scoreObj.getInt("blue");
        if(tower.getTeam() == 0) teamB+=50;
        else teamA+=50;
        scoreObj.putInt("purple",teamA);
        scoreObj.putInt("blue",teamB);
        ExtensionCommands.updateScores(parentExt,u,teamA,teamB);
    }

    public static void rangedAttackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles ranged attacks against tower
        //Schedules damage after projectile hits target
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,tower),500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,tower.getId(),"Bip001","Bip001",(float)0.5);
        }
    }
}

class RangedAttack implements Runnable{ //Handles damage from ranged attacks
    ATBPExtension parentExt;
    Room room;
    int damage;
    String target;
    Tower tower;
    String attacker;

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, String target){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = target;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Tower tower){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = tower.getId();
        this.tower = tower;
        this.attacker = attacker;
    }
    @Override
    public void run() {
        if(target.contains("tower") && tower != null){
            Champion.attackTower(parentExt,room,attacker,tower,damage);
        }else{
            User p = room.getUserById(Integer.parseInt(target));
            for(User u : room.getUserList()){
                Champion.attackChampion(parentExt,u,p,damage);
            }
        }
    }
}

class DestroyActor implements Runnable{ //TODO: Used for destroying actors, when it becomes necessary

    String id;
    ATBPExtension parentExt;
    Room room;

    DestroyActor(ATBPExtension parentExt, Room room, String id){
        this.id = id;
        this.parentExt = parentExt;
        this.room = room;
    }
    @Override
    public void run() {
        for(User u : room.getUserList()){
            ExtensionCommands.destroyActor(parentExt,u,id);
        }
    }
}