package gg.bayes.challenge.persistence.repository;

import gg.bayes.challenge.persistence.model.CombatLogEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CombatLogEntryRepository extends JpaRepository<CombatLogEntryEntity, Long> {
    List<CombatLogEntryEntity> findByMatchIdAndType(Long matchId, CombatLogEntryEntity.Type type);
    List<CombatLogEntryEntity> findByMatchIdAndActorAndType(Long matchId, String actor, CombatLogEntryEntity.Type type);
}
