var password = "";
var portalNpc = 1063011;

function enter(pi) {
    if (pi.getQuestStatus(21720) == 1) {
    	pi.warp(910050200,0);
    } else {
    	pi.openNpc(portalNpc);
    	//pi.warp(910050300,0);
    }
}