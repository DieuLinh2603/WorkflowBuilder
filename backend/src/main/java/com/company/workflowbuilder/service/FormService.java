package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.entity.form.*;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class FormService {
    private final RequestFormRepository forms;
    private final FormVersionRepository versions;
    private final FormFieldRepository fields;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() {
        return forms.findAllByOrderByUpdatedAtDesc().stream().filter(form -> !form.isArchived())
                .map(this::view).toList();
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(UUID id) { return view(form(id)); }

    @Transactional
    public Map<String,Object> create(Map<String,Object> body) {
        currentUser.requireAdmin();
        RequestForm form=forms.save(RequestForm.builder().name(required(body,"name"))
                .description(text(body.get("description"))).createdBy(currentUser.user()).build());
        FormVersion version=versions.save(FormVersion.builder().form(form).versionNumber(1).build());
        replaceFields(version, body.get("fields"));
        return versionView(version);
    }

    @Transactional
    public Map<String,Object> updateVersion(UUID formId,UUID versionId,Map<String,Object> body) {
        currentUser.requireAdmin();
        FormVersion version=version(formId,versionId); requireDraft(version);
        RequestForm form=version.getForm();
        if(body.containsKey("name")) form.setName(required(body,"name"));
        if(body.containsKey("description")) form.setDescription(text(body.get("description")));
        version.setInstruction(text(body.get("instruction")));
        String mode=String.valueOf(body.getOrDefault("submissionMode","SINGLE")).toUpperCase(Locale.ROOT);
        if(!Set.of("SINGLE","BATCH").contains(mode)) throw new IllegalArgumentException("submissionMode must be SINGLE or BATCH");
        version.setSubmissionMode(mode);
        int max=integer(body.get("maxBatchRows"),500);
        if(max<1||max>2000) throw new IllegalArgumentException("maxBatchRows must be between 1 and 2000");
        version.setMaxBatchRows(max);
        version.setRecordRecipientFieldKey(blankToNull(body.get("recordRecipientFieldKey")));
        replaceFields(version,body.get("fields"));
        validateVersion(version);
        forms.save(form); versions.save(version);
        return versionView(version);
    }

    @Transactional
    public Map<String,Object> publish(UUID formId,UUID versionId) {
        currentUser.requireAdmin(); FormVersion version=version(formId,versionId); requireDraft(version);
        validateVersion(version); version.setStatus(FormStatus.PUBLISHED); version.setPublishedAt(LocalDateTime.now());
        return versionView(versions.save(version));
    }

    @Transactional
    public Map<String,Object> createNextVersion(UUID formId) {
        currentUser.requireAdmin(); RequestForm form=form(formId);
        Optional<FormVersion> existing=versions.findByFormIdAndStatus(formId,FormStatus.DRAFT);
        if(existing.isPresent()) return versionView(existing.get());
        List<FormVersion> all=versions.findByFormIdOrderByVersionNumberDesc(formId);
        FormVersion source=all.stream().filter(v->v.getStatus()==FormStatus.PUBLISHED).findFirst()
                .orElseThrow(()->new IllegalStateException("Publish the first form version before creating another version"));
        int number=all.stream().mapToInt(FormVersion::getVersionNumber).max().orElse(0)+1;
        FormVersion draft=versions.save(FormVersion.builder().form(form).versionNumber(number)
                .instruction(source.getInstruction()).submissionMode(source.getSubmissionMode())
                .recordRecipientFieldKey(source.getRecordRecipientFieldKey()).maxBatchRows(source.getMaxBatchRows()).build());
        for(FormField old:fields.findByFormVersionIdOrderByDisplayOrderAsc(source.getId()))
            fields.save(FormField.builder().formVersion(draft).fieldKey(old.getFieldKey()).label(old.getLabel())
                    .type(old.getType()).required(old.isRequired()).placeholder(old.getPlaceholder())
                    .configurationJson(old.getConfigurationJson()).displayOrder(old.getDisplayOrder()).build());
        return versionView(draft);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> versionViewById(UUID id) {
        return versionView(versions.findById(id).orElseThrow(()->new ResourceNotFoundException("FormVersion","id",id)));
    }

    public List<com.company.workflowbuilder.dto.response.CustomFieldResponse> fieldResponses(FormVersion version) {
        return fields.findByFormVersionIdOrderByDisplayOrderAsc(version.getId()).stream().map(this::fieldResponse).toList();
    }

    private void replaceFields(FormVersion version,Object raw) {
        if(!(raw instanceof Collection<?> rows)) return;
        fields.deleteByFormVersionId(version.getId());
        int order=0; Set<String> keys=new HashSet<>();
        for(Object item:rows) {
            Map<String,Object> row=mapper.convertValue(item,new TypeReference<>(){});
            String key=required(row,"fieldKey"); if(!keys.add(key)) throw new IllegalArgumentException("Duplicate fieldKey: "+key);
            FieldType type=FieldType.valueOf(required(row,"type").toUpperCase(Locale.ROOT));
            Map<String,Object> config=new LinkedHashMap<>();
            if(row.get("options")!=null) config.put("options",row.get("options"));
            if(Boolean.TRUE.equals(row.get("allowMultiple"))) config.put("allowMultiple",true);
            fields.save(FormField.builder().formVersion(version).fieldKey(key).label(required(row,"label"))
                    .type(type).required(Boolean.TRUE.equals(row.get("required"))).placeholder(text(row.get("placeholder")))
                    .configurationJson(write(config)).displayOrder(order++).build());
        }
    }

    private void validateVersion(FormVersion version) {
        List<FormField> definitions=fields.findByFormVersionIdOrderByDisplayOrderAsc(version.getId());
        if("BATCH".equals(version.getSubmissionMode())&&definitions.isEmpty()) throw new IllegalArgumentException("Batch form requires at least one field");
        if("BATCH".equals(version.getSubmissionMode())&&definitions.stream().anyMatch(f->f.getType()==FieldType.FILE))
            throw new IllegalArgumentException("Batch form does not support FILE fields");
        String recipient=version.getRecordRecipientFieldKey();
        if(recipient!=null&&definitions.stream().noneMatch(f->f.getFieldKey().equals(recipient)&&f.getType()==FieldType.TEXT))
            throw new IllegalArgumentException("recordRecipientFieldKey must reference a TEXT field");
    }

    private Map<String,Object> view(RequestForm form) {
        List<FormVersion> all=versions.findByFormIdOrderByVersionNumberDesc(form.getId());
        Map<String,Object> out=new LinkedHashMap<>(); out.put("id",form.getId());out.put("name",form.getName());
        out.put("description",form.getDescription());out.put("archived",form.isArchived());
        out.put("versions",all.stream().map(this::versionSummary).toList());
        all.stream().filter(v->v.getStatus()==FormStatus.PUBLISHED).findFirst().ifPresent(v->out.put("latestPublishedVersion",versionSummary(v)));
        all.stream().filter(v->v.getStatus()==FormStatus.DRAFT).findFirst().ifPresent(v->out.put("draftVersion",versionSummary(v)));
        return out;
    }

    private Map<String,Object> versionView(FormVersion v) {
        Map<String,Object> out=new LinkedHashMap<>(versionSummary(v)); out.put("formName",v.getForm().getName());
        out.put("description",v.getForm().getDescription());out.put("instruction",v.getInstruction());
        out.put("submissionMode",v.getSubmissionMode());out.put("recordRecipientFieldKey",v.getRecordRecipientFieldKey());
        out.put("maxBatchRows",v.getMaxBatchRows());out.put("fields",fieldResponses(v));return out;
    }
    private Map<String,Object> versionSummary(FormVersion v) {Map<String,Object> x=new LinkedHashMap<>();x.put("id",v.getId());x.put("formId",v.getForm().getId());x.put("versionNumber",v.getVersionNumber());x.put("status",v.getStatus());x.put("publishedAt",v.getPublishedAt());return x;}
    private com.company.workflowbuilder.dto.response.CustomFieldResponse fieldResponse(FormField f) {Map<String,Object> c=json(f.getConfigurationJson());return com.company.workflowbuilder.dto.response.CustomFieldResponse.builder().id(f.getId()).fieldKey(f.getFieldKey()).label(f.getLabel()).type(f.getType()).required(f.isRequired()).placeholder(f.getPlaceholder()).displayOrder(f.getDisplayOrder()).options(mapper.convertValue(c.getOrDefault("options",List.of()),new TypeReference<>(){})).allowMultiple(Boolean.TRUE.equals(c.get("allowMultiple"))).build();}
    private RequestForm form(UUID id){return forms.findById(id).orElseThrow(()->new ResourceNotFoundException("Form","id",id));}
    private FormVersion version(UUID formId,UUID id){FormVersion v=versions.findById(id).orElseThrow(()->new ResourceNotFoundException("FormVersion","id",id));if(!v.getForm().getId().equals(formId))throw new IllegalArgumentException("Form version does not belong to form");return v;}
    private void requireDraft(FormVersion v){if(v.getStatus()!=FormStatus.DRAFT)throw new IllegalStateException("Published form versions are immutable");}
    private String required(Map<String,Object>b,String k){String v=text(b.get(k));if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v.trim();}
    private String text(Object v){return v==null?null:String.valueOf(v).trim();} private String blankToNull(Object v){String x=text(v);return x==null||x.isBlank()?null:x;}
    private int integer(Object v,int d){try{return v==null?d:Integer.parseInt(String.valueOf(v));}catch(Exception e){return d;}}
    private String write(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("Invalid form JSON",e);}}
    private Map<String,Object> json(String v){try{return mapper.readValue(v,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
}
