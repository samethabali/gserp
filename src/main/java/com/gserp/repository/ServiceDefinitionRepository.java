package com.gserp.repository;

import com.gserp.model.ServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {
    List<ServiceDefinition> findByActiveTrue();
}
