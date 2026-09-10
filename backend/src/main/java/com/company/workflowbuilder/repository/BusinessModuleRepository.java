package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.metadata.BusinessModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusinessModuleRepository extends JpaRepository<BusinessModule, String> {
    List<BusinessModule> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<BusinessModule> findByCodeInAndActiveTrueOrderBySortOrderAscNameAsc(Collection<String> codes);
    List<BusinessModule> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByNameIgnoreCaseAndCodeNot(String name, String code);
    boolean existsByNameIgnoreCase(String name);
}
