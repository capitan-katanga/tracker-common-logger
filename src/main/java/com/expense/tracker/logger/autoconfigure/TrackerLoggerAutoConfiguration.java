package com.expense.tracker.logger.autoconfigure;

import com.expense.tracker.logger.client.LoggingClientInterceptor;
import com.expense.tracker.logger.config.TrackerLoggerProperties;
import com.expense.tracker.logger.filter.HttpLoggingFilter;
import com.expense.tracker.logger.filter.UserIdMdcFilter;
import com.expense.tracker.logger.otel.OpenTelemetryAppenderInstaller;
import com.expense.tracker.logger.task.MdcTaskDecorator;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.client.RestClient;

/**
 * Autoconfiguración del starter: se activa con solo tener la dependencia en el classpath.
 *
 * <ul>
 *   <li>Instala el appender de OpenTelemetry de logback (logs a Loki por OTLP) y publica {@link ProcessorMetrics}.</li>
 *   <li>Servlet: registra {@link HttpLoggingFilter} y, con Spring Security, {@link UserIdMdcFilter}.</li>
 *   <li>{@code RestClient}: engancha {@link LoggingClientInterceptor} a todo {@code RestClient.Builder} de Boot
 *       (incluidos los HTTP service groups, que aplican los {@code RestClientCustomizer}).</li>
 *   <li>{@code @Async}: un {@link TaskDecorator} que propaga MDC y traza, si el consumidor no definió otro.</li>
 * </ul>
 *
 * <p>Los defaults de exportación OTLP no están acá sino en {@code ObservabilityDefaultsPostProcessor}, porque
 * tienen que existir antes de que corran las autoconfiguraciones de Boot.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(TrackerLoggerProperties.class)
public class TrackerLoggerAutoConfiguration {

    /** Orden del filtro de logueo: justo después del {@code ServerHttpObservationFilter} (HIGHEST_PRECEDENCE + 1). */
    static final int HTTP_LOGGING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** Orden del filtro de userId: después de la cadena de Spring Security ({@code SecurityProperties.DEFAULT_FILTER_ORDER} = -100). */
    static final int USER_ID_FILTER_ORDER = -99;

    @Bean
    @ConditionalOnMissingBean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskDecorator trackerTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenTelemetry.class)
    static class OpenTelemetryLoggingConfiguration {

        @Bean
        OpenTelemetryAppenderInstaller openTelemetryAppenderInstaller(ObjectProvider<OpenTelemetry> openTelemetry) {
            return new OpenTelemetryAppenderInstaller(openTelemetry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBooleanProperty(name = "tracker.logger.http.server.enabled", matchIfMissing = true)
    static class ServerLoggingConfiguration {

        @Bean
        FilterRegistrationBean<HttpLoggingFilter> trackerHttpLoggingFilter(TrackerLoggerProperties properties) {
            var registration = new FilterRegistrationBean<>(new HttpLoggingFilter(properties));
            registration.setOrder(HTTP_LOGGING_FILTER_ORDER);
            return registration;
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
        static class UserIdConfiguration {

            @Bean
            FilterRegistrationBean<UserIdMdcFilter> trackerUserIdMdcFilter() {
                var registration = new FilterRegistrationBean<>(new UserIdMdcFilter());
                registration.setOrder(USER_ID_FILTER_ORDER);
                return registration;
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RestClient.class, RestClientCustomizer.class})
    @ConditionalOnBooleanProperty(name = "tracker.logger.http.client.enabled", matchIfMissing = true)
    static class ClientLoggingConfiguration {

        /**
         * Último customizer a propósito: el interceptor queda después de los de auth del consumidor y loguea los
         * headers finales (el {@code Authorization} sale enmascarado).
         */
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        RestClientCustomizer trackerLoggingRestClientCustomizer(TrackerLoggerProperties properties) {
            var interceptor = new LoggingClientInterceptor(
                    properties.maskedHeaders(), properties.maskedBodyFields(), properties.maxBodyLength());
            return builder -> builder.requestInterceptor(interceptor);
        }
    }
}
