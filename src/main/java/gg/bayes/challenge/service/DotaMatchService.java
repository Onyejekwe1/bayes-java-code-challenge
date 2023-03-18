package gg.bayes.challenge.service;

import gg.bayes.challenge.persistence.model.CombatLogEntryEntity;
import gg.bayes.challenge.persistence.model.MatchEntity;
import gg.bayes.challenge.persistence.repository.CombatLogEntryRepository;
import gg.bayes.challenge.persistence.repository.MatchRepository;
import gg.bayes.challenge.rest.model.HeroDamage;
import gg.bayes.challenge.rest.model.HeroItem;
import gg.bayes.challenge.rest.model.HeroKills;
import gg.bayes.challenge.rest.model.HeroSpells;
import gg.bayes.challenge.service.contract.IDotaMatchService;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DotaMatchService implements IDotaMatchService {
    private CombatLogEntryRepository combatLogEntryRepository;
    private MatchRepository matchRepository;

    public DotaMatchService(CombatLogEntryRepository combatLogEntryRepository, MatchRepository matchRepository) {
        this.combatLogEntryRepository = combatLogEntryRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public Long processCombatLog(String combatLog) {
        // Create a new MatchEntity object and save it to the database
        MatchEntity match = new MatchEntity();
        matchRepository.save(match);

        // Get the generated match ID
        Long matchId = match.getId();

        // Process the combat log lines
        String[] lines = combatLog.split("\n");
        for (String line : lines) {
            String[] tokens = line.split(" ");
            if (tokens.length < 2) {
                continue;
            }

            String timestampString = tokens[0].replace("[", "").replace("]", "");
            System.out.println("timestampString: " + timestampString);
            LocalTime time = LocalTime.parse(timestampString);
            long totalMilliseconds = time.toSecondOfDay() * 1000L + time.getNano() / 1_000_000;

            String event = tokens[1];
            if (event.startsWith("npc_dota_hero")) {
                String hero = event.substring("npc_dota_hero_".length());
                String action = tokens[2];
                switch (action) {
                    case "casts":
                        processSpellCast(line, totalMilliseconds, hero, match);
                        break;
                    case "hits":
                        processDamageDone(line, totalMilliseconds, hero, match);
                        break;
                    case "is":
                        if (tokens[3].equals("killed")) {
                            processHeroKilled(line, totalMilliseconds, hero, match);
                        }
                        break;
                    case "uses":
                        if (tokens[3].startsWith("item_")) {
                            processItemPurchased(line, totalMilliseconds, hero, match);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        return matchId;
    }




    @Override
    public List<HeroKills> getHeroKills(Long matchId) {
        List<CombatLogEntryEntity> kills = combatLogEntryRepository.findByMatchIdAndType(matchId, CombatLogEntryEntity.Type.HERO_KILLED);
        return kills.stream()
                .collect(Collectors.groupingBy(CombatLogEntryEntity::getActor, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new HeroKills(entry.getKey(), entry.getValue().intValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<HeroItem> getHeroItems(Long matchId, String heroName) {
        List<CombatLogEntryEntity> items = combatLogEntryRepository.findByMatchIdAndActorAndType(matchId, heroName, CombatLogEntryEntity.Type.ITEM_PURCHASED);
        return items.stream()
                .map(entry -> new HeroItem(entry.getItem(), entry.getTimestamp()))
                .collect(Collectors.toList());
    }

    @Override
    public List<HeroSpells> getHeroSpells(Long matchId, String heroName) {
        List<CombatLogEntryEntity> spells = combatLogEntryRepository.findByMatchIdAndActorAndType(matchId, heroName, CombatLogEntryEntity.Type.SPELL_CAST);
        return spells.stream()
                .collect(Collectors.groupingBy(CombatLogEntryEntity::getAbility, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new HeroSpells(entry.getKey(), entry.getValue().intValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<HeroDamage> getHeroDamage(Long matchId, String heroName) {
        List<CombatLogEntryEntity> damages = combatLogEntryRepository.findByMatchIdAndActorAndType(matchId, heroName, CombatLogEntryEntity.Type.DAMAGE_DONE);
        return damages.stream()
                .collect(Collectors.groupingBy(CombatLogEntryEntity::getTarget,
                        Collectors.summingInt(CombatLogEntryEntity::getDamage)))
                .entrySet().stream()
                .map(entry -> new HeroDamage(entry.getKey(), (int) damages.stream()
                        .filter(damage -> damage.getTarget().equals(entry.getKey())).count(), entry.getValue()))
                .collect(Collectors.toList());
    }


    private String extractHeroName(String token) {
        if (token.startsWith("npc_dota_hero_")) {
            return token.substring("npc_dota_hero_".length());
        }
        return null;
    }

    private CombatLogEntryEntity processSpellCast(String line, long timestamp, String hero, MatchEntity match) {
        // Split the line
        String[] splitLine = line.split(" ");

        // Extract ability name
        String abilityName = splitLine[4];

        // Extract ability level
        int abilityLevel = 0;
        for (int i = 6; i < splitLine.length; i++) {
                abilityLevel = Integer.parseInt(splitLine[i].substring(0, splitLine[i].length() - 1));
                break;
        }

        return createCombatLogEntry(match, timestamp, hero, null, abilityName, abilityLevel, null, null, CombatLogEntryEntity.Type.SPELL_CAST);
    }

    private CombatLogEntryEntity processDamageDone(String line, long timestamp, String hero, MatchEntity match) {
        String[] splitLine = line.split(" ");
        String target = null;
        int damage = 0;

        for (int i = 0; i < splitLine.length; i++) {
            if (target == null && i == 3) {
                target = extractHeroName(splitLine[i]);
            } else if (splitLine[i].equals("for")) {
                if (i + 1 < splitLine.length) {
                    damage = Integer.parseInt(splitLine[i + 1]);
                    break;
                }
            }
        }

        return createCombatLogEntry(match, timestamp, hero, target, null, null, null, damage, CombatLogEntryEntity.Type.DAMAGE_DONE);
    }

    private CombatLogEntryEntity processHeroKilled(String line, long timestamp, String hero, MatchEntity match) {
        String target = extractHeroName(line.split(" ")[3]);
        return createCombatLogEntry(match, timestamp, hero, target, null, null, null, null, CombatLogEntryEntity.Type.HERO_KILLED);
    }

    private CombatLogEntryEntity processItemPurchased(String line, long timestamp, String hero, MatchEntity match) {
        String item = line.split(" ")[3].substring(5);
        return createCombatLogEntry(match, timestamp, hero, null, null, null, item, null, CombatLogEntryEntity.Type.ITEM_PURCHASED);
    }

    private CombatLogEntryEntity createCombatLogEntry(MatchEntity match, long timestamp, String hero, String target, String ability, Integer abilityLevel, String item, Integer damage, CombatLogEntryEntity.Type type) {
        CombatLogEntryEntity entry = new CombatLogEntryEntity();
        entry.setMatch(match);
        entry.setTimestamp(timestamp);
        entry.setActor(hero);
        entry.setTarget(target);
        entry.setAbility(ability);
        entry.setAbilityLevel(abilityLevel);
        entry.setItem(item);
        entry.setDamage(damage);
        entry.setType(type);
        combatLogEntryRepository.save(entry);
        return entry;
    }


}
