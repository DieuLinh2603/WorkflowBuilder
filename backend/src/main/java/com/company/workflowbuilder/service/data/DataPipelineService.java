package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.dto.response.BatchInstanceResponse;
import com.company.workflowbuilder.entity.data.*;
import com.company.workflowbuilder.entity.user.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service @RequiredArgsConstructor
public class DataPipelineService {
    private final DataPipelineRepository pipelines; private final PipelineRunRepository runs;
    private final DatasetRepository datasets; private final DatasetVersionRepository versions; private final DatasetRecordRepository records;
    private final WorkflowDataBindingRepository bindings; private final PipelineFileVersionRepository files;
    private final WorkflowBatchRecordRepository batchRecords;
    private final WorkflowRepository workflows; private final UserRepository users; private final CurrentUserService currentUser;
    private final PipelineDefinitionEngine engine; private final WorkflowDataBindingExecutionService bindingExecution;
    private final NotificationCenterService notifications; private final ObjectMapper mapper;
    private final DataConnectorService connectorService;
    private final WorkflowAuthorizationService workflowAuthorization;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final ConcurrentMap<UUID, AtomicBoolean> pauseSignals = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> runLocks = new ConcurrentHashMap<>();

    @Transactional(readOnly=true) public List<Map<String,Object>> list(){List<DataPipeline> list=currentUser.hasRole(SystemRole.ADMIN)?pipelines.findAll():pipelines.findDistinctByOwnerIdOrSharedWithIdOrderByUpdatedAtDesc(currentUser.id(),currentUser.id());return list.stream().map(this::view).toList();}
    @Transactional(readOnly=true) public Map<String,Object> getView(UUID id){return view(owned(id));}
    @Transactional public Map<String,Object> save(UUID id,Map<String,Object>body){requireDesigner();DataPipeline p=id==null?new DataPipeline():owned(id);if(id==null)p.setOwner(currentUser.user());p.setName(required(body,"name"));p.setDescription((String)body.get("description"));Object definition=body.getOrDefault("definition",Map.of("sources",List.of(),"joins",List.of(),"transforms",List.of()));validateConnectorAccess(definition);p.setDefinitionJson(write(definition));p.setOutputSchemaJson(write(body.getOrDefault("outputSchema",List.of())));p.setBusinessKey(required(body,"businessKey"));p.setScheduleType(String.valueOf(body.getOrDefault("scheduleType","MANUAL")).toUpperCase());if(!Set.of("MANUAL","ONCE","DAILY").contains(p.getScheduleType()))throw new IllegalArgumentException("scheduleType must be MANUAL, ONCE or DAILY");p.setTimezone("Asia/Ho_Chi_Minh");p.setScheduledAt(parseDateTime(body.get("scheduledAt")));p.setDailyTime(parseTime(body.get("dailyTime")));validateSchedule(p);p.setNextRunAt(null);p.setStatus("DRAFT");p.setPublishedAt(null);return view(pipelines.save(p));}
    @Transactional(readOnly=true) public Map<String,Object> deletionImpact(UUID id){
        DataPipeline pipeline=owned(id);
        Optional<Dataset> dataset=datasets.findByPipelineId(id);
        boolean running=runs.existsByPipelineIdAndStatusIn(id,List.of("QUEUED","RUNNING","RETRY","PAUSED"));
        long instanceRecordCount=dataset.map(value->batchRecords.countBySourceDatasetVersionDatasetId(value.getId())).orElse(0L);
        Map<String,Object> impact=new LinkedHashMap<>();
        impact.put("pipelineId",id); impact.put("pipelineName",pipeline.getName());
        impact.put("status",pipeline.getStatus()); impact.put("canDelete",!running&&instanceRecordCount==0);
        impact.put("runCount",runs.countByPipelineId(id));
        impact.put("fileCount",files.countByPipelineId(id));
        impact.put("datasetVersionCount",dataset.map(value->versions.findByDatasetIdOrderByVersionNumberDesc(value.getId()).size()).orElse(0));
        impact.put("bindingCount",dataset.map(value->bindings.countByDatasetId(value.getId())).orElse(0L));
        impact.put("instanceRecordCount",instanceRecordCount);
        if(running)impact.put("reason","Không thể xóa khi pipeline đang chạy hoặc đang chờ chạy lại.");
        else if(instanceRecordCount>0)impact.put("reason","Không thể xóa vì dataset đã tạo dữ liệu cho workflow. Cần giữ pipeline để bảo toàn lịch sử xử lý.");
        return impact;
    }
    @Transactional public void delete(UUID id){
        DataPipeline pipeline=owned(id); requireOwnerOrAdmin(pipeline);
        if(runs.existsByPipelineIdAndStatusIn(id,List.of("QUEUED","RUNNING","RETRY","PAUSED")))
            throw new IllegalStateException("Không thể xóa khi pipeline đang chạy hoặc đang chờ chạy lại");
        Optional<Dataset> dataset=datasets.findByPipelineId(id);
        if(dataset.isPresent()&&batchRecords.countBySourceDatasetVersionDatasetId(dataset.get().getId())>0)
            throw new IllegalStateException("Không thể xóa pipeline vì dataset đã được sử dụng trong workflow");
        pipelines.delete(pipeline);
    }
    @Transactional public Map<String,Object> share(UUID id,UUID userId){
        currentUser.requireAdmin(); DataPipeline pipeline=owned(id);
        if(userId==null){pipeline.setSharedWith(null);return view(pipelines.save(pipeline));}
        User recipient=users.findById(userId).filter(User::isActive)
                .orElseThrow(()->new ResourceNotFoundException("User","id",userId));
        if(!recipient.getSystemRoles().contains(SystemRole.WORKFLOW_OWNER))
            throw new IllegalArgumentException("Chỉ có thể chia sẻ pipeline cho Workflow Owner đang hoạt động");
        if(recipient.getId().equals(pipeline.getOwner().getId()))
            throw new IllegalArgumentException("Workflow Owner này đã là chủ sở hữu pipeline");
        pipeline.setSharedWith(recipient); return view(pipelines.save(pipeline));
    }
    @Transactional public Map<String,Object> preview(UUID id){DataPipeline p=owned(id);PipelineDefinitionEngine.ExecutionResult result=engine.execute(p);p.setStatus("PREVIEWED");pipelines.save(p);Map<String,Object>out=new LinkedHashMap<>();out.put("count",result.records().size());out.put("sample",result.records().stream().limit(100).toList());out.put("detectedSchema",result.detectedSchema());out.put("duplicateKeys",List.of());out.put("unmatchedRows",0);out.put("castErrors",List.of());return out;}
    @Transactional(readOnly=true) public Map<String,Object> discover(UUID id,Map<String,Object> body){DataPipeline p=owned(id);Object definition=body.getOrDefault("definition",Map.of());validateConnectorAccess(definition);PipelineDefinitionEngine.ExecutionResult result=engine.discover(p,definition);int size=Math.max(1,Math.min(integer(body.get("size"),20),100));int page=Math.max(0,integer(body.get("page"),0));int total=result.records().size();int from=Math.min(page*size,total),to=Math.min(from+size,total);Map<String,Object>out=new LinkedHashMap<>();out.put("count",total);out.put("inputCount",result.inputCount());out.put("sourceCount",result.sourceCount());out.put("sample",result.records().subList(from,to));out.put("detectedSchema",result.detectedSchema());out.put("page",page);out.put("size",size);out.put("totalPages",total==0?0:(total+size-1)/size);out.put("hasPrevious",page>0);out.put("hasNext",to<total);return out;}
    @Transactional(readOnly=true) public Map<String,Object> previewOutput(UUID id,Map<String,Object> body){DataPipeline saved=owned(id);Object definition=body.getOrDefault("definition",jsonMap(saved.getDefinitionJson()));validateConnectorAccess(definition);DataPipeline temporary=DataPipeline.builder().id(saved.getId()).definitionJson(write(definition)).outputSchemaJson(write(body.getOrDefault("outputSchema",jsonList(saved.getOutputSchemaJson())))).businessKey(required(body,"businessKey")).build();return previewPage(engine.execute(temporary),body);}
    private Map<String,Object> previewPage(PipelineDefinitionEngine.ExecutionResult result,Map<String,Object>body){int size=Math.max(1,Math.min(integer(body.get("size"),20),100));int page=Math.max(0,integer(body.get("page"),0));int total=result.records().size();int from=Math.min(page*size,total),to=Math.min(from+size,total);Map<String,Object>out=new LinkedHashMap<>();out.put("count",total);out.put("inputCount",result.inputCount());out.put("sourceCount",result.sourceCount());out.put("sample",result.records().subList(from,to));out.put("detectedSchema",result.detectedSchema());out.put("page",page);out.put("size",size);out.put("totalPages",total==0?0:(total+size-1)/size);out.put("hasPrevious",page>0);out.put("hasNext",to<total);return out;}
    @Transactional public Map<String,Object> publish(UUID id){DataPipeline p=owned(id);if(!"PREVIEWED".equals(p.getStatus()))throw new IllegalStateException("Pipeline must preview successfully before publish");p.setStatus("PUBLISHED");p.setPublishedAt(LocalDateTime.now());p.setNextRunAt(initialNextRun(p,LocalDateTime.now()));return view(pipelines.save(p));}
    @Transactional public Map<String,Object> schedule(UUID id,Map<String,Object>body){DataPipeline p=owned(id);p.setScheduleType(String.valueOf(body.getOrDefault("scheduleType","MANUAL")).toUpperCase());p.setTimezone("Asia/Ho_Chi_Minh");p.setScheduledAt(parseDateTime(body.get("scheduledAt")));p.setDailyTime(parseTime(body.get("dailyTime")));validateSchedule(p);p.setNextRunAt("PUBLISHED".equals(p.getStatus())?initialNextRun(p,LocalDateTime.now()):null);return view(pipelines.save(p));}

