package com.workflow_worker.demo.executers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;

import java.sql.*;
import java.util.*;

@PluginExecutor("DATABASE_QUERY")
public class DatabaseExecutor implements WorkflowPlugin {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "Database Query Executor";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<String, String> getInputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("jdbcUrl", "Database connection URL, e.g. jdbc:postgresql://localhost:5432/db (required)");
        schema.put("username", "Database username (required)");
        schema.put("password", "Database password (required)");
        schema.put("sql", "SQL query or update statement to execute (required)");
        schema.put("queryType", "Execution type: QUERY or UPDATE (default: QUERY)");
        return schema;
    }

    @Override
    public Map<String, String> getOutputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("result", "JSON formatted query result rows or affected rows count");
        return schema;
    }

    @Override
    public void validate(StepDefinition step) throws Exception {
        Map<String, Object> config = step.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("DatabaseExecutor missing configuration");
        }
        if (config.get("jdbcUrl") == null || String.valueOf(config.get("jdbcUrl")).isBlank()) {
            throw new IllegalArgumentException("DatabaseExecutor missing 'jdbcUrl' configuration");
        }
        if (config.get("username") == null || String.valueOf(config.get("username")).isBlank()) {
            throw new IllegalArgumentException("DatabaseExecutor missing 'username' configuration");
        }
        if (config.get("password") == null || String.valueOf(config.get("password")).isBlank()) {
            throw new IllegalArgumentException("DatabaseExecutor missing 'password' configuration");
        }
        if (config.get("sql") == null || String.valueOf(config.get("sql")).isBlank()) {
            throw new IllegalArgumentException("DatabaseExecutor missing 'sql' configuration");
        }
    }

    @Override
    public String execute(StepDefinition step, String payload) throws Exception {
        Map<String, Object> config = step.getConfig();
        String jdbcUrl = String.valueOf(config.get("jdbcUrl")).trim();
        String username = String.valueOf(config.get("username")).trim();
        String password = String.valueOf(config.get("password"));
        String sql = String.valueOf(config.get("sql")).trim();
        String queryType = config.get("queryType") == null ? "QUERY" : String.valueOf(config.get("queryType")).trim().toUpperCase();

        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection(jdbcUrl, username, password);
            stmt = conn.createStatement();

            if ("UPDATE".equals(queryType)) {
                int affectedRows = stmt.executeUpdate(sql);
                Map<String, Object> result = new HashMap<>();
                result.put("affectedRows", affectedRows);
                result.put("status", "SUCCESS");
                return objectMapper.writeValueAsString(result);
            } else {
                ResultSet rs = stmt.executeQuery(sql);
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object columnValue = rs.getObject(i);
                        row.put(columnName, columnValue);
                    }
                    rows.add(row);
                }

                rs.close();
                return objectMapper.writeValueAsString(rows);
            }
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException ignored) {}
            }
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }
}
