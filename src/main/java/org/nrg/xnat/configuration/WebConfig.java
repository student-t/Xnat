/*
 * web: org.nrg.xnat.configuration.WebConfig
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.configuration;

import org.nrg.xapi.rest.dicomweb.Dicom2XmlMessageConverter;
import org.nrg.xapi.rest.dicomweb.MultipartDicomFileMessageConverter;
import org.nrg.xapi.rest.dicomweb.MultipartDicomMessageConverter;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xnat.web.converters.XftBeanHttpMessageConverter;
import org.nrg.xnat.web.converters.XftObjectHttpMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import javax.xml.bind.Marshaller;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebMvc
@EnableAspectJAutoProxy
@ComponentScan({"org.nrg.xapi.rest.aspects", "org.nrg.xapi.authorization"})
public class WebConfig extends WebMvcConfigurerAdapter {
    @Autowired
    public void setJackson2ObjectMapperBuilder(final Jackson2ObjectMapperBuilder objectMapperBuilder) {
        _objectMapperBuilder = objectMapperBuilder;
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("**/swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    public void configureMessageConverters(final List<HttpMessageConverter<?>> converters) {
        converters.add(stringHttpMessageConverter());
        converters.add(mappingJackson2HttpMessageConverter());
        converters.add(dicom2XmlMessageConverter());
        converters.add(marshallingHttpMessageConverter());
        converters.add(resourceHttpMessageConverter());
        converters.add(xftBeanHttpMessageConverter());
        converters.add(xftObjectHttpMessageConverter());
        converters.add(multipartDicomMessageConverter( converters));
        converters.add(multipartDicomFileMessageConverter( ));
    }

    @Override
    public void configurePathMatch(final PathMatchConfigurer matcher) {
        matcher.setUseRegisteredSuffixPatternMatch(true);
    }

    @Override
    public void configureAsyncSupport(final AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(-1);
        configurer.setTaskExecutor(asyncTaskExecutor());
    }

    @Bean
    public HttpMessageConverter<?> xftObjectHttpMessageConverter() {
        return new XftObjectHttpMessageConverter();
    }

    @Bean
    public HttpMessageConverter<?> xftBeanHttpMessageConverter() {
        return new XftBeanHttpMessageConverter();
    }

    @Bean
    public HttpMessageConverter<?> resourceHttpMessageConverter() {
        return new ResourceHttpMessageConverter();
    }

    @Bean
    public HttpMessageConverter<?> stringHttpMessageConverter() {
        return new StringHttpMessageConverter();
    }

    @Bean
    public HttpMessageConverter<?> marshallingHttpMessageConverter() {
        return new MarshallingHttpMessageConverter(_marshaller, _marshaller);
    }

    @Bean
    public HttpMessageConverter<?> mappingJackson2HttpMessageConverter() {
        return new MappingJackson2HttpMessageConverter(_objectMapperBuilder.build());
    }

    @Bean
    public HttpMessageConverter<?> multipartDicomMessageConverter(List<HttpMessageConverter<?>> converters) {
        return new MultipartDicomMessageConverter( converters);
    }

    @Bean
    public HttpMessageConverter<?> multipartDicomFileMessageConverter() {
        return new MultipartDicomFileMessageConverter( );
    }

    @Bean
    public HttpMessageConverter<?> dicom2XmlMessageConverter() {
        return new Dicom2XmlMessageConverter();
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new SimpleAsyncTaskExecutor("async");
    }

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Bean
    public ViewResolver viewResolver() {
        return new InternalResourceViewResolver() {{
            setExposeContextBeansAsAttributes(true);
            setViewClass(JstlView.class);
            setPrefix("/WEB-INF/views/");
            setSuffix(".jsp");
        }};
    }

    @Bean
    public MessageSource messageSource() {
        return new ReloadableResourceBundleMessageSource() {{
            setBasename("classpath:org/nrg/xnat/messages/system");
        }};
    }

    private static final Map<String, Object> MARSHALLER_PROPERTIES = new HashMap<String, Object>() {{
        put(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }};

    private Jackson2ObjectMapperBuilder _objectMapperBuilder;

    private final Jaxb2Marshaller _marshaller = new Jaxb2Marshaller() {{
        setClassesToBeBound(SiteConfigPreferences.class);
        setMarshallerProperties(MARSHALLER_PROPERTIES);
    }};
}
