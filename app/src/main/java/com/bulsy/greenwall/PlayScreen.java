package com.bulsy.greenwall;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents the main screen of play for the game.
 *
 * There are many ugly things here, partly because game programming lends itself
 * to questionable style.  ...and partly because this is my first venture into Android.
 *
 * Messy manual object recycling, violation of normal object oriented privacy
 * principles, and everything is extremely imperative/procedural, even for Java.
 * But apparently keeping android
 * happy is more important than data hiding and hands-free garbage collection.
 *
 * Created by ugliest on 12/29/14.
 */
public class PlayScreen extends Screen {
    static final float ZSTRETCH = 240; // lower -> more stretched on z axis
    static final float WALL_Z = 1000; // where is the wall, on the z axis?
    static final float WALL_Y_CENTER_FACTOR = 0.38f;  // factor giving y "center" of wall; this is used as infinity on z axis
    static final Rect wallbounds_at_wall_z = new Rect();  // wall bounds AT WALL Z (!!WaLLzeY!!)
    static final Rect wallbounds_at_screen_z = new Rect();  // wall bounds at screen z
    static final float WALLZFACT = 1.0f - (ZSTRETCH/(WALL_Z+ZSTRETCH));
    static final long ONESEC_NANOS = 1000000000L;
    static final int ACC_GRAVITY = 5000;
    static final int INIT_SELECTABLE_SPEED = 150;  // speed of selectable fruit at bottom of screen
    static final int SELECTABLE_Y_PLAY = 2;  // jiggles fruit up and down
    static final float INIT_SELECTABLE_Y_FACTOR = 0.9f;
    static final int MIN_ROUND_PASS_PCT = 50;  // pct splatted on wall that we require to advance level
    private Paint p;
    private Point effpt = new Point(); // reusable point for rendering, to avoid excessive obj creation.  brutally un-threadsafe, obviously, but we will use it in only the render thread.
    private List<Fruit> fruitsSelectable = Collections.synchronizedList(new LinkedList<Fruit>()); // fruits ready for user to throw
    private List<Fruit> fruitsFlying = Collections.synchronizedList(new LinkedList<Fruit>());  // fruits user has sent flying
    private List<Fruit> fruitsSplatted = new LinkedList<Fruit>(); // fruits that have splatted on wall
    private List<Fruit> fruitsRecycled = new LinkedList<Fruit>(); // fruit objects no longer in use
    private volatile Fruit selectedFruit = null;
    private int maxShownSelectableFruit;
    private float touchvx, touchvy;  // touchpoint's velocity
    private long touchtime = 0, frtime = 0;
    private Rect scaledDst = new Rect();
    private MainActivity act = null;
    private int selectable_speed;
    private final int GS_RUNNING = 1; // normal running state of game
    private final int GS_STARTROUND = 2; // flag to start round, transition state, only momentarily existing
    private final int GS_ENDROUNDSUMMARY = 3; // pausing, showing summary at end of round
    private final int GS_STARTGAME = 4; // transition state into game
    private final int GS_GAMEOVER = 5; // player lost
    private volatile int gamestate = GS_STARTGAME;
    private int fps=0; // rendering rate, frames per sec

    private int width = 0;
    private int height = 0;
    private int wallxcenter = 0;
    private int wallycenter = 0;
    private int inity = 0;
    private int minXbound = 0;
    private int maxXbound = 0;
    private int maxYbound = 0;

    // types of throwables, remaining quantities, values, etc
    private List<Seed> seedsQueued = new LinkedList<Seed>();
    private Bitmap wallbtm, pearbtm[], banbtm[], orangebtm[], milkbtm[], icbtm[], ketbtm[], nutbtm[];
    private Seed pearseed;
    private Seed orangeseed;
    private Seed banseed;
    private Seed milkseed;
    private Seed icseed;
    private Seed nutseed;
    private Seed ketseed;
    private int nWallSplats = 0;
    private int nTotFruit = 0;

    private int round;
    private int score;
    private int lives;
    private int hiscore;
    private static final String HISCORE_FILE = "gwhs.dat";
    private static final int START_NUMLIVES = 3;

