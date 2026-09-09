package com.company.workflowbuilder.repository;
import com.company.workflowbuilder.entity.data.PipelineFileVersion; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PipelineFileVersionRepository extends JpaRepository<PipelineFileVersion,UUID>{ List<PipelineFileVersion> findByPipelineIdAndSourceAliasOrderByVersionNumberDesc(UUID pipelineId,String sourceAlias); }
