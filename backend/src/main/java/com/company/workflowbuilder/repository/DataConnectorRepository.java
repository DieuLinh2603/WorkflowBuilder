package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.DataConnector;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DataConnectorRepository extends JpaRepository<DataConnector, UUID> {
    @Query("select distinct c from DataConnector c left join c.grantedUsers u where c.createdBy.id=:userId or u.id=:userId")
    List<DataConnector> findAccessible(@Param("userId") UUID userId);
}
