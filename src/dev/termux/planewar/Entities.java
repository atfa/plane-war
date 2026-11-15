package dev.termux.planewar;

/** Plain data holders - the game keeps flat lists of these and iterates backwards. */
class Bullet {
    float x, y, vx, vy, r, dmg;
    float life;
    int color;
    boolean enemy;
}

class Enemy {
    // 0 grunt, 1 darter, 2 gunship, 3 elite, 4 heavy
    int type;
    float x, y, vx, vy, r;
    float hp, maxhp;
    float t;              // age (s)
    float fire;           // shot countdown
    float phase;          // per enemy phase for sine patterns
    int score;
    int color;
    int flash;            // white flash frames after a hit
    int hover;            // 0 = descending, 1 = in attack pattern
}

class PowerUp {
    int kind;             // P weapon, S shield, H heal, B bomb, X score, F rapid
    float x, y, vx, vy, t, r;
}

class Particle {
    int kind;             // 0 spark, 1 smoke, 2 ring, 3 debris, 4 glow
    float x, y, vx, vy;
    float life, max, size, rot, vr;
    int color;
}

class FloatText {
    float x, y, vy, t, life, size;
    int color;
    String s;
}

class Star {
    float x, y, z;
}