    private List<Combo> combos = new ArrayList<Combo>();  // possible combos
    private List<Fruit> comboFruits = new ArrayList<Fruit>();  // fruits potentially involved in combo
    private Map<Combo, ComboHit> hitCombos = new HashMap<Combo, ComboHit>(); // combos tht have just been hit, and are being displayed to player
    private List<Seed> neededSeeds = new ArrayList<Seed>(); // the list of seeds required when computing whether a combo has been hit.
    private static final int COMBOHIT_SPEED = 300; // how fast the combohit message rises
    private static final long COMBOHIT_DISPLAYTIME = (int)(0.75 * ONESEC_NANOS); // time to display a combo hit

    public PlayScreen(MainActivity act) {
        p = new Paint();
        this.act = act;
        AssetManager assetManager = act.getAssets();
        try {
            // wall
            InputStream inputStream = assetManager.open("wall1_800x1104_16.png");
            wallbtm = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // pear
            pearbtm = new Bitmap[4];
            inputStream = assetManager.open("pear1.png");
            pearbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pear2.png");
            pearbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pear3.png");
            pearbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pearsplat1.png");
            pearbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // banana
            banbtm = new Bitmap[4];
            inputStream = assetManager.open("ban1.png");
            banbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("ban2.png");
            banbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("ban3.png");
            banbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pearsplat1.png");
            banbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // orange
            orangebtm = new Bitmap[4];
            inputStream = assetManager.open("orange1.png");
            orangebtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orange2.png");
            orangebtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orange3.png");
            orangebtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orangesplat.png");
            orangebtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // nutella
            nutbtm = new Bitmap[5];
            inputStream = assetManager.open("nut1.png");
            nutbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut2.png");
            nutbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut3.png");
            nutbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut4.png");
            nutbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nutsplat.png");
            nutbtm[4] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // ice cream
            icbtm = new Bitmap[5];
            inputStream = assetManager.open("icecream1.png");
            icbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("icecream2.png");
            icbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("icecream3.png");
            icbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("icecream4.png");
            icbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("icecreamsplat.png");
            icbtm[4] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // milk
            milkbtm = new Bitmap[5];
            inputStream = assetManager.open("milk1.png");
            milkbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("milk2.png");
            milkbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("milk3.png");
            milkbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("milk4.png");
            milkbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("milksplat.png");
            milkbtm[4] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // ketchup
            ketbtm = new Bitmap[2];
            inputStream = assetManager.open("ketch1.png");
            ketbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("ketchsplat.png");
            ketbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // initialize types of fruit (seeds), point values
            pearseed = new Seed(pearbtm, 10);
            orangeseed = new Seed(orangebtm, 15);
            banseed = new Seed(banbtm, 20);
            milkseed = new Seed(milkbtm, 25);
            icseed = new Seed(icbtm, 30);
            nutseed = new Seed(nutbtm, 40);
            ketseed = new Seed(ketbtm, 0);

            // init combos
            ArrayList<Seed> sl = new ArrayList<Seed>();
            sl.add(pearseed);
            sl.add(orangeseed);
            sl.add(banseed);
            combos.add(new Combo(sl, "Fruit Salad!", 80));
            sl = new ArrayList<Seed>();
            sl.add(milkseed);
            sl.add(nutseed);
            combos.add(new Combo(sl, "chOcolaTe MiLK!", 80));
            sl = new ArrayList<Seed>();
            sl.add(banseed);
            sl.add(icseed);
            sl.add(nutseed);
            combos.add(new Combo(sl, "bAnaNA sPLiT!!", 100));

            p.setTypeface(act.getGameFont());
            round = 1;
            initRound();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * initialize and start a game
     */
    void initGame() {
        score = 0;
        round = 1;
        lives = START_NUMLIVES;
        hiscore = 0;
        try {
            BufferedReader f = new BufferedReader(new FileReader(act.getFilesDir() + HISCORE_FILE));
            hiscore = Integer.parseInt(f.readLine());;
            f.close();
        } catch (Exception e) {
            Log.d(MainActivity.LOG_ID, "ReadHiScore", e);
        }
        gamestate = GS_STARTROUND;
    }

    /**
     * add the specified number of the specified fruit seed to the specified list.
     * seeds are added at random locations in the list.
     * @param list
     * @param n
     */
    private void addFruitSeed(List<Seed> list, Seed s, int n) {
        for (int i = 0; i < n; i++) {
            int loc = (int) (Math.random() * list.size());
            list.add(loc, s);
        }
    }

    /**
     * init game for current round
     */
    private void initRound() {
        selectable_speed = INIT_SELECTABLE_SPEED + (round*10);

        if (round < 2)
            maxShownSelectableFruit = 1;
        else if (round < 8)
            maxShownSelectableFruit = 2;
        else if (round < 14)
            maxShownSelectableFruit = 3;
        else if (round < 20)
            maxShownSelectableFruit = 4;
        else
            maxShownSelectableFruit = 5;

        fruitsRecycled.addAll(fruitsSplatted);
        fruitsSplatted.clear();

        // set up fruits to throw
        seedsQueued.clear();
        addFruitSeed(seedsQueued, pearseed, 5+round/2);
        if (round > 1)
            addFruitSeed(seedsQueued, orangeseed, 4+round/2);
        if (round > 2)
            addFruitSeed(seedsQueued, banseed, round/2);
        if (round > 4)
            addFruitSeed(seedsQueued, milkseed, 1+round/3);
        if (round > 7)
            addFruitSeed(seedsQueued, icseed, round/3);
        if (round > 9)
            addFruitSeed(seedsQueued, nutseed, round/3);

        if (round > 5)
            addFruitSeed(seedsQueued, ketseed, round/2);

        nWallSplats = 0;
        nTotFruit = seedsQueued.size();

        gamestate = GS_RUNNING;
    }

    /**
     * player lost a life
     */
    private void loseLife() {
        lives--;
        if (lives == 0) {
            gamestate = GS_GAMEOVER;
                try {
                    BufferedWriter f = new BufferedWriter(new FileWriter(act.getFilesDir() + HISCORE_FILE));
                    f.write(Integer.toString(hiscore));
                    f.close();
                } catch (Exception e) { // if we can't write the hi score file...oh well.
                    Log.d(MainActivity.LOG_ID, "WriteHiScore", e);
                }
        }
    }

    /**
     * Represents a combination of splats
     */
    private class Combo {
        List<Seed> seeds;
        String name;
        int points;

        public Combo (List<Seed> seeds, String name, int points) {
            this.seeds = seeds;
            this.name = name;
            this.points = points;
        }
    }

    private class ComboHit {
        float x=0;
        float y=0;
        long hitTime = 0;
        int alpha = 0; // translucence
    }

    /**
     * return the effective screen x,y point rendered from the inpassed
     * (x, y, z) point.
     * @param x
     * @param y
     * @param z
     * @return
     */
    private Point renderFromZ(float x, float y, float z, float xc, float yc) {
        float zfact = 1.0f - (ZSTRETCH/(z+ZSTRETCH));
        int effx = (int)(x + (zfact * (xc-x)));
        int effy = (int)(y + (zfact * (yc-y)));
        effpt.set(effx, effy);
        return effpt;
    }


    /**
     * Draw the inpassed fruit, using the inpassed bitmap, rendering the
     * fruit's x/y/z position to the
     * actual x/y screen coords.
     * @param c
     * @param f
     * @param btm
     * @param xc  the x center. to which the "z axis" points
     * @param yc  the y center, to which the "z axis" points
     */
    private void drawFruit3Dcoords(Canvas c, Fruit f, Bitmap btm, float xc, float yc) {
        // render effective x and y, from x y z
        // DRY says this should call renderFromZ, but that creates even more
        // ugliness than this duplication of code, and this code isn't going to change.
        float zfact = 1.0f - (ZSTRETCH/(f.z+ZSTRETCH));
        int effx = (int)(f.x + (zfact * (xc-f.x)));
        int effy = (int)(f.y + (zfact * (yc-f.y)));
        int effhalfw = (int)(f.seed.halfWidth * (1.0f - zfact));
        int effhalfh = (int)(f.seed.halfHeight * (1.0f - zfact));
        scaledDst.set(effx - effhalfw, effy - effhalfh, effx + effhalfw, effy + effhalfh);
        c.drawBitmap(btm, null, scaledDst, p);
    }

    @Override
    public void update(View v) {
        long newtime = System.nanoTime();
        float elapsedsecs = (float)(newtime - frtime) / ONESEC_NANOS;
        frtime = newtime;
        fps = (int)(1/elapsedsecs);

        // update combo hits
        Iterator<Combo> hcit = hitCombos.keySet().iterator();
        while (hcit.hasNext()) {
            Combo combo = hcit.next();
            ComboHit ch = hitCombos.get(combo);
            ch.y -= COMBOHIT_SPEED * elapsedsecs;
            float chtime = frtime - ch.hitTime;
            ch.alpha = (int)(255 * (1.0f - chtime/COMBOHIT_DISPLAYTIME));
            if (frtime - ch.hitTime > COMBOHIT_DISPLAYTIME)
                hcit.remove();
        }

        if (gamestate == GS_STARTGAME) {
            initGame();
            return;
        }
        else if (gamestate == GS_STARTROUND) {
            // this goofy construction is to make sure we initialize the round from
            // the update/draw thread, not from the UI thread.
            initRound();
            return;
        }
        else if (gamestate == GS_ENDROUNDSUMMARY || gamestate == GS_GAMEOVER)
            return;  // we're in pause, there's nothing else to update

        if (width == 0) {
            // set variables that rely on screen size
            width = v.getWidth();
            height = v.getHeight();
            wallxcenter = width / 2;
            wallycenter = (int) (height * WALL_Y_CENTER_FACTOR);

            inity = (int) (INIT_SELECTABLE_Y_FACTOR * height); // initial fruit placement, also bottom of wall.

            // attempt to compute wall bounds at wall z  from screen size.  constants are pure
            // magic, found thru trial and error iterations.
            // if the background picture changes, they will need to be recalibrated.
            wallbounds_at_wall_z.set((int) (-1.5 * width), (int) (-height * .94), (int) (2.43 * width), inity);  // wall bounds AT WALL Z (!!WaLLzeY!!)

            // magic trial and error world-bounds contants, based on screen image size
            minXbound = 8 * -width;
            maxXbound = 8 * width;
            maxYbound = 5 * height;

            // compute wall bounds at screen z, used for clipping.
            int effl = (int) (wallbounds_at_wall_z.left + (WALLZFACT * (wallxcenter - wallbounds_at_wall_z.left)));
            int efft = (int) (wallbounds_at_wall_z.top + (WALLZFACT * (wallycenter - wallbounds_at_wall_z.top)));
            int effr = (int) (wallbounds_at_wall_z.right + (WALLZFACT * (wallxcenter - wallbounds_at_wall_z.right)));
            int effb = (int) (wallbounds_at_wall_z.bottom + (WALLZFACT * (wallycenter - wallbounds_at_wall_z.bottom)));
            wallbounds_at_screen_z.set(effl, efft, effr, effb);
        }

        if (fruitsSelectable.size() < maxShownSelectableFruit
                && seedsQueued.size() > 0
                && Math.random() > .95) { // "every now and then" make a fruit available
            Fruit newf = null;
            if (fruitsRecycled.size() > 0) { // recycle a fruit if we can
                newf = fruitsRecycled.get(0);
                fruitsRecycled.remove(0);
            }
            else { // create if needed
                newf = new Fruit();
            }
            int initx = 0;
            int speed = selectable_speed;
            if (Math.random() > .5) {
                initx = width;
                speed = -speed;
            }

            // choose fruit
            Seed s = seedsQueued.get(0);
            seedsQueued.remove(0);
            newf.init(s, initx, inity, 0, speed);
            fruitsSelectable.add(newf);
        }
        else if (fruitsSelectable.size() == 0
                && fruitsFlying.size() == 0
                && seedsQueued.size() == 0) {
            // round is over
            gamestate = GS_ENDROUNDSUMMARY;
            if (nWallSplats*100/nTotFruit >= MIN_ROUND_PASS_PCT)
                round++;
            else
                loseLife();
        }

        // update fruit positions
        synchronized (fruitsFlying) {
            Iterator<Fruit> fit = fruitsFlying.iterator();
            while (fit.hasNext()) {
                Fruit f = fit.next();
                f.x += f.vx * elapsedsecs;
                f.y += f.vy * elapsedsecs;
                f.z += f.vz * elapsedsecs;
                f.vy += ACC_GRAVITY * elapsedsecs;
                if (f.z >= WALL_Z && wallbounds_at_wall_z.contains((int)f.x, (int)f.y)) {
                    // fruit has hit wall
                    fit.remove();
                    fruitsSplatted.add(f);
                    nWallSplats++;
                    score += f.seed.points;
                    //act.getSound splat

                    // check combo
//                    synchronized (fruitsSplatted) {
                        for (Combo c : combos) {
                            neededSeeds.clear();
                            neededSeeds.addAll(c.seeds);
                            neededSeeds.remove(f.seed);
                            comboFruits.clear();
                            comboFruits.add(f);
                            for (Fruit spf : fruitsSplatted) {
                                if (neededSeeds.contains(spf.seed)) {
                                    if (spf.getBounds().intersect(f.getBounds())) {
                                        neededSeeds.remove(spf.seed);
                                        comboFruits.add(spf);
                                    }
                                    if (neededSeeds.size() == 0)
                                        break;
                                }
                            }
                            if (neededSeeds.size() == 0) {
                                // combo is hit
                                score += c.points;
                                for (Fruit spf : comboFruits) {
                                    fruitsSplatted.remove(spf);
                                }

                                // combo sound play
                                effpt = renderFromZ(f.x, f.y, f.z, wallxcenter, wallycenter);
                                ComboHit ch = new ComboHit();

                                // display combo hit message "somewhere next to" combo hit
                                ch.x = effpt.x + (float)Math.random() * 100 -50;
                                ch.y = effpt.y + (float)Math.random() * 100 -80;

                                // ensure combo display is fully onscreen
                                p.getTextBounds(c.name, 0, c.name.length(), scaledDst);
                                if (ch.x < 0)
                                    ch.x = 0;
                                else if (ch.x > width - scaledDst.width())
                                    ch.x = width - scaledDst.width();

                                ch.hitTime = System.nanoTime();
                                hitCombos.put(c, ch);
                            }
                        }
//                    }

                }
                else if (f.y > inity
                        && f.y < inity + f.vy * elapsedsecs
                        && f.z > WALL_Z/2) {
                    // fruit has hit ground near wall
                    fit.remove();
                    fruitsSplatted.add(f);
                }
                else if (f.z > WALL_Z
                        // here we goofily force java to call render function when we need it
                        && (effpt = renderFromZ(f.x, f.y, f.z, wallxcenter, wallycenter))!=null
                        && wallbounds_at_screen_z.contains(effpt.x, effpt.y)
                        ) {
                    // wild pitch, behind wall
                    fit.remove();
                    fruitsRecycled.add(f);
                }
                else if (f.y > maxYbound
                        || f.x >= maxXbound
                        || f.x <= minXbound) {
                    // wild pitch, out of bounds
                    fit.remove();
                    fruitsRecycled.add(f);
                }
            }
        }
        synchronized (fruitsSelectable) {
            Iterator<Fruit> fit = fruitsSelectable.iterator();
            while (fit.hasNext()) {
                Fruit f = fit.next();
                if (f != selectedFruit) {
                    f.x += f.vx * elapsedsecs;
                    f.y += (inity - f.y) / 3;
                    if (f.y - inity < 0.9)
                      f.y += SELECTABLE_Y_PLAY;
                }
                if (f.x < -f.seed.halfWidth || f.x > width + f.seed.halfWidth) {
                    // we floated off screen
                    fit.remove();
                    fruitsRecycled.add(f);
                }
            }
        }
    }

    /**
     * draw the screen.
     * @param c
     * @param v
     */
    @Override
    public void draw(Canvas c, View v) {
        try {
            // actually draw the screen
            scaledDst.set(0, 0, width, height);
            c.drawBitmap(wallbtm, null, scaledDst, p);

            // draw wall's bounds, for debugging
            //p.setColor(Color.RED);
            //c.drawRect(wallbounds_at_screen_z, p);

            // draw fruits
            for (Fruit f : fruitsSplatted){
                drawFruit3Dcoords(c, f, f.getSplatBitmap(), wallxcenter, wallycenter);
            }
            synchronized (fruitsFlying) {
                for (Fruit f : fruitsFlying) {
                    drawFruit3Dcoords(c, f, f.getBitmap(System.nanoTime()), wallxcenter, wallycenter);
                }
            }
            synchronized (fruitsSelectable) {
                for (Fruit f : fruitsSelectable) {
                    // selectable fruit is on z=0, so we can just display normally:
                    c.drawBitmap(f.seed.btm[0], f.x - f.seed.halfWidth, f.y - f.seed.halfHeight, p);
                }
            }

            // draw combo hits
            for (Combo combo : hitCombos.keySet()) {
                ComboHit ch = hitCombos.get(combo);
                p.setColor(Color.YELLOW);
                p.setARGB(ch.alpha, 200+(int)(Math.random() * 50),  200+(int)(Math.random() * 50),  (int)(Math.random() * 50));
//                p.setColor((int)(Math.random() * 65536));
                p.setTypeface(act.getGameFont());
                p.setTextSize(45);
                c.drawText(combo.name, ch.x, ch.y, p);
            }

//            c.drawText("fps: "+fps
//                        +"x:"+touchx+" y:"+touchy+" tvx:"+(int)touchvx+"\ttvy:"+(int)touchvy+
//                    "\tflying:" + fruitsFlying.size()
//                            + " ffz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).z : -1)
//                            + " ffvz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).vz : -1),
//                    , 0, 200, p);
            p.setColor(Color.WHITE);
            p.setTextSize(45);
            p.setTypeface(act.getGameFont());
            p.setFakeBoldText(true);
            c.drawText("ROUND " + round, width - 300, 60, p);
            c.drawText("LIVES "+lives, width - 300, 120, p);
            c.drawText("SCORE: "+score, 10, 60, p);
            if (score > hiscore)
                hiscore = score;
            c.drawText("HIGH: "+hiscore, 10, 120, p);

            if (gamestate == GS_ENDROUNDSUMMARY || gamestate == GS_GAMEOVER) {
                // round ended, display stats
                int splatPct = (int)(nWallSplats*100/nTotFruit);

                c.drawText(splatPct+"% sPLAttaGe!", width/4, height/3, p);
                if (splatPct < 50)
                    c.drawText("Ooops...try again.", width/4, (int)(height/2.5), p);
                else if (splatPct < 60)
                    c.drawText("Not too bad.", width/4, (int)(height/2.5), p);
                else if (splatPct < 70)
                    c.drawText("Nice!", width*3/4, (int)(height/2.5), p);
                else if (splatPct < 90) {
                    c.drawText("sPAzTIc!", width / 3, (int) (height / 2.5), p);
                    c.drawText("CruDe!!", width / 2, (int) (height / 2.2), p);
                }
                else if (round > 8) {
                    c.drawText("Dude, really?!", width / 4, (int) (height / 2.5), p);
                    c.drawText("That was awesome.", width / 3, (int) (height / 2.2), p);
                }
                else {
                    c.drawText("eEEeEeeEh!! sPAzTIc!", width / 4, (int) (height / 2.5), p);
                }
                if (gamestate != GS_GAMEOVER)
                    c.drawText("Touch to continue", width/4, height*2/3, p);
            }
            if (gamestate == GS_GAMEOVER) {
                c.drawText("Game OveR!", width/3, height/2, p);
                c.drawText("Touch to end game", width/4, height*2/3, p);
            }

        } catch (Exception e) {
            Log.e(MainActivity.LOG_ID, "draw", e);
            e.printStackTrace();
        }
    }

    VelocityTracker mVelocityTracker = null;
    @Override
    public boolean onTouch(MotionEvent e) {
        long newtime = System.nanoTime();
        float elapsedsecs = (float)(newtime - touchtime) / ONESEC_NANOS;
        touchtime = newtime;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gamestate == GS_ENDROUNDSUMMARY) {
                    gamestate = GS_STARTROUND; // prep and start round
                    return false; // no followup msgs
                }
                else if (gamestate == GS_GAMEOVER) {
                    act.leaveGame(); // user touched after gameover -> back to entry screen
                    return false;  // no followup msgs
                }
                else {
                    synchronized (fruitsSelectable) {
                        for (Fruit f : fruitsSelectable) {
                            if (f.hasCollision(e.getX(), e.getY()))
                                selectedFruit = f;
                        }
                    }
                    if (mVelocityTracker == null) {
                        // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        // Reset the velocity tracker back to its initial state.
                        mVelocityTracker.clear();
                    }
                    // Add a user's movement to the tracker.
                    mVelocityTracker.addMovement(e);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (selectedFruit != null) {
                    selectedFruit.x = e.getX();
                    selectedFruit.y = e.getY();
                }
                mVelocityTracker.addMovement(e);
                // When you want to determine the velocity, call
                // computeCurrentVelocity(). Then call getXVelocity()
                // and getYVelocity() to retrieve the velocity for each pointer ID.
                mVelocityTracker.computeCurrentVelocity(1000);
                int pointerId = e.getPointerId(e.getActionIndex());
                // Log velocity of pixels per second
                // Best practice to use VelocityTrackerCompat where possible.
//                Log.d(MainActivity.LOG_ID, "X velocity: me:" + touchvx+" VT:"+
//                        VelocityTrackerCompat.getXVelocity(mVelocityTracker,
//                                pointerId));
//                Log.d(MainActivity.LOG_ID, "Y velocity: me:" + touchvy+" VT:"+
//                        VelocityTrackerCompat.getYVelocity(mVelocityTracker,
//                                pointerId));
                touchvx = VelocityTrackerCompat.getXVelocity(mVelocityTracker,
                        pointerId);
                touchvy = VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                        pointerId);
                break;

            case MotionEvent.ACTION_UP:
                float tvx = touchvx;
                float tvy = touchvy;
                touchvx = 0;
                touchvy = 0;
                if (selectedFruit != null) {
                    Fruit f = selectedFruit;
                    selectedFruit = null;
                    if (-tvy > 0) {
                        // there is upward motion at release-- user threw fruit
                        f.throwFruit(tvx, tvy);
                        synchronized (fruitsFlying) {
                            fruitsFlying.add(f);
                            fruitsSelectable.remove(f);
                        }
                    }
                }
                mVelocityTracker.recycle();
                break;
        }

        return true;
    }

