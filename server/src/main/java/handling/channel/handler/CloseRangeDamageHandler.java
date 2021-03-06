package handling.channel.handler;

import java.lang.ref.WeakReference;

import client.ISkill;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleClient;
import client.SkillFactory;
import client.anticheat.CheatingOffense;
import constants.GameConstants;
import handling.AbstractMaplePacketHandler;
import server.MapleStatEffect;
import server.Timer.CloneTimer;
import server.events.MapleSnowball.MapleSnowballs;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

public class CloseRangeDamageHandler extends AbstractMaplePacketHandler {

	private final boolean energy;

	public CloseRangeDamageHandler(boolean energy) {
		this.energy = energy;
	}

	private static boolean isFinisher(final int skillid) {
		switch (skillid) {
		case 1111003:
		case 1111004:
		case 1111005:
		case 1111006:
		case 11111002:
		case 11111003:
			return true;
		}
		return false;
	}

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, final MapleClient c) {
		final MapleCharacter chr = c.getPlayer();
		if (chr == null || (energy && chr.getBuffedValue(MapleBuffStat.ENERGY_CHARGE) == null
				&& chr.getBuffedValue(MapleBuffStat.BODY_PRESSURE) == null && !GameConstants.isKOC(chr.getJob()))) {
			return;
		}
		if (!chr.isAlive() || chr.getMap() == null) {
			chr.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
			return;
		}
		final AttackInfo attack = DamageParse.Modify_AttackCrit(DamageParse.parseDmgM(slea), chr, 1);
		if (!chr.isSkillBelongToJob(attack.skill)) {
			chr.dropMessage(5, "This skill cannot be used with the current job");
			c.getSession().write(MaplePacketCreator.enableActions());
			return;
		}
		if (attack.portals != chr.getPortalCount(false)) { // Portal count
															// didn't match.
															// Ignore
			c.getSession().write(MaplePacketCreator.enableActions());
			return;
		}
		final boolean mirror = chr.getBuffedValue(MapleBuffStat.MIRROR_IMAGE) != null;
		double maxdamage = chr.getStat().getCurrentMaxBaseDamage();
		int attackCount = (chr.getJob() >= 430 && chr.getJob() <= 434 ? 2 : 1), skillLevel = 0;
		MapleStatEffect effect = null;
		ISkill skill = null;

		if (attack.skill != 0) {
			skill = SkillFactory.getSkill(GameConstants.getLinkedAranSkill(attack.skill));
			skillLevel = chr.getSkillLevel(skill);
			effect = attack.getAttackEffect(chr, skillLevel, skill);
			if (effect == null) {
				return;
			}
			maxdamage *= effect.getDamage() / 100.0;
			attackCount = effect.getAttackCount();

			if (effect.getCooldown() > 0 && !chr.isGM()) {
				if (chr.skillisCooling(attack.skill)) {
					c.getSession().write(MaplePacketCreator.enableActions());
					return;
				}
				c.getSession().write(MaplePacketCreator.skillCooldown(attack.skill, effect.getCooldown()));
				chr.addCooldown(attack.skill, System.currentTimeMillis(), effect.getCooldown() * 1000);
			}
		}

		attackCount *= (mirror ? 2 : 1);
		if (!energy) {
			if ((chr.getMapId() == 109060000 || chr.getMapId() == 109060002 || chr.getMapId() == 109060004)
					&& attack.skill == 0) {
				MapleSnowballs.hitSnowball(chr);
			}
			// handle combo orbconsume
			int numFinisherOrbs = 0;
			final Integer comboBuff = chr.getBuffedValue(MapleBuffStat.COMBO);

			if (isFinisher(attack.skill)) { // finisher
				if (comboBuff != null) {
					numFinisherOrbs = comboBuff.intValue() - 1;
				}
				chr.handleOrbconsume();

			} else if (attack.targets > 0 && comboBuff != null) {
				// handle combo orbgain
				switch (chr.getJob()) {
				case 111:
				case 112:
				case 1110:
				case 1111:
					if (attack.skill != 1111008) { // shout should not give orbs
						chr.handleOrbgain();
					}
					break;
				}
			}
			switch (chr.getJob()) {
			case 511:
			case 512: {
				chr.handleEnergyCharge(5110001, attack.targets * attack.hits);
				break;
			}
			case 1510:
			case 1511:
			case 1512: {
				chr.handleEnergyCharge(15100004, attack.targets * attack.hits);
				break;
			}
			}
			// handle sacrifice hp loss
			// after BIG BANG, TEMP
			if (attack.targets > 0 && attack.skill == 1211002) { // handle
																	// charged
																	// blow
				final int advcharge_level = chr.getSkillLevel(SkillFactory.getSkill(1220010));
				if (advcharge_level > 0) {
					if (!SkillFactory.getSkill(1220010).getEffect(advcharge_level).makeChanceResult()) {
						chr.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
						chr.cancelEffectFromBuffStat(MapleBuffStat.LIGHTNING_CHARGE);
					}
				} else {
					chr.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
					chr.cancelEffectFromBuffStat(MapleBuffStat.LIGHTNING_CHARGE);
				}
			}

			if (numFinisherOrbs > 0) {
				maxdamage *= numFinisherOrbs;
			} else if (comboBuff != null) {
				ISkill combo;
				if (c.getPlayer().getJob() == 1110 || c.getPlayer().getJob() == 1111) {
					combo = SkillFactory.getSkill(11111001);
				} else {
					combo = SkillFactory.getSkill(1111002);
				}
				if (c.getPlayer().getSkillLevel(combo) > 0) {
					maxdamage *= 1.0 + (combo.getEffect(c.getPlayer().getSkillLevel(combo)).getDamage() / 100.0 - 1.0)
							* (comboBuff.intValue() - 1);
				}
			}

			if (isFinisher(attack.skill)) {
				if (numFinisherOrbs == 0) {
					return;
				}
				maxdamage = 199999; // FIXME reenable damage calculation for
									// finishers
			}
		}
		chr.checkFollow();
		chr.getMap().broadcastMessage(chr,
				MaplePacketCreator.closeRangeAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display,
						attack.animation, attack.speed, attack.allDamage, energy, chr.getLevel(),
						chr.getStat().passive_mastery(), attack.unk, attack.charge),
				chr.getPosition());
		DamageParse.applyAttack(attack, skill, c.getPlayer(), attackCount, maxdamage, effect,
				mirror ? AttackType.NON_RANGED_WITH_MIRROR : AttackType.NON_RANGED);
		WeakReference<MapleCharacter>[] clones = chr.getClones();
		for (int i = 0; i < clones.length; i++) {
			if (clones[i].get() != null) {
				final MapleCharacter clone = clones[i].get();
				final ISkill skil2 = skill;
				final int skillLevel2 = skillLevel;
				final int attackCount2 = attackCount;
				final double maxdamage2 = maxdamage;
				final MapleStatEffect eff2 = effect;
				final AttackInfo attack2 = DamageParse.DivideAttack(attack, 1);

				CloneTimer.getInstance().schedule(new Runnable() {

					public void run() {
						if (attack2.portals != chr.getPortalCount(false)) { // Portal
																			// count
																			// didn't
																			// match.
																			// Ignore
							c.getSession().write(MaplePacketCreator.enableActions());
							return;
						}
						clone.getMap()
								.broadcastMessage(MaplePacketCreator.closeRangeAttack(clone.getId(), attack2.tbyte,
										attack2.skill, skillLevel2, attack2.display, attack2.animation, attack2.speed,
										attack2.allDamage, energy, clone.getLevel(), clone.getStat().passive_mastery(),
										attack2.unk, attack2.charge));
						DamageParse.applyAttack(attack2, skil2, chr, attackCount2, maxdamage2, eff2,
								mirror ? AttackType.NON_RANGED_WITH_MIRROR : AttackType.NON_RANGED);
					}
				}, 500 * i + 500);
			}
		}

	}

}