    @Transactional public Map<String,Object> upload(UUID id,String alias,MultipartFile upload){
        DataPipeline p=owned(id);String sourceAlias=alias==null?"":alias.trim();
        if(!sourceAlias.matches("[a-zA-Z][a-zA-Z0-9_]{0,99}"))throw new IllegalArgumentException("Tên gợi nhớ phải bắt đầu bằng chữ và chỉ gồm chữ, số, dấu gạch dưới");
        if(upload==null||upload.isEmpty())throw new IllegalArgumentException("File CSV trống");
        String originalName=Optional.ofNullable(upload.getOriginalFilename()).orElse("data.csv");
        if(!originalName.toLowerCase(Locale.ROOT).endsWith(".csv"))throw new IllegalArgumentException("Chỉ chấp nhận file có phần mở rộng .csv");
        try{byte[] bytes=upload.getBytes();if(bytes.length==0)throw new IllegalArgumentException("File CSV trống");String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));int number=files.findByPipelineIdAndSourceAliasOrderByVersionNumberDesc(id,sourceAlias).stream().findFirst().map(x->x.getVersionNumber()+1).orElse(1);PipelineFileVersion f=files.save(PipelineFileVersion.builder().pipeline(p).sourceAlias(sourceAlias).versionNumber(number).originalName(originalName).contentType(upload.getContentType()).contentBytes(bytes).checksum(hash).uploadedBy(currentUser.user()).build());return Map.of("id",f.getId(),"sourceAlias",sourceAlias,"versionNumber",number,"fileName",f.getOriginalName(),"checksum",hash);}
        catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("Không thể lưu file CSV: "+ex.getMessage(),ex);}
    }

    public Map<String,Object> requestRun(UUID id){DataPipeline p=owned(id);if(!"PUBLISHED".equals(p.getStatus()))throw new IllegalStateException("Only a published pipeline can run");if(runs.existsByPipelineIdAndStatusIn(id,List.of("QUEUED","RUNNING","RETRY","PAUSED")))throw new IllegalStateException("Pipeline đã có một lượt chạy đang xử lý hoặc tạm ngừng");PipelineRun run=createRun(p,"MANUAL",null);executeRun(run);return runView(runs.findById(run.getId()).orElse(run));}
    @Transactional public PipelineRun enqueueScheduled(DataPipeline p,LocalDateTime scheduledFor){if(runs.existsByPipelineIdAndScheduledFor(p.getId(),scheduledFor))return null;PipelineRun run=createRun(p,"SCHEDULED",scheduledFor);p.setNextRunAt(nextAfter(p,scheduledFor));pipelines.save(p);return run;}
    public void executeRun(PipelineRun candidate){
        UUID runId=candidate.getId(); Object lock=runLocks.computeIfAbsent(runId,key->new Object());
        AtomicBoolean pause=pauseSignals.computeIfAbsent(runId,key->new AtomicBoolean(false));
        PipelineRun run;
        synchronized(lock){
            run=runs.findById(runId).orElseThrow(()->new ResourceNotFoundException("PipelineRun","id",runId));
            if("PAUSED".equals(run.getStatus())||pause.get())return;
            if(!Set.of("QUEUED","RETRY").contains(run.getStatus()))return;
            run.setAttemptCount(run.getAttemptCount()+1);run.setStatus("RUNNING");
            if(run.getStartedAt()==null)run.setStartedAt(LocalDateTime.now());runs.save(run);
        }
        try{
            PipelineDefinitionEngine.ExecutionResult result=engine.execute(run.getPipeline(),pause::get);
            synchronized(lock){
                run=runs.findById(runId).orElseThrow(()->new ResourceNotFoundException("PipelineRun","id",runId));
                if(pause.get()||"PAUSED".equals(run.getStatus())){markPaused(run);return;}
                PipelineRun finalRun=run;
                DatasetVersion version=new TransactionTemplate(transactionManager).execute(status->{
                    finalRun.setSourceCount(result.sourceCount());finalRun.setInputCount(result.inputCount());finalRun.setOutputCount(result.records().size());
                    DatasetVersion published=publishDataset(finalRun,result.records());finalRun.setChangedCount(published.getChangedCount());
                    finalRun.setStatus(published.getChangedCount()==0?"NO_CHANGES":"SUCCESS");finalRun.setCompletedAt(LocalDateTime.now());finalRun.setNextAttemptAt(null);runs.save(finalRun);return published;
                });
                pauseSignals.remove(runId);runLocks.remove(runId,lock);
                if(version!=null)eventPublisher.publishEvent(new DatasetVersionPublishedEvent(version.getId()));
            }
        }catch(PipelineDefinitionEngine.StageException ex){synchronized(lock){run=runs.findById(runId).orElse(run);if(pause.get()||"PAUSED".equals(run.getStatus()))markPaused(run);else fail(run,ex.getStage(),ex.getMessage());}}
        catch(Exception ex){synchronized(lock){run=runs.findById(runId).orElse(run);if(pause.get()||"PAUSED".equals(run.getStatus()))markPaused(run);else fail(run,"PUBLISH",ex.getMessage());}}
    }
    public Map<String,Object> pauseActiveRun(UUID pipelineId){
        owned(pipelineId);
        PipelineRun run=runs.findFirstByPipelineIdAndStatusInOrderByCreatedAtDesc(pipelineId,List.of("QUEUED","RUNNING","RETRY"))
                .orElseThrow(()->new IllegalStateException("Pipeline không có lượt chạy nào có thể tạm ngừng"));
        Object lock=runLocks.computeIfAbsent(run.getId(),key->new Object());
        synchronized(lock){
            PipelineRun current=runs.findById(run.getId()).orElseThrow(()->new ResourceNotFoundException("PipelineRun","id",run.getId()));
            if(!Set.of("QUEUED","RUNNING","RETRY").contains(current.getStatus()))throw new IllegalStateException("Lượt chạy này không còn có thể tạm ngừng");
            pauseSignals.computeIfAbsent(current.getId(),key->new AtomicBoolean()).set(true);markPaused(current);return runView(current);
        }
    }
    public Map<String,Object> resumeRun(UUID pipelineId,UUID runId){
        owned(pipelineId); PipelineRun run=runs.findById(runId).orElseThrow(()->new ResourceNotFoundException("PipelineRun","id",runId));
        if(!run.getPipeline().getId().equals(pipelineId))throw new IllegalArgumentException("Lượt chạy không thuộc pipeline này");
        Object lock=runLocks.computeIfAbsent(runId,key->new Object());
        synchronized(lock){
            run=runs.findById(runId).orElseThrow(()->new ResourceNotFoundException("PipelineRun","id",runId));
            if(!"PAUSED".equals(run.getStatus()))throw new IllegalStateException("Chỉ có thể tiếp tục một lượt chạy đang tạm ngừng");
            pauseSignals.computeIfAbsent(runId,key->new AtomicBoolean()).set(false);run.setStatus("QUEUED");run.setNextAttemptAt(LocalDateTime.now());run.setCompletedAt(null);runs.save(run);
        }
        executeRun(run);return runView(runs.findById(runId).orElse(run));
    }
    private void markPaused(PipelineRun run){run.setStatus("PAUSED");run.setNextAttemptAt(null);run.setCompletedAt(null);runs.save(run);}
    private void fail(PipelineRun run,String stage,String message){run.setErrorStage(stage);run.setErrorMessage(message==null?"Unknown error":message.substring(0,Math.min(2000,message.length())));if(run.getAttemptCount()<3){run.setStatus("RETRY");run.setNextAttemptAt(LocalDateTime.now().plusMinutes(switch(run.getAttemptCount()){case 1->1;case 2->5;default->15;}));}else{run.setStatus("FAILED");run.setCompletedAt(LocalDateTime.now());String detail=run.getPipeline().getName()+" failed after 3 attempts: "+run.getErrorMessage();notifications.create(run.getPipeline().getOwner(),"Data pipeline failed",detail,null,"/pipelines/"+run.getPipeline().getId());users.findDistinctBySystemRolesContainingAndActiveTrue(SystemRole.ADMIN).stream().filter(u->!u.getId().equals(run.getPipeline().getOwner().getId())).forEach(u->notifications.create(u,"Data pipeline failed",detail,null,"/pipelines/"+run.getPipeline().getId()));}runs.save(run);}
    private DatasetVersion publishDataset(PipelineRun run,List<Map<String,Object>>output){DataPipeline p=run.getPipeline();Dataset ds=datasets.findByPipelineId(p.getId()).orElseGet(()->datasets.save(Dataset.builder().pipeline(p).name(p.getName()).latestVersion(0).build()));Map<String,String> previous=new HashMap<>();if(ds.getLatestVersion()>0){DatasetVersion pv=versions.findByDatasetIdAndVersionNumber(ds.getId(),ds.getLatestVersion()).orElseThrow();for(DatasetRecord r:records.findByDatasetVersionId(pv.getId()))previous.put(r.getBusinessKey(),r.getChecksum());}int number=ds.getLatestVersion()+1;List<String> businessKeys=Arrays.stream(p.getBusinessKey().split(",")).map(String::trim).toList();List<PendingRecord> pending=new ArrayList<>();int changed=0;for(Map<String,Object>row:output){String key=businessKey(row,businessKeys),checksum=engine.checksum(row),type=!previous.containsKey(key)?"NEW":previous.get(key).equals(checksum)?"UNCHANGED":"CHANGED";if(!"UNCHANGED".equals(type))changed++;pending.add(new PendingRecord(key,checksum,type,row));}DatasetVersion version=versions.save(DatasetVersion.builder().dataset(ds).pipelineRun(run).versionNumber(number).recordCount(output.size()).changedCount(changed).schemaJson(p.getOutputSchemaJson()).build());for(PendingRecord r:pending)records.save(DatasetRecord.builder().datasetVersion(version).businessKey(r.key()).checksum(r.checksum()).changeType(r.type()).payloadJson(write(r.payload())).build());ds.setLatestVersion(number);datasets.save(ds);return version;}

    @Transactional(readOnly=true) public List<Map<String,Object>> runHistory(UUID pipelineId){owned(pipelineId);return runs.findByPipelineIdOrderByCreatedAtDesc(pipelineId).stream().map(this::runView).toList();}
    @Transactional(readOnly=true) public Page<Map<String,Object>> datasetRecords(UUID datasetId,int version,int page,int size){Dataset ds=datasets.findById(datasetId).orElseThrow(()->new ResourceNotFoundException("Dataset","id",datasetId));owned(ds.getPipeline().getId());DatasetVersion v=versions.findByDatasetIdAndVersionNumber(datasetId,version).orElseThrow(()->new ResourceNotFoundException("DatasetVersion","version",version));return records.findByDatasetVersionId(v.getId(),PageRequest.of(page,Math.min(size,200))).map(this::recordView);}
    @Transactional(readOnly=true) public List<Map<String,Object>> datasets(){List<DataPipeline> allowed=currentUser.hasRole(SystemRole.ADMIN)?pipelines.findAll():pipelines.findByOwnerIdOrderByUpdatedAtDesc(currentUser.id());return allowed.stream().map(p->datasets.findByPipelineId(p.getId()).map(d->Map.<String,Object>of("id",d.getId(),"pipelineId",p.getId(),"name",d.getName(),"latestVersion",d.getLatestVersion())).orElse(null)).filter(Objects::nonNull).toList();}

    @Transactional public Map<String,Object> saveBinding(UUID id,Map<String,Object>body){
        Dataset ds=datasets.findById(UUID.fromString(required(body,"datasetId")))
                .orElseThrow(()->new ResourceNotFoundException("Dataset","id",body.get("datasetId")));
        owned(ds.getPipeline().getId());
        Workflow wf=workflows.findById(UUID.fromString(required(body,"workflowId")))
                .orElseThrow(()->new ResourceNotFoundException("Workflow","id",body.get("workflowId")));
        workflowAuthorization.requireOwnerOrAdmin(wf);
        if(wf.getStatus()!=WorkflowStatus.PUBLISHED)throw new IllegalArgumentException("Binding requires a published workflow version");

        WorkflowDataBinding b=id==null
                ? bindings.findByDatasetIdAndWorkflowId(ds.getId(),wf.getId()).orElseGet(WorkflowDataBinding::new)
                : bindingOwned(id);
        boolean creating=b.getId()==null;
        if(creating){b.setDataset(ds);b.setWorkflow(wf);b.setCreatedBy(currentUser.user());}
        else if(!b.getDataset().getId().equals(ds.getId())||!b.getWorkflow().getId().equals(wf.getId()))
            throw new IllegalArgumentException("Dataset và Workflow của Binding hiện tại không thể thay đổi");
        b.setTriggerMode(String.valueOf(body.getOrDefault("triggerMode","AUTO_ON_DATASET_SUCCESS")));
        b.setFilterJson(write(body.getOrDefault("filter",List.of())));
        b.setMappingJson(write(body.getOrDefault("mapping",Map.of())));
        b.setActive(!Boolean.FALSE.equals(body.get("active")));
        return bindingView(bindings.save(b));
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> bindingList(UUID workflowId){return bindings.findByWorkflowId(workflowId).stream().filter(b->canOwn(b.getDataset().getPipeline())).map(this::bindingView).toList();}
    @Transactional public Map<String,Object> triggerBinding(UUID id){WorkflowDataBinding b=bindingOwned(id);Dataset ds=b.getDataset();if(ds.getLatestVersion()<=b.getLastConsumedVersion())return Map.of("status","NO_CHANGES");DatasetVersion v=versions.findByDatasetIdAndVersionNumber(ds.getId(),ds.getLatestVersion()).orElseThrow();BatchInstanceResponse result=bindingExecution.consume(b.getId(),v.getId());return Map.of("status",result==null?"NO_CHANGES":"STARTED","batch",result==null?Map.of():result);}

    public DataPipeline owned(UUID id){DataPipeline p=pipelines.findById(id).orElseThrow(()->new ResourceNotFoundException("DataPipeline","id",id));if(!canOwn(p))throw new AccessDeniedException("Bạn không có quyền truy cập pipeline này");return p;}private boolean canOwn(DataPipeline p){return currentUser.hasRole(SystemRole.ADMIN)||p.getOwner().getId().equals(currentUser.id())||(p.getSharedWith()!=null&&p.getSharedWith().getId().equals(currentUser.id()));}
    private void requireOwnerOrAdmin(DataPipeline pipeline){if(!currentUser.hasRole(SystemRole.ADMIN)&&!pipeline.getOwner().getId().equals(currentUser.id()))throw new AccessDeniedException("Chỉ Admin hoặc chủ sở hữu mới có thể xóa pipeline");}
    private WorkflowDataBinding bindingOwned(UUID id){WorkflowDataBinding b=bindings.findById(id).orElseThrow(()->new ResourceNotFoundException("WorkflowDataBinding","id",id));owned(b.getDataset().getPipeline().getId());return b;}
    private PipelineRun createRun(DataPipeline p,String trigger,LocalDateTime scheduled){return runs.save(PipelineRun.builder().pipeline(p).triggerType(trigger).status("QUEUED").scheduledFor(scheduled).nextAttemptAt(LocalDateTime.now()).build());}
    private LocalDateTime initialNextRun(DataPipeline p,LocalDateTime now){return switch(p.getScheduleType()){case"ONCE"->toServerTime(p.getScheduledAt(),p.getTimezone());case"DAILY"->nextDaily(p,now);default->null;};}private LocalDateTime nextAfter(DataPipeline p,LocalDateTime from){return "DAILY".equals(p.getScheduleType())?nextDaily(p,from.plusSeconds(1)):null;}private LocalDateTime nextDaily(DataPipeline p,LocalDateTime serverNow){ZoneId server=ZoneId.systemDefault(),target=ZoneId.of(p.getTimezone());ZonedDateTime targetNow=serverNow.atZone(server).withZoneSameInstant(target);ZonedDateTime next=ZonedDateTime.of(targetNow.toLocalDate(),p.getDailyTime(),target);if(!next.isAfter(targetNow))next=next.plusDays(1);return next.withZoneSameInstant(server).toLocalDateTime();}private LocalDateTime toServerTime(LocalDateTime local,String zone){return local==null?null:local.atZone(ZoneId.of(zone)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}
    private void validateSchedule(DataPipeline p){if("ONCE".equals(p.getScheduleType())&&p.getScheduledAt()==null)throw new IllegalArgumentException("scheduledAt is required for ONCE");if("DAILY".equals(p.getScheduleType())&&p.getDailyTime()==null)throw new IllegalArgumentException("dailyTime is required for DAILY");}
    private Map<String,Object>view(DataPipeline p){Map<String,Object>x=new LinkedHashMap<>();x.put("id",p.getId());x.put("name",p.getName());x.put("description",p.getDescription());x.put("ownerId",p.getOwner().getId());x.put("ownerName",p.getOwner().getDisplayName());x.put("sharedWithId",p.getSharedWith()==null?null:p.getSharedWith().getId());x.put("sharedWithName",p.getSharedWith()==null?null:p.getSharedWith().getDisplayName());x.put("canDelete",currentUser.hasRole(SystemRole.ADMIN)||p.getOwner().getId().equals(currentUser.id()));x.put("status",p.getStatus());x.put("definition",jsonMap(p.getDefinitionJson()));x.put("outputSchema",jsonList(p.getOutputSchemaJson()));x.put("businessKey",p.getBusinessKey());x.put("scheduleType",p.getScheduleType());x.put("scheduledAt",p.getScheduledAt());x.put("dailyTime",p.getDailyTime());x.put("timezone",p.getTimezone());x.put("nextRunAt",p.getNextRunAt());runs.findFirstByPipelineIdAndStatusInOrderByCreatedAtDesc(p.getId(),List.of("QUEUED","RUNNING","RETRY","PAUSED")).ifPresent(run->x.put("activeRun",runView(run)));datasets.findByPipelineId(p.getId()).ifPresent(d->{x.put("datasetId",d.getId());x.put("latestVersion",d.getLatestVersion());});return x;}
    private Map<String,Object>runView(PipelineRun r){Map<String,Object>x=new LinkedHashMap<>();x.put("id",r.getId());x.put("pipelineId",r.getPipeline().getId());x.put("status",r.getStatus());x.put("triggerType",r.getTriggerType());x.put("attemptCount",r.getAttemptCount());x.put("sourceCount",r.getSourceCount());x.put("inputCount",r.getInputCount());x.put("outputCount",r.getOutputCount());x.put("changedCount",r.getChangedCount());x.put("errorStage",r.getErrorStage());x.put("errorMessage",r.getErrorMessage());x.put("startedAt",r.getStartedAt());x.put("completedAt",r.getCompletedAt());return x;}
    private Map<String,Object>recordView(DatasetRecord r){return Map.of("id",r.getId(),"businessKey",r.getBusinessKey(),"checksum",r.getChecksum(),"changeType",r.getChangeType(),"payload",jsonMap(r.getPayloadJson()));}
    private Map<String,Object>bindingView(WorkflowDataBinding b){Map<String,Object>x=new LinkedHashMap<>();x.put("id",b.getId());x.put("datasetId",b.getDataset().getId());x.put("workflowId",b.getWorkflow().getId());x.put("workflowName",b.getWorkflow().getName());x.put("triggerMode",b.getTriggerMode());x.put("filter",jsonList(b.getFilterJson()));x.put("mapping",jsonMap(b.getMappingJson()));x.put("lastConsumedVersion",b.getLastConsumedVersion());x.put("active",b.isActive());return x;}
    private String businessKey(Map<String,Object>row,List<String>keys){StringJoiner j=new StringJoiner("\u001f");keys.forEach(k->j.add(Objects.toString(row.get(k),"")));return j.toString();}private int integer(Object value,int fallback){if(value==null)return fallback;try{return Integer.parseInt(String.valueOf(value));}catch(NumberFormatException ex){return fallback;}}private String required(Map<String,Object>b,String k){String v=String.valueOf(b.getOrDefault(k,"")).trim();if(v.isEmpty())throw new IllegalArgumentException(k+" is required");return v;}private String write(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON",e);}}private Map<String,Object>jsonMap(String s){try{return mapper.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}private List<Map<String,Object>>jsonList(String s){try{return mapper.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}private LocalDateTime parseDateTime(Object v){return v==null||String.valueOf(v).isBlank()?null:LocalDateTime.parse(String.valueOf(v));}private LocalTime parseTime(Object v){return v==null||String.valueOf(v).isBlank()?null:LocalTime.parse(String.valueOf(v));}
    private record PendingRecord(String key,String checksum,String type,Map<String,Object>payload){}
    private void requireDesigner(){if(!currentUser.hasRole(SystemRole.ADMIN)&&!currentUser.hasRole(SystemRole.WORKFLOW_OWNER))throw new AccessDeniedException("Admin or Workflow Owner role is required");}
    private void validateConnectorAccess(Object definition){Map<String,Object>d=mapper.convertValue(definition,new TypeReference<>(){});Object raw=d.get("sources");if(raw instanceof Collection<?> sources)for(Object item:sources){Map<String,Object>s=mapper.convertValue(item,new TypeReference<>(){});if(s.get("connectorId")!=null)connectorService.accessible(UUID.fromString(String.valueOf(s.get("connectorId"))));}}
}