    /**
     * A Seed is more or less a template for a Fruit.
     */
    private class Seed {
        int points; // points this type of Fruit is worth, if it hits the wall.
        Bitmap btm[]; // bitmap for animating this type of throwable
        float width=0; // width onscreen
        float height=0;  // height onscreen
        float halfWidth = 0;  // convenience
        float halfHeight = 0;
        final float HALF_DIVISOR = 1.9f;  // we fudge "half" a little, results are more comfortable.

        public Seed(Bitmap bitmaps[], int points) {
            this.btm = bitmaps;
            this.width = bitmaps[0].getWidth();
            this.height = bitmaps[0].getHeight();
            this.halfWidth = width/HALF_DIVISOR;
            this.halfHeight = height/HALF_DIVISOR;
            this.points = points;
        }
    }

    /**
     * "Throwable" would be a better name here, but as that's taken, "Fruit" it is.
     * A fruit is something that the player is presented with, usually to throw at the wall.
     */
    private class Fruit {
        final int APS = 4; // number of animation cycles per second

        // position
        int initx=0;
        int inity=0;
        int initz=0;
        float x=0;
        float y=0;
        float z=0;

        // speed
        float vx=0;
        float vy=0;
        float vz=0;

        long thrownTime = 0; // when this fruit was thrown; 0 = not yet
        Seed seed=null; // the core information about this throwable fruit

