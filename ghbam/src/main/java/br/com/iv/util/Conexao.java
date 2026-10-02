package br.com.iv.util;

import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Conexao implements Serializable, Closeable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(Conexao.class);
    
    // Cache de DataSources com TTL
    private static final Map<String, CachedDataSource> DATA_SOURCE_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int MAX_CACHE_SIZE = 20;
    
    // Monitoramento de conexões ativas
    private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger(0);
    private static final int MAX_ACTIVE_CONNECTIONS = 50;
    
    // Configurações de timeout
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int SOCKET_TIMEOUT_MS = 180000; // 3 minutos para relatórios pesados
    private static final int VALIDATION_TIMEOUT_MS = 5000;
    private static final int STATEMENT_TIMEOUT_MS = 180000; // 3 minutos
    
    private final Connection connection;
    private final String dataSourceKey;
    private final long creationTime;
    
    // Classe interna para cache com timestamp
    private static class CachedDataSource {
        final DataSource dataSource;
        final long creationTime;
        final AtomicInteger usageCount;
        
        CachedDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            this.creationTime = System.currentTimeMillis();
            this.usageCount = new AtomicInteger(0);
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - creationTime) > CACHE_TTL_MS;
        }
        
        int incrementAndGetUsage() {
            return usageCount.incrementAndGet();
        }
        
        int getUsageCount() {
            return usageCount.get();
        }
    }
    
    private Conexao(Connection connection, String dataSourceKey) {
        this.connection = connection;
        this.dataSourceKey = dataSourceKey;
        this.creationTime = System.currentTimeMillis();
        ACTIVE_CONNECTIONS.incrementAndGet();
        LOG.debugf("Conexão criada. Ativas: %d", ACTIVE_CONNECTIONS.get());
    }
    
    public static Conexao getConexao(String xurlconexao, String xusuarioconexao, String xsenhaconexao) 
            throws SQLException {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Verifica limite de conexões ativas
            if (ACTIVE_CONNECTIONS.get() >= MAX_ACTIVE_CONNECTIONS) {
                LOG.warnf("Limite de conexões ativas atingido: %d", MAX_ACTIVE_CONNECTIONS);
                throw new SQLException("Limite máximo de conexões simultâneas atingido");
            }
            
            String cacheKey = generateCacheKey(xurlconexao, xusuarioconexao);
            
            // Obtém ou cria DataSource do cache
            DataSource dataSource = getOrCreateDataSource(cacheKey, xurlconexao, xusuarioconexao, xsenhaconexao);
            
            // Obtém conexão com timeout
            Connection conn = dataSource.getConnection();
            
            // Configurações otimizadas da conexão
            configureConnection(conn);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            LOG.debugf("Conexão obtida em %d ms para: %s", elapsedTime, getDatabaseName(xurlconexao));
            
            return new Conexao(conn, cacheKey);
            
        } catch (SQLException e) {
            LOG.errorf(e, "Erro ao obter conexão para: %s", getDatabaseName(xurlconexao));
            throw e;
        }
    }
    
    private static DataSource getOrCreateDataSource(String cacheKey, String url, String user, String password) 
            throws SQLException {
        
        // Limpa cache se necessário
        cleanupCacheIfNeeded();
        
        CachedDataSource cached = DATA_SOURCE_CACHE.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            cached.incrementAndGetUsage();
            LOG.debugf("DataSource reutilizado do cache: %s (uso: %d)", 
                      cacheKey, cached.getUsageCount());
            return cached.dataSource;
        }
        
        // Cria novo DataSource otimizado
        LOG.infof("Criando novo DataSource para: %s", cacheKey);
        DataSource newDataSource = createHikariDataSource(url, user, password);
        
        cached = new CachedDataSource(newDataSource);
        cached.incrementAndGetUsage();
        DATA_SOURCE_CACHE.put(cacheKey, cached);
        
        LOG.debugf("DataSource criado e cacheado. Cache size: %d", DATA_SOURCE_CACHE.size());
        return newDataSource;
    }
    
    private static void cleanupCacheIfNeeded() {
        // Limpa cache se muito grande
        if (DATA_SOURCE_CACHE.size() > MAX_CACHE_SIZE) {
            DATA_SOURCE_CACHE.entrySet().removeIf(entry -> 
                entry.getValue().isExpired() || 
                entry.getValue().getUsageCount() == 0
            );
            LOG.debugf("Cache limpo. Novo tamanho: %d", DATA_SOURCE_CACHE.size());
        }
    }
    
    private static DataSource createHikariDataSource(String url, String user, String password) 
            throws SQLException {
        
        try {
            // Tenta usar HikariCP se disponível
            Class<?> hikariConfigClass = Class.forName("com.zaxxer.hikari.HikariConfig");
            Class<?> hikariDataSourceClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            
            Object config = hikariConfigClass.getDeclaredConstructor().newInstance();
            
            // Configurações usando reflection
            hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(config, url);
            hikariConfigClass.getMethod("setUsername", String.class).invoke(config, user);
            hikariConfigClass.getMethod("setPassword", String.class).invoke(config, password);
            
            // Pool otimizado
            hikariConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(config, 10);
            hikariConfigClass.getMethod("setMinimumIdle", int.class).invoke(config, 2);
            hikariConfigClass.getMethod("setConnectionTimeout", long.class).invoke(config, CONNECTION_TIMEOUT_MS);
            hikariConfigClass.getMethod("setIdleTimeout", long.class).invoke(config, 600000L); // 10 minutos
            hikariConfigClass.getMethod("setMaxLifetime", long.class).invoke(config, 1800000L); // 30 minutos
            hikariConfigClass.getMethod("setLeakDetectionThreshold", long.class).invoke(config, 60000L);
            hikariConfigClass.getMethod("setValidationTimeout", long.class).invoke(config, VALIDATION_TIMEOUT_MS);
            
            // Otimizações para PostgreSQL
            hikariConfigClass.getMethod("setConnectionTestQuery", String.class)
                .invoke(config, "SELECT 1");
            
            hikariConfigClass.getMethod("addDataSourceProperty", String.class, String.class)
                .invoke(config, "socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
            
            // Cria DataSource
            return (DataSource) hikariDataSourceClass
                .getDeclaredConstructor(hikariConfigClass)
                .newInstance(config);
            
        } catch (Exception e) {
            // Fallback para DriverManager se HikariCP não estiver disponível
            LOG.warn("HikariCP não encontrado, usando DriverManager como fallback");
            return createFallbackDataSource(url, user, password);
        }
    }
    
    private static DataSource createFallbackDataSource(String url, String user, String password) {
        return new SimpleDataSource(url, user, password);
    }
    
    private static void configureConnection(Connection conn) throws SQLException {
        if (conn != null) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // Otimizações específicas para PostgreSQL
            try (Statement stmt = conn.createStatement()) {
                // Timeout de 3 minutos para relatórios pesados
                stmt.execute("SET statement_timeout = " + STATEMENT_TIMEOUT_MS);
                stmt.execute("SET idle_in_transaction_session_timeout = 180000");

                // Fetch size otimizado
                stmt.setFetchSize(100);

            } catch (SQLException e) {
                LOG.warnf("Não foi possível configurar otimizações da conexão: %s", e.getMessage());
            }
        }
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public Statement createStatement() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Conexão está fechada");
        }
        Statement stmt = connection.createStatement();
        stmt.setFetchSize(50); // Fetch size otimizado
        return stmt;
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    // Rollback se houver transação pendente
                    try {
                        connection.rollback();
                    } catch (SQLException e) {
                        // Ignora erro de rollback
                    }
                    
                    // Fecha a conexão
                    connection.close();
                    LOG.debugf("Conexão fechada para: %s", dataSourceKey);
                }
            } catch (SQLException e) {
                LOG.warnf("Erro ao fechar conexão: %s", e.getMessage());
            } finally {
                ACTIVE_CONNECTIONS.decrementAndGet();
                LOG.debugf("Conexão finalizada. Ativas: %d", ACTIVE_CONNECTIONS.get());
            }
        }
    }
    
    // Métodos de utilidade
    private static String generateCacheKey(String url, String user) {
        return url + "||" + user;
    }
    
    private static String getDatabaseName(String url) {
        try {
            // Extrai nome do banco da URL
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash != -1) {
                String dbPart = url.substring(lastSlash + 1);
                int questionMark = dbPart.indexOf('?');
                return questionMark != -1 ? dbPart.substring(0, questionMark) : dbPart;
            }
        } catch (Exception e) {
            // Ignora erro
        }
        return url;
    }
    
    // Método para fechar todas as conexões (para shutdown)
    public static void shutdown() {
        LOG.info("Iniciando shutdown das conexões...");
        
        // Fecha todos os DataSources do cache
        for (CachedDataSource cached : DATA_SOURCE_CACHE.values()) {
            try {
                if (cached.dataSource instanceof AutoCloseable) {
                    ((AutoCloseable) cached.dataSource).close();
                }
            } catch (Exception e) {
                LOG.warnf("Erro ao fechar DataSource: %s", e.getMessage());
            }
        }
        
        DATA_SOURCE_CACHE.clear();
        LOG.info("Shutdown das conexões completado");
    }
    
    // Métodos para monitoramento
    public static int getActiveConnectionsCount() {
        return ACTIVE_CONNECTIONS.get();
    }
    
    public static int getCacheSize() {
        return DATA_SOURCE_CACHE.size();
    }
    
    public static Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("activeConnections", ACTIVE_CONNECTIONS.get());
        stats.put("cacheSize", DATA_SOURCE_CACHE.size());
        stats.put("maxActiveConnections", MAX_ACTIVE_CONNECTIONS);
        
        // Estatísticas por DataSource
        Map<String, Object> dataSourceStats = new java.util.HashMap<>();
        for (Map.Entry<String, CachedDataSource> entry : DATA_SOURCE_CACHE.entrySet()) {
            Map<String, Object> dsInfo = new java.util.HashMap<>();
            dsInfo.put("usageCount", entry.getValue().getUsageCount());
            dsInfo.put("ageMinutes", 
                TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - entry.getValue().creationTime));
            dataSourceStats.put(getDatabaseName(entry.getKey()), dsInfo);
        }
        stats.put("dataSources", dataSourceStats);
        
        return stats;
    }
    
    // DataSource simples como fallback
    private static class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;
        
        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }
        
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        
        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }
        
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }
        
        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            throw new java.sql.SQLFeatureNotSupportedException();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap não suportado");
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}