package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Lich extends UserActor{

    private Skully skully;
    private long lastSkullySpawn;
    private boolean qActivated = false;
    private List<Point2D> slimePath = null;
    private HashMap<String, Long> slimedEnemies = null;

    public Lich(User u, ATBPExtension parentExt){
        super(u,parentExt);
        lastSkullySpawn = 0;
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        if (skully == null && System.currentTimeMillis() - lastSkullySpawn > 40000) {
            this.spawnSkully();
        }
        switch (ability) {
            case 1: //Q
                double statIncrease = this.speed * 0.25;
                this.handleEffect("speed", statIncrease, 6000, "lich_trail");
                qActivated = true;
                slimePath = new ArrayList<>();
                slimedEnemies = new HashMap<>();
                ExtensionCommands.createActorFX(parentExt, room, id, "lichking_deathmist", 6000, "lich_trail", true, "", true, false, team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new TrailHandler(), 6000, TimeUnit.MILLISECONDS);
                break;
            case 2: //W
                break;
            case 3: //E
                break;
            case 4: //Passive
                break;
        }
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.skully != null) skully.update(msRan);
        if(this.qActivated){
            this.slimePath.add(this.location);
            for(Point2D slime : this.slimePath){
                for(Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()){
                    if(a.getTeam() != this.team && a.getLocation().distance(slime) < 0.5){
                        JsonNode attackData = this.parentExt.getAttackData(this.avatar,"spell1");
                        if(slimedEnemies.containsKey(a.getId())){
                            if(System.currentTimeMillis() - slimedEnemies.get(a.getId()) >= 1000){
                                System.out.println(a.getId() + " getting slimed!");
                                a.damaged(this,20,attackData);
                                a.handleEffect("speed",a.getPlayerStat("speed")*-0.3,1500,"lich_slow");
                                slimedEnemies.put(a.getId(),System.currentTimeMillis());
                                break;
                            }
                        }else{
                            System.out.println(a.getId() + " getting slimed!");
                            a.damaged(this,20,attackData);
                            a.handleEffect("speed",a.getPlayerStat("speed")*-0.3,1500,"lich_slow");
                            slimedEnemies.put(a.getId(),System.currentTimeMillis());
                            break;
                        }
                    }
                }
            }
            if(this.slimePath.size() > 150) this.slimePath.remove(this.slimePath.size()-1);
        }
    }

    @Override
    public void setPath(Point2D start, Point2D end){
        super.setPath(start,end);
        if(skully != null) skully.lichUpdated = true;
    }

    private void spawnSkully(){
        skully = new Skully();
        lastSkullySpawn = System.currentTimeMillis();
    }

    private class TrailHandler implements Runnable {
        @Override
        public void run() {
            qActivated = false;
            slimePath = null;
            slimedEnemies = null;
        }
    }

    private class Skully extends Actor {

        private Point2D destination;
        private Point2D originalLocation;
        private float timeTraveled = 0f;
        private Actor target;
        private boolean lichUpdated = true;

        Skully(){
            this.room = Lich.this.room;
            this.parentExt = Lich.this.parentExt;
            this.currentHealth = 500;
            this.maxHealth = 500;
            this.location = Lich.this.location;
            this.avatar = "skully";
            this.id = "skully_"+Lich.this.id;
            this.team = Lich.this.team;
            this.destination = Lich.this.destination;
            this.originalLocation = this.location;
            this.speed = 2.95f;
            ExtensionCommands.createActor(parentExt,room,this.id,this.avatar,this.location,0f,this.team);
        }

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return false;
        }

        @Override
        public void attack(Actor a) {

        }

        @Override
        public void die(Actor a) {

        }

        @Override
        public void update(int msRan) {
            this.timeTraveled = 0.1f;
            this.destination = Lich.this.location;
            if(getRelativePoint().distance(this.destination) > 3) this.location = getRelativePoint();
            if(this.location.distance(this.destination) > 3){
                ExtensionCommands.moveActor(parentExt,room,this.id,this.location,this.destination,(float)this.speed,true);
            }
        }

        public Point2D getRelativePoint(){ //Gets player's current location based on time
            Point2D rPoint = new Point2D.Float();
            float x2 = (float) destination.getX();
            float y2 = (float) destination.getY();
            float x1 = (float) location.getX();
            float y1 = (float) location.getY();
            Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
            double dist = movementLine.getP1().distance(movementLine.getP2());
            double time = dist/1.75f;
            double currentTime = this.timeTraveled;
            if(currentTime>time) currentTime=time;
            double currentDist = 1.75f*currentTime;
            float x = (float)(x1+(currentDist/dist)*(x2-x1));
            float y = (float)(y1+(currentDist/dist)*(y2-y1));
            rPoint.setLocation(x,y);
            if(dist != 0) return rPoint;
            else return location;
        }
    }
}
