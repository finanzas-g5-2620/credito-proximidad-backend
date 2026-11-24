package pe.upc.credito.infrastructure.bootstrap;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@ConditionalOnExpression("""
        ${app.demo-data.enabled:false} && ${app.demo-data.scenarios:false} && !${app.demo-clock.enabled:false}
        """)
public class DemoScenarioSeeder implements ApplicationRunner {
    private final DataSource dataSource;

    public DemoScenarioSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        new ResourceDatabasePopulator(
                new ClassPathResource("demo/sales-history.sql"),
                new ClassPathResource("demo/collection-and-catalog.sql")
        ).execute(dataSource);
    }
}
