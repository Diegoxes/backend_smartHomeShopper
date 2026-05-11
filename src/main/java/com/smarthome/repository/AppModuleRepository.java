package com.smarthome.repository;

import com.smarthome.entity.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppModuleRepository extends JpaRepository<AppModule, Long> {
    Optional<AppModule> findByKey(String key);
}
