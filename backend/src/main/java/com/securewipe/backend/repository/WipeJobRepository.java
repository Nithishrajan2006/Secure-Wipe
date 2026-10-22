package com.securewipe.backend.repository;

import com.securewipe.backend.entity.User;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.WipeMethod;
import com.securewipe.backend.enums.WipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WipeJobRepository extends JpaRepository<WipeJob, Long> {

    Optional<WipeJob> findByJobId(String jobId);

    Optional<WipeJob> findByCertificateId(String certificateId);

    Page<WipeJob> findByUser(User user, Pageable pageable);

    @Override
    Page<WipeJob> findAll(Pageable pageable);

    List<WipeJob> findByStatus(WipeStatus status);

    List<WipeJob> findByStatusIn(List<WipeStatus> statuses);

    List<WipeJob> findByUserAndStatus(User user, WipeStatus status);

    // --- Count / sum queries using named parameters (Hibernate 6 compatible) ---

    @Query("SELECT COUNT(w) FROM WipeJob w WHERE w.user = :user AND w.status = :status")
    long countByUserAndStatus(@Param("user") User user, @Param("status") WipeStatus status);

    @Query("SELECT COUNT(w) FROM WipeJob w WHERE w.status = :status")
    long countByStatus(@Param("status") WipeStatus status);

    @Query("SELECT COALESCE(SUM(w.deviceSize), 0) FROM WipeJob w WHERE w.user = :user AND w.status = :status")
    Long sumDeviceSizeByUserAndStatus(@Param("user") User user, @Param("status") WipeStatus status);

    @Query("SELECT COALESCE(SUM(w.deviceSize), 0) FROM WipeJob w WHERE w.status = :status")
    Long sumDeviceSizeByStatus(@Param("status") WipeStatus status);

    // Sum of bytesWiped (actual bytes written by the wipe engine, not deviceSize)
    @Query("SELECT COALESCE(SUM(w.bytesWiped), 0) FROM WipeJob w WHERE w.user = :user AND w.status = :status")
    Long sumBytesWipedByUserAndStatus(@Param("user") User user, @Param("status") WipeStatus status);

    @Query("SELECT COALESCE(SUM(w.bytesWiped), 0) FROM WipeJob w WHERE w.status = :status")
    Long sumBytesWipedByStatus(@Param("status") WipeStatus status);

    @Query("SELECT w FROM WipeJob w WHERE w.user = :user ORDER BY w.createdAt DESC")
    List<WipeJob> findRecentByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT w FROM WipeJob w ORDER BY w.createdAt DESC")
    List<WipeJob> findAllRecent(Pageable pageable);

    @Query("SELECT w FROM WipeJob w WHERE w.user = :user " +
           "AND (:status IS NULL OR w.status = :status) " +
           "AND (:method IS NULL OR w.wipeMethod = :method) " +
           "AND (:from IS NULL OR w.createdAt >= :from) " +
           "AND (:to IS NULL OR w.createdAt <= :to)")
    Page<WipeJob> findByFilters(
            @Param("user") User user,
            @Param("status") WipeStatus status,
            @Param("method") WipeMethod method,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT w FROM WipeJob w WHERE " +
           "(:status IS NULL OR w.status = :status) " +
           "AND (:method IS NULL OR w.wipeMethod = :method) " +
           "AND (:from IS NULL OR w.createdAt >= :from) " +
           "AND (:to IS NULL OR w.createdAt <= :to)")
    Page<WipeJob> findAllByFilters(
            @Param("status") WipeStatus status,
            @Param("method") WipeMethod method,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT COUNT(w) FROM WipeJob w WHERE w.user = :user AND w.status IN :statuses")
    long countByUserAndStatusIn(@Param("user") User user, @Param("statuses") List<WipeStatus> statuses);

    @Query("SELECT COUNT(w) FROM WipeJob w WHERE w.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<WipeStatus> statuses);

    @Query("SELECT COUNT(w) FROM WipeJob w WHERE w.user = :user")
    long countByUser(@Param("user") User user);
}
