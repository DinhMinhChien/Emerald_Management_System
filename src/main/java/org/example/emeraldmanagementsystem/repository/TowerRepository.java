package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.Tower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TowerRepository extends JpaRepository<Tower, String> {
    List<Tower> findAllByDeletedFalseOrderByCodeAsc();
    Optional<Tower> findByIdAndDeletedFalse(String id);
}
