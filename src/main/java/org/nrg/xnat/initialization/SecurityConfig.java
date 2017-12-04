/*
 * web: org.nrg.xnat.initialization.SecurityConfig
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.initialization;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.configuration.ConfigPaths;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xdat.services.AliasTokenService;
import org.nrg.xdat.services.XdatUserAuthService;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.security.*;
import org.nrg.xnat.security.alias.AliasTokenAuthenticationProvider;
import org.nrg.xnat.security.provider.AuthenticationProviderConfigurationLocator;
import org.nrg.xnat.security.provider.XnatDatabaseAuthenticationProvider;
import org.nrg.xnat.security.userdetailsservices.XnatDatabaseUserDetailsService;
import org.nrg.xnat.services.XnatAppInfo;
import org.nrg.xnat.services.validation.DateValidation;
import org.nrg.xnat.utils.DefaultInteractiveAgentDetector;
import org.nrg.xnat.utils.InteractiveAgentDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.access.vote.UnanimousBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.ReflectionSaltSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.channel.ChannelDecisionManagerImpl;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.access.channel.InsecureChannelProcessor;
import org.springframework.security.web.access.channel.SecureChannelProcessor;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.SimpleRedirectSessionInformationExpiredStrategy;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.nrg.xnat.initialization.XnatWebAppInitializer.EMPTY_ARRAY;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    public SecurityConfig(final SiteConfigPreferences preferences, final XnatAppInfo appInfo, final AliasTokenService aliasTokenService, final XdatUserAuthService userAuthService, final DateValidation dateValidation, final MessageSource messageSource, final NamedParameterJdbcTemplate template, final DataSource dataSource) {
        _preferences = preferences;
        _appInfo = appInfo;
        _aliasTokenService = aliasTokenService;
        _userAuthService = userAuthService;
        _dateValidation = dateValidation;
        _messageSource = messageSource;
        _template = template;
        _dataSource = dataSource;
    }

    @Autowired
    public void setAuthenticationProviders(final List<AuthenticationProvider> providers) {
        _providers.addAll(providers);
    }

    @Autowired(required = false)
    public void setXnatSecurityExtensions(final List<XnatSecurityExtension> extensions) {
        _extensions.addAll(extensions);
    }

    @Bean
    public XnatProviderManager customAuthenticationManager() {
        return new XnatProviderManager(_preferences, _userAuthService, _providers);
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        return new XnatDatabaseUserDetailsService(_dataSource);
    }

    @Bean
    public UnanimousBased unanimousBased() {
        final RoleVoter voter = new RoleVoter();
        voter.setRolePrefix("ROLE_");
        final List<AccessDecisionVoter<?>> voters = new ArrayList<>();
        voters.add(voter);
        voters.add(new AuthenticatedVoter());
        return new UnanimousBased(voters);
    }

    @Bean
    public OnXnatLogin logUserLogin() {
        return new OnXnatLogin();
    }

    @Bean
    public AuthenticationFailureHandler authFailure() {
        return new XnatUrlAuthenticationFailureHandler("/app/template/Login.vm?failed=true", "/app/template/PostRegister.vm");
    }

    @Bean
    public InteractiveAgentDetector interactiveAgentDetector(final SiteConfigPreferences preferences) {
        return new DefaultInteractiveAgentDetector(preferences);
    }

    @Bean
    public XnatAuthenticationEntryPoint loginUrlAuthenticationEntryPoint(final SiteConfigPreferences preferences, final InteractiveAgentDetector detector) {
        return new XnatAuthenticationEntryPoint("/app/template/Login.vm", preferences, detector);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ConcurrentSessionFilter concurrencyFilter(final SessionRegistry sessionRegistry) {
        return new ConcurrentSessionFilter(sessionRegistry, new SimpleRedirectSessionInformationExpiredStrategy("/app/template/Login.vm"));
    }

    @Bean
    @Primary
    public CompositeSessionAuthenticationStrategy sessionAuthenticationStrategy(final SessionRegistry sessionRegistry, final SiteConfigPreferences preferences) {
        return new CompositeSessionAuthenticationStrategy(Arrays.asList(new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry) {{
                                                                            setMaximumSessions(preferences.getConcurrentMaxSessions());
                                                                            setExceptionIfMaximumExceeded(true);
                                                                        }},
                                                                        new SessionFixationProtectionStrategy(),
                                                                        new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    public LogoutFilter logoutFilter(final SessionRegistry sessionRegistry) {
        final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
        securityContextLogoutHandler.setInvalidateHttpSession(true);
        final LogoutFilter filter = new LogoutFilter(logoutSuccessHandler(),
                                                     securityContextLogoutHandler,
                                                     new XnatLogoutHandler(sessionRegistry));
        filter.setFilterProcessesUrl("/app/action/LogoutUser");
        return filter;
    }

    @Bean
    public XnatLogoutSuccessHandler logoutSuccessHandler() {
        return new XnatLogoutSuccessHandler(_preferences.getRequireLogin(), "/", "/app/template/Login.vm");
    }

    @Bean
    public ConfigurableSecurityMetadataSourceFactory metadataSourceFactory() {
        return new ConfigurableSecurityMetadataSourceFactory(_preferences, _appInfo);
    }

    @Bean
    public TranslatingChannelProcessingFilter channelProcessingFilter() {
        final ChannelDecisionManagerImpl decisionManager = new ChannelDecisionManagerImpl();
        decisionManager.setChannelProcessors(Arrays.asList(new SecureChannelProcessor(), new InsecureChannelProcessor()));
        return new TranslatingChannelProcessingFilter(decisionManager, _preferences.getSecurityChannel());
    }

    @Bean
    public AliasTokenAuthenticationProvider aliasTokenAuthenticationProvider(final AliasTokenService aliasTokenService, final XdatUserAuthService userAuthService) {
        return new AliasTokenAuthenticationProvider(aliasTokenService, userAuthService);
    }

    @Bean
    public XnatAuthenticationFilter customAuthenticationFilter() {
        return new XnatAuthenticationFilter();
    }

    @Bean
    public XnatExpiredPasswordFilter expiredPasswordFilter(final SiteConfigPreferences preferences, final NamedParameterJdbcTemplate jdbcTemplate, final AliasTokenService aliasTokenService, final DateValidation dateValidation) {
        return new XnatExpiredPasswordFilter(preferences, aliasTokenService, dateValidation, jdbcTemplate) {{
            setChangePasswordPath("/app/template/XDATScreen_UpdateUser.vm");
            setChangePasswordDestination("/app/action/ModifyPassword");
            setLogoutDestination("/app/action/LogoutUser");
            setLoginPath("/app/template/Login.vm");
            setLoginDestination("/app/action/XDATLoginUser");
            setInactiveAccountPath("/app/template/InactiveAccount.vm");
            setInactiveAccountDestination("/app/action/XnatInactiveAccount");
            setEmailVerificationPath("/app/template/VerifyEmail.vm");
            setEmailVerificationDestination("/data/services/sendEmailVerification");
        }};
    }

    @Bean
    public XnatInitCheckFilter xnatInitCheckFilter(final XnatAppInfo appInfo) {
        return new XnatInitCheckFilter(appInfo);
    }

    @Bean
    public XnatDatabaseAuthenticationProvider xnatDatabaseAuthenticationProvider() {
        final ReflectionSaltSource saltSource = new ReflectionSaltSource();
        saltSource.setUserPropertyToUse("salt");

        final String name = _messageSource.getMessage("authProviders.localdb.defaults.name", EMPTY_ARRAY, "Database", Locale.getDefault());

        final XnatDatabaseAuthenticationProvider sha2DatabaseAuthProvider = new XnatDatabaseAuthenticationProvider(name, _aliasTokenService);
        sha2DatabaseAuthProvider.setUserDetailsService(userDetailsService());
        sha2DatabaseAuthProvider.setPasswordEncoder(Users.getEncoder());
        sha2DatabaseAuthProvider.setSaltSource(saltSource);
        return sha2DatabaseAuthProvider;
    }

    @Bean
    @Autowired
    public AuthenticationProviderConfigurationLocator authenticationProviderConfigurationLocator(final ConfigPaths configPaths) {
        return new AuthenticationProviderConfigurationLocator(configPaths, _messageSource);
    }

    @Bean
    @Override
    protected AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder builder) throws Exception {
        builder.parentAuthenticationManager(customAuthenticationManager());
        final XnatDatabaseAuthenticationProvider xnatDbAuthProvider = xnatDatabaseAuthenticationProvider();
        builder.authenticationProvider(xnatDbAuthProvider);
        for (final AuthenticationProvider provider : _providers) {
            if (!provider.equals(xnatDbAuthProvider)) {
                builder.authenticationProvider(provider);
            }
        }

        if (_extensions.size() > 0) {
            for (final XnatSecurityExtension extension : _extensions) {
                log.info("Now processing the security extension {} for authentication manager configuration", extension.getAuthMethod());
                extension.configure(builder);
            }
        }
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // This is basically what super.configure() does, minus httpBasic().
        http.authorizeRequests().anyRequest().authenticated().and().formLogin();

        final XnatAuthenticationEntryPoint authenticationEntryPoint = loginUrlAuthenticationEntryPoint(_preferences, interactiveAgentDetector(_preferences));
        http.apply(new XnatBasicAuthConfigurer<HttpSecurity>(authenticationEntryPoint));

        http.headers().frameOptions().sameOrigin()
            .httpStrictTransportSecurity().disable()
            .contentSecurityPolicy("frame-ancestors 'self'");

        http.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint);
        http.csrf().disable();
        http.anonymous().key(UserI.ANONYMOUS_AUTH_PROVIDER_KEY);
        http.sessionManagement().sessionAuthenticationStrategy(sessionAuthenticationStrategy(sessionRegistry(), _preferences));

        http.addFilterAt(channelProcessingFilter(), ChannelProcessingFilter.class)
            .addFilterBefore(customAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(xnatInitCheckFilter(_appInfo), RememberMeAuthenticationFilter.class)
            .addFilterAfter(expiredPasswordFilter(_preferences, _template, _aliasTokenService, _dateValidation), SecurityContextPersistenceFilter.class)
            .addFilterAt(concurrencyFilter(sessionRegistry()), ConcurrentSessionFilter.class)
            .addFilterAt(logoutFilter(sessionRegistry()), LogoutFilter.class);

        if (_extensions.size() > 0) {
            for (final XnatSecurityExtension extension : _extensions) {
                log.info("Now processing the security extension {} for HTTP security configuration", extension.getAuthMethod());
                extension.configure(http);
            }
        }
    }

    private final SiteConfigPreferences      _preferences;
    private final XnatAppInfo                _appInfo;
    private final AliasTokenService          _aliasTokenService;
    private final XdatUserAuthService        _userAuthService;
    private final MessageSource              _messageSource;
    private final DateValidation             _dateValidation;
    private final NamedParameterJdbcTemplate _template;
    private final DataSource                 _dataSource;

    private final List<AuthenticationProvider> _providers  = new ArrayList<>();
    private final List<XnatSecurityExtension>  _extensions = new ArrayList<>();
}
