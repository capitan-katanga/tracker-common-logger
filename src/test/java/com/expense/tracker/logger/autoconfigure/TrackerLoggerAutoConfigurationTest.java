package com.expense.tracker.logger.autoconfigure;

import com.expense.tracker.logger.client.LoggingClientInterceptor;
import com.expense.tracker.logger.config.TrackerLoggerProperties;
import com.expense.tracker.logger.otel.OpenTelemetryAppenderInstaller;
import com.expense.tracker.logger.task.MdcTaskDecorator;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TrackerLoggerAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TrackerLoggerAutoConfiguration.class));

    @Test
    @DisplayName("en una app servlet registra filtros, customizer, métricas, decorator y el appender de OTel")
    void registersAllBeans() {
        webRunner.run(context -> assertAll(
                () -> assertThat(context.containsBean("trackerHttpLoggingFilter"), is(true)),
                () -> assertThat(context.containsBean("trackerUserIdMdcFilter"), is(true)),
                () -> assertThat(context.getBean("trackerHttpLoggingFilter", FilterRegistrationBean.class).getOrder(),
                        equalTo(TrackerLoggerAutoConfiguration.HTTP_LOGGING_FILTER_ORDER)),
                () -> assertThat(context.getBean("trackerUserIdMdcFilter", FilterRegistrationBean.class).getOrder(),
                        equalTo(TrackerLoggerAutoConfiguration.USER_ID_FILTER_ORDER)),
                () -> assertThat(context.getBeansOfType(RestClientCustomizer.class).size(), equalTo(1)),
                () -> assertThat(context.getBeansOfType(ProcessorMetrics.class).size(), equalTo(1)),
                () -> assertThat(context.getBean(TaskDecorator.class), instanceOf(MdcTaskDecorator.class)),
                () -> assertThat(context.getBeansOfType(OpenTelemetryAppenderInstaller.class).size(), equalTo(1))));
    }

    @Test
    @DisplayName("bindea los defaults de tracker.logger.* y acepta overrides")
    void bindsProperties() {
        webRunner.run(context -> {
            var properties = context.getBean(TrackerLoggerProperties.class);
            assertAll(
                    () -> assertThat(properties.maxBodyLength(), equalTo(4096)),
                    () -> assertThat(properties.maskedHeaders(), hasItem("Authorization")),
                    () -> assertThat(properties.maskedBodyFields(), hasItem("password")),
                    () -> assertThat(properties.http().server().excludedPaths(), hasItem("/actuator/**")),
                    () -> assertThat(properties.http().client().enabled(), is(true)));
        });
        webRunner.withPropertyValues("tracker.logger.max-body-length=10", "tracker.logger.masked-headers=X-Secret")
                .run(context -> assertAll(
                        () -> assertThat(context.getBean(TrackerLoggerProperties.class).maxBodyLength(), equalTo(10)),
                        () -> assertThat(context.getBean(TrackerLoggerProperties.class).maskedHeaders(), contains("X-Secret"))));
    }

    @Test
    @DisplayName("el customizer agrega el interceptor de logueo al RestClient.Builder")
    void customizerAddsInterceptor() {
        webRunner.run(context -> {
            var builder = RestClient.builder();
            context.getBean(RestClientCustomizer.class).customize(builder);
            var interceptors = new AtomicReference<Object>();
            builder.requestInterceptors(list -> interceptors.set(list.getFirst()));

            assertThat(interceptors.get(), instanceOf(LoggingClientInterceptor.class));
        });
    }

    @Test
    @DisplayName("se pueden apagar el logueo server y el client por properties")
    void disablesByProperty() {
        webRunner.withPropertyValues("tracker.logger.http.server.enabled=false", "tracker.logger.http.client.enabled=false")
                .run(context -> assertAll(
                        () -> assertThat(context.containsBean("trackerHttpLoggingFilter"), is(false)),
                        () -> assertThat(context.containsBean("trackerUserIdMdcFilter"), is(false)),
                        () -> assertThat(context.getBeansOfType(RestClientCustomizer.class).isEmpty(), is(true))));
    }

    @Test
    @DisplayName("sin Spring Security no registra el filtro de userId")
    void skipsUserIdFilterWithoutSecurity() {
        webRunner.withClassLoader(new FilteredClassLoader(SecurityContextHolder.class))
                .run(context -> assertAll(
                        () -> assertThat(context.containsBean("trackerHttpLoggingFilter"), is(true)),
                        () -> assertThat(context.containsBean("trackerUserIdMdcFilter"), is(false))));
    }

    @Test
    @DisplayName("fuera de una app servlet no registra filtros")
    void skipsFiltersOutsideServlet() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TrackerLoggerAutoConfiguration.class))
                .run(context -> assertThat(context.containsBean("trackerHttpLoggingFilter"), is(false)));
    }

    @Test
    @DisplayName("respeta un TaskDecorator propio del consumidor")
    void backsOffCustomTaskDecorator() {
        TaskDecorator custom = runnable -> runnable;
        webRunner.withBean(TaskDecorator.class, () -> custom)
                .run(context -> assertThat(context.getBean(TaskDecorator.class), equalTo(custom)));
    }
}
