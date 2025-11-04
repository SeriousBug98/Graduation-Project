package com.example.dbids.sqlite;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.*;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@ConditionalOnBean(name = "sqliteDataSource")
@ConditionalOnProperty(name = "app.sqlite.init-enabled", havingValue = "true", matchIfMissing = true)
public class SqliteSchemaInit {
    private final DataSource sqlite;

    public SqliteSchemaInit(@Qualifier("sqliteDataSource") DataSource sqlite) {
        this.sqlite = sqlite;
    }

    @PostConstruct
    public void init() {
        ResourceDatabasePopulator pop = new ResourceDatabasePopulator(
                new ClassPathResource("schema-sqlite.sql"));
        DatabasePopulatorUtils.execute(pop, sqlite);
    }
}
