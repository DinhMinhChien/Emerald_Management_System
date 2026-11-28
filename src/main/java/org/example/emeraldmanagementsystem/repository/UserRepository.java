package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,String> {
    Optional<User> findUserByUsername(String username);
    Optional<User> findUserByUsernameAndDeletedFalse(String username);
    Optional<User> findByResidentIdAndDeletedFalse(String residentId);
    boolean existsByUsernameAndDeletedFalse(String username);

    @Query("""
            select distinct user.residentId
            from User user
            where user.deleted = false
              and user.residentId in :residentIds
            """)
    List<String> findResidentIdsWithAccounts(@Param("residentIds") Collection<String> residentIds);

    @Query("""
            select user.residentId as residentId,
                   user.status as status
            from User user
            where user.deleted = false
              and user.residentId in :residentIds
            """)
    List<ResidentAccountStatusProjection> findResidentAccountStatuses(
            @Param("residentIds") Collection<String> residentIds
    );

    interface ResidentAccountStatusProjection {
        String getResidentId();
        String getStatus();
    }
}
