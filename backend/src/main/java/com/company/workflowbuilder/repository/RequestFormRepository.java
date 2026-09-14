package com.company.workflowbuilder.repository;
import com.company.workflowbuilder.entity.form.RequestForm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RequestFormRepository extends JpaRepository<RequestForm,UUID> { List<RequestForm> findAllByOrderByUpdatedAtDesc(); }
