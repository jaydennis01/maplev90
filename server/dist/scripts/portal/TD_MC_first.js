function enter(pi) {
    var level = pi.getPlayerStat("LVL");
    if (level >= 30 && level <= 40) {
	pi.playPortalSE();
	pi.warp(106020000, 1);
    } else {
	pi.playerMessage(5, "Unable to approach due to force off the ground.");
    }
}