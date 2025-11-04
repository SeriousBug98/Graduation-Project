package com.example.dbids.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class MultiDataSourceConfig {

    // --- MySQL (기본) ---
//    @Bean
//    @ConfigurationProperties("spring.datasource.mysql")
//    public DataSource mysqlDataSource() { return DataSourceBuilder.create().build(); }
//
//    @Bean
//    public LocalContainerEntityManagerFactoryBean mysqlEmf(
//            @Qualifier("mysqlDataSource") DataSource ds) {
//        var emf = new LocalContainerEntityManagerFactoryBean();
//        emf.setDataSource(ds);
//        emf.setPackagesToScan("com.example.dbids.model"); // (대상 DB 엔티티 쓰는 경우)
//        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
//        emf.setPersistenceUnitName("mysqlUnit");
//        return emf;
//    }
//
//    @Bean
//    public PlatformTransactionManager mysqlTx(
//            @Qualifier("mysqlEmf") LocalContainerEntityManagerFactoryBean emf) {
//        return new JpaTransactionManager(emf.getObject());
//    }

    // --- SQLite (내부 저장) ---
    @Bean
    @ConfigurationProperties("spring.datasource.sqlite")
    public DataSource sqliteDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    public LocalContainerEntityManagerFactoryBean sqliteEmf(
            @Qualifier("sqliteDataSource") DataSource ds) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(ds);
        emf.setPackagesToScan("com.example.dbids.sqlite.model");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setPersistenceUnitName("sqliteUnit");

        // Hibernate 속성 (Dialect 등)
        var props = new java.util.HashMap<String, Object>();
        props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        props.put("hibernate.hbm2ddl.auto", "none"); // 우리가 schema-sqlite.sql로 초기화
        // 옵션: SQL 로깅
        // props.put("hibernate.show_sql", "true");
        emf.setJpaPropertyMap(props);

        return emf;
    }

    @Bean(name = {"sqliteTx", "transactionManager"})
    public PlatformTransactionManager sqliteTx(
            @Qualifier("sqliteEmf") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    // SQLite 외래키 강제 (옵션)
    @Bean
    HibernatePropertiesCustomizer sqlitePragmaCustomizer(
            @Qualifier("sqliteEmf") LocalContainerEntityManagerFactoryBean emf) {
        return (props) -> props.put("hibernate.jdbc.lob.non_contextual_creation", true);
    }
}

@Configuration
@EnableJpaRepositories(
        basePackages = "com.example.dbids.sqlite.repository",
        entityManagerFactoryRef = "sqliteEmf",
        transactionManagerRef = "sqliteTx"
)
class SqliteRepoConfig {}

//@Configuration
//@EnableJpaRepositories(
//        basePackages = "com.example.dbids.repository",
//        entityManagerFactoryRef = "mysqlEmf",
//        transactionManagerRef = "mysqlTx"
//)
//class MysqlRepoConfig {}