        Rect bounds = new Rect();

        /**
         * initialize a fruit, at initial location.
         * @param initx
         * @param inity
         * @param initz
         */
        public void init (Seed s, int initx, int inity, int initz, int initxspeed) {
            this.initx = initx;
            this.inity = inity;
            this.initz = initz;
            this.vx = initxspeed;
            this.x = initx;
            this.y = inity;
            this.z = initz;
            this.seed = s;
        }

        /**
         * looks for a collision with an inpossed point -=- z axis ignored..
         * @param collx
         * @param colly
         */
        public boolean hasCollision(float collx, float colly) {
            return getBounds().contains((int) collx, (int) colly);
        }

        public Rect getBounds() {
            bounds.set((int)(this.x - seed.halfWidth), (int)(this.y-seed.halfHeight),
                    (int)(this.x+seed.halfWidth), (int)(this.y+seed.halfHeight));
            return bounds;
        }

        public void throwFruit(float tvx, float tvy) {
            thrownTime = System.nanoTime(); // used by animation
            vx = tvx;

            // to simulate throwing into the screen,
            // y velocity ("up") is faster as we release higher on the touchscreen,
            // and z velocity ("into the screen") is faster if we release lower.
            // yzfact represents how much of the user's actual touchpoint y-velocity
            // is treated as z-velocity.
            float yzfact = y / inity;
            vy = tvy * (1 - yzfact);
            vz = (-tvy * yzfact)/2;
        }

        public Bitmap getBitmap(long t) {
            // cycle through the images, over half a sec
            int nframes = seed.btm.length - 1;
            int idx =(int)((t - thrownTime) / (ONESEC_NANOS / (APS * nframes))) % nframes;
            return seed.btm[idx];
        }

        public Bitmap getSplatBitmap() {
            return seed.btm[seed.btm.length - 1];
        }
    }
}
