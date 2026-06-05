package com.zrlog.plugin.sitecheck.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.type.ActionType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class SiteCheckDatabase {

    private static volatile boolean configured;
    private static DataSourceWrapperImpl dataSource;
    private static Properties dataSourceProperties;

    private SiteCheckDatabase() {
    }

    public static void ensureConfigured(IOSession session) {
        if (configured) {
            return;
        }
        synchronized (SiteCheckDatabase.class) {
            if (configured) {
                return;
            }
            Map response = session.getResponseSync(ContentType.JSON, new HashMap<>(), ActionType.GET_DB_PROPERTIES, Map.class);
            String path = response == null ? "" : Objects.toString(response.get("dbProperties"), "");
            if (path.trim().isEmpty()) {
                throw new IllegalStateException("db.properties path is empty");
            }
            File dbPropertiesFile = new File(path);
            if (!dbPropertiesFile.exists()) {
                throw new IllegalStateException("db.properties not found: " + path);
            }
            Properties properties = loadProperties(dbPropertiesFile);
            DataSourceWrapperImpl wrapper = new DataSourceWrapperImpl(properties, false);
            if (!wrapper.isWebApi()) {
                String driverClass = properties.getProperty("driverClass");
                if (driverClass != null && !driverClass.trim().isEmpty()) {
                    wrapper.setDriverClassName(driverClass);
                }
                wrapper.setJdbcUrl(properties.getProperty("jdbcUrl"));
            }
            wrapper.setUsername(properties.getProperty("user"));
            wrapper.setPassword(properties.getProperty("password"));
            DAO.setDs(wrapper);
            dataSource = wrapper;
            dataSourceProperties = properties;
            configured = true;
        }
    }

    public static DataSourceWrapperImpl dataSource(IOSession session) {
        ensureConfigured(session);
        return dataSource;
    }

    public static Properties properties(IOSession session) {
        ensureConfigured(session);
        return dataSourceProperties;
    }

    public static List<Map<String, Object>> queryList(IOSession session, String sql, Object... params)
            throws SQLException {
        ensureConfigured(session);
        return new DAO(dataSource).queryListWithParams(sql, params);
    }

    public static Object queryFirstObj(IOSession session, String sql, Object... params) throws SQLException {
        ensureConfigured(session);
        return new DAO(dataSource).queryFirstObj(sql, params);
    }

    public static void execute(IOSession session, String sql) throws SQLException {
        ensureConfigured(session);
        new DAO(dataSource).execute(sql);
    }

    public static void executeStatement(IOSession session, String sql) throws SQLException {
        ensureConfigured(session);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Properties loadProperties(File dbPropertiesFile) {
        Properties dbProperties = new Properties();
        try (FileInputStream in = new FileInputStream(dbPropertiesFile)) {
            dbProperties.load(in);
            return dbProperties;
        } catch (IOException e) {
            throw new IllegalStateException("read db.properties failed: " + dbPropertiesFile, e);
        }
    }
}
