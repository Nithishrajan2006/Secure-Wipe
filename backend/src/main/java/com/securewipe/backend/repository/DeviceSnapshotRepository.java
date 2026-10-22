package com.securewipe.backend.repository;

import com.securewipe.backend.entity.DeviceSnapshot;
import com.securewipe.backend.entity.WipeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceSnapshotRepository extends JpaRepository<DeviceSnapshot, Long> {
    Optional<DeviceSnapshot> findByWipeJob(WipeJob wipeJob);
}
