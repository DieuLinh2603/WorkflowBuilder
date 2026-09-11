package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.CurrentUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;

@Service @RequiredArgsConstructor
public class DataConnectorService {
    private final DataConnectorRepository connectors;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CredentialCipherService cipher;
    private final ObjectMapper mapper;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() {
        List<DataConnector> rows = currentUser.hasRole(com.company.workflowbuilder.entity.user.SystemRole.ADMIN)
                ? connectors.findAll() : connectors.findAccessible(currentUser.id());
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public Map<String,Object> save(UUID id, Map<String,Object> body) {
        currentUser.requireAdmin();
        DataConnector row = id == null ? new DataConnector() : get(id);
        row.setName(required(body, "name"));
        row.setDescription(Objects.toString(body.get("description"), "").trim());
        row.setConnectorType(required(body, "connectorType").toUpperCase(Locale.ROOT));
        if (!Set.of("REST", "POSTGRESQL").contains(row.getConnectorType())) throw new IllegalArgumentException("connectorType must be REST or POSTGRESQL");
        row.setConfigJson(write(body.getOrDefault("config", Map.of())));
        if (body.containsKey("credentials")) {
            Map<String,Object> merged = row.getEncryptedCredentials() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials(row));
            Map<String,Object> incoming = mapper.convertValue(body.get("credentials"), new TypeReference<>(){});
            incoming.forEach((key,value) -> { if (value != null && !String.valueOf(value).isBlank()) merged.put(key,value); });
            row.setEncryptedCredentials(cipher.encrypt(write(merged)));
        }
        row.setActive(!Boolean.FALSE.equals(body.get("active")));
        if (row.getCreatedBy() == null) row.setCreatedBy(currentUser.user());
        if (body.get("grantedUserIds") instanceof Collection<?> ids) {
            Set<User> granted = new HashSet<>();
            for (Object value : ids) granted.add(users.findById(UUID.fromString(String.valueOf(value)))
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", value)));
            row.setGrantedUsers(granted);
        }
        validateConfig(row);
        return view(connectors.save(row));
    }

    @Transactional public void delete(UUID id) { currentUser.requireAdmin(); connectors.delete(get(id)); }

    @Transactional(readOnly=true)
    public Map<String,Object> test(UUID id) {
        DataConnector row = accessible(id);
        try {
            if ("REST".equals(row.getConnectorType())) {
                Map<String,Object> config = json(row.getConfigJson()); validateRestUrl(String.valueOf(config.get("baseUrl")), config);
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(String.valueOf(config.get("baseUrl"))))
                        .timeout(Duration.ofSeconds(10)).method("GET", HttpRequest.BodyPublishers.noBody());
                applyAuth(request, credentials(row));
                int status = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 400) throw new IllegalStateException("HTTP " + status);
            } else {
                Map<String,Object> config = json(row.getConfigJson()), credentials = credentials(row);
                try (Connection connection = DriverManager.getConnection(String.valueOf(config.get("jdbcUrl")), String.valueOf(credentials.get("username")), String.valueOf(credentials.get("password")))) {
                    connection.setReadOnly(true); if (!connection.isValid(5)) throw new IllegalStateException("Connection is not valid");
                }
            }
            return Map.of("success", true, "message", "Connection successful");
        } catch (Exception ex) { throw new IllegalArgumentException("Connection failed: " + ex.getMessage()); }
    }

    public DataConnector accessible(UUID id) {
        DataConnector row = get(id);
        if (!row.isActive()) throw new IllegalStateException("Connector is disabled");
        if (!currentUser.hasRole(com.company.workflowbuilder.entity.user.SystemRole.ADMIN)
                && !row.getCreatedBy().getId().equals(currentUser.id())
                && row.getGrantedUsers().stream().noneMatch(u -> u.getId().equals(currentUser.id()))) throw new AccessDeniedException("Connector access was not granted");
        return row;
    }
    public Map<String,Object> credentials(DataConnector row) { return json(cipher.decrypt(row.getEncryptedCredentials())); }

    private DataConnector get(UUID id) { return connectors.findById(id).orElseThrow(() -> new ResourceNotFoundException("DataConnector", "id", id)); }
    private void validateConfig(DataConnector row) {
        Map<String,Object> config = json(row.getConfigJson());
        if ("REST".equals(row.getConnectorType())) {
            validateRestUrl(String.valueOf(config.get("baseUrl")), config);
            Map<String,Object> secret = credentials(row);
            String authType = Objects.toString(secret.getOrDefault("authType", "NONE")).toUpperCase(Locale.ROOT);
            if (!Set.of("NONE", "BEARER", "API_KEY").contains(authType))
                throw new IllegalArgumentException("REST authType must be NONE, BEARER or API_KEY");
            if ("BEARER".equals(authType) && Objects.toString(secret.get("token"), "").isBlank())
                throw new IllegalArgumentException("Bearer token is required");
            if ("API_KEY".equals(authType) && (Objects.toString(secret.get("apiKey"), "").isBlank()
                    || Objects.toString(secret.get("headerName"), "").isBlank()))
                throw new IllegalArgumentException("API key and header name are required");
            if ("API_KEY".equals(authType) && Set.of("host", "content-length")
                    .contains(Objects.toString(secret.get("headerName"), "").toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("API key header name is not allowed");
        }
        else if (!String.valueOf(config.get("jdbcUrl")).startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("Only jdbc:postgresql URLs are supported");
    }
    private void validateRestUrl(String raw, Map<String,Object> config) {
        try {
            URI uri = URI.create(raw); if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("REST baseUrl must be http(s) without query or fragment");
            Set<String> allowed = new HashSet<>(); Object value=config.get("allowedHosts"); if(value instanceof Collection<?> c) c.forEach(x->allowed.add(String.valueOf(x).toLowerCase()));
            if (!allowed.isEmpty() && !allowed.contains(uri.getHost().toLowerCase())) throw new IllegalArgumentException("REST host is not in allowedHosts");
            InetAddress address = InetAddress.getByName(uri.getHost());
            if ((address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) && !allowed.contains(uri.getHost().toLowerCase()))
                throw new IllegalArgumentException("Private REST address requires an explicit allowedHosts entry");
        } catch (UnknownHostException | IllegalArgumentException ex) { throw new IllegalArgumentException("Unsafe REST baseUrl: " + ex.getMessage()); }
    }
    private void applyAuth(HttpRequest.Builder request, Map<String,Object> credentials) {
        String type=String.valueOf(credentials.getOrDefault("authType", "NONE"));
        if ("BEARER".equalsIgnoreCase(type)) request.header("Authorization", "Bearer " + credentials.get("token"));
        if ("API_KEY".equalsIgnoreCase(type)) request.header(String.valueOf(credentials.getOrDefault("headerName", "X-API-Key")), String.valueOf(credentials.get("apiKey")));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> viewById(UUID id) { return view(accessible(id)); }
    private Map<String,Object> view(DataConnector row) { Map<String,Object> out=new LinkedHashMap<>(); out.put("id",row.getId());out.put("name",row.getName());out.put("description",row.getDescription());out.put("connectorType",row.getConnectorType());out.put("config",json(row.getConfigJson()));out.put("active",row.isActive());out.put("credentialConfigured",row.getEncryptedCredentials()!=null);if("REST".equals(row.getConnectorType())){Map<String,Object> secret=credentials(row);out.put("authType",secret.getOrDefault("authType","NONE"));out.put("apiKeyHeader",secret.getOrDefault("headerName","X-API-Key"));}out.put("grantedUserIds",row.getGrantedUsers().stream().map(User::getId).toList());return out; }
    private String required(Map<String,Object> body,String key){String v=String.valueOf(body.getOrDefault(key,"")).trim();if(v.isEmpty())throw new IllegalArgumentException(key+" is required");return v;}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Invalid JSON",ex);}}
    private Map<String,Object> json(String value){try{return mapper.readValue(value,new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("Stored connector JSON is invalid",ex);}}
}
