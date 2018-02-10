package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.PebbleTemplateEngine;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.middleware.UserInfoHandler;
import org.blackcat.trunk.http.requests.MainHandler;
import org.blackcat.trunk.http.requests.handlers.*;
import org.blackcat.trunk.http.requests.response.impl.HtmlResponseBuilderImpl;
import org.blackcat.trunk.http.requests.response.impl.JsonResponseBuilderImpl;
import org.blackcat.trunk.storage.Storage;

import java.text.MessageFormat;

import static org.blackcat.trunk.conf.Keys.OAUTH2_PROVIDER_GOOGLE;
import static org.blackcat.trunk.conf.Keys.OAUTH2_PROVIDER_KEYCLOAK;

public final class MainHandlerImpl implements MainHandler {

    private final String OAUTH2_CALLBACK_LOCATION = "/callback";
    private final Logger logger = LoggerFactory.getLogger(MainHandlerImpl.class);

    private final Configuration configuration;
    private final Vertx vertx;
    private final Router router;
    private final Storage storage;

    private final HtmlResponseBuilderImpl htmlResponseBuilder;
    private final JsonResponseBuilderImpl jsonResponseBuilder;

    public MainHandlerImpl(final Vertx vertx,
                           final Configuration configuration,
                           final Storage storage) {

        this.vertx = vertx;
        this.router = Router.router(vertx);
        this.configuration = configuration;
        this.storage = storage;

        this.htmlResponseBuilder = new HtmlResponseBuilderImpl(PebbleTemplateEngine.create(vertx));
        this.jsonResponseBuilder = new JsonResponseBuilderImpl();

        setupMiddlewareHandlers();
        setupOAuth2Handlers();
        setupProtectedHandlers();
        setupPublicHandlers();
        setupErrorHandlers();
    }

    private void setupMiddlewareHandlers() {

        final String vertxKey = "vertx";
        final String storageKey = "storage";
        final String configurationKey = "configuration";
        final String jsonResponseBuilderKey = "jsonResponseBuilder";
        final String htmlResponseBuilderKey = "htmlResponseBuilder";

        /* hack required to prevent request to be prematurely consumed when doing uploads */
        router.postWithRegex("/protected/.*").handler(ctx -> {
            ctx.request().pause();
            ctx.next();
        });

        // Initial routing ctx setup
        router.route().handler(ctx -> {
            // Add general refs to the ctx
            ctx.put( vertxKey, vertx);
            ctx.put( storageKey, storage);
            ctx.put( configurationKey, configuration);

            // it's up to the request handler to decider whether to use one or the other
            ctx.put(jsonResponseBuilderKey, jsonResponseBuilder);
            ctx.put(htmlResponseBuilderKey, htmlResponseBuilder);

            ctx.next();
        });

        // We need cookies, sessions and request bodies
        router.route().handler(CookieHandler.create());

        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx))
                                            .setCookieHttpOnlyFlag(true);

        if (configuration.isSSLEnabled()) {
            // avoid reading, sniffing hijacking or tampering your sessions (requires SSL)
            sessionHandler
                .setCookieSecureFlag(true);
        } else sessionHandler.setNagHttps(false); /* avoid nagging about not using https */

        router.route().handler(sessionHandler);
    }

    private void setupErrorHandlers() {
        /* invalid URL */
        router.getWithRegex(".*")
            .handler(htmlResponseBuilder::notFound);

        /* invalid method */
        router.routeWithRegex(".*")
            .handler(htmlResponseBuilder::methodNotAllowed);

        /* errors */
        router.route()
            .failureHandler(htmlResponseBuilder::internalServerError);
    }

    private void setupProtectedHandlers() {
        /* An extra handler to fetch user info into context */
        UserInfoHandler userInfoHandler = UserInfoHandler.create();
        router.routeWithRegex("/protected/.*").handler(userInfoHandler);

        router.get("/protected/main")
            .handler(ProtectedIndexHandler.create());

        router.get("/protected/logout")
            .handler(LogoutRequestHandler.create());

        router.getWithRegex("/share/.*")
            .handler(GetSharingInformationRequestHandler.create());

        router.putWithRegex("/share/.*")
            .handler(BodyHandler.create());

        router.routeWithRegex("/share/.*")
            .handler(userInfoHandler);

        router.putWithRegex("/share/.*")
            .handler(PutSharingInformationRequestHandler.create());

        router.getWithRegex("/protected/.*")
            .handler(GetResourceRequestHandler.create());

        router.putWithRegex("/protected/.*")
            .handler(PutResourceRequestHandler.create());

        router.postWithRegex("/protected/.*")
            .handler(PostResourceRequestHandler.create());

        router.deleteWithRegex("/protected/.*")
            .handler(DeleteResourceRequestHandler.create());
    }

    private void setupPublicHandlers() {
        /* public index (unrestricted) */
        router.get("/")
            .handler(PublicIndexHandler.create());

        /* static files (unrestricted) */
        router.getWithRegex("/static/.*")
            .handler(StaticHandler.create());
    }

    private void setupOAuth2Handlers() {
        OAuth2Auth authProvider;
        final String oauth2ProviderName = configuration.getOauth2Provider();

        if (oauth2ProviderName.equals(OAUTH2_PROVIDER_GOOGLE)) {
            authProvider = GoogleAuth.create(vertx,
                configuration.getOauth2ClientID(),
                configuration.getOauth2ClientSecret());
        } else if (oauth2ProviderName.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            authProvider = KeycloakAuth.create(vertx,
                OAuth2FlowType.AUTH_CODE,
                buildKeyCloakConfiguration());
        } else {
            throw new RuntimeException(
                MessageFormat.format("Unsupported OAuth2 provider: {0}", oauth2ProviderName));
        }

        // FIXME: 2/3/18 This sucks!
        String callBackFullURL = String.format("http%s://%s:%d%s",
            configuration.isSSLEnabled() ? "s" : "",
            configuration.getHttpHost(),
            configuration.getHttpPort(),
            OAUTH2_CALLBACK_LOCATION);
        logger.info("Setting up oauth2 callback at {}", callBackFullURL);
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(authProvider, callBackFullURL);

        /* We need a user session handler too to make sure the user is stored in the session between requests */
        router.route().handler(UserSessionHandler.create(authProvider));

        /* Keep protected contents under oauth2 */
        authHandler.setupCallback(router.get(OAUTH2_CALLBACK_LOCATION));
        router.routeWithRegex("/protected/.*").handler(authHandler);
        router.routeWithRegex("/share/.*").handler(authHandler);
    }

    private JsonObject buildKeyCloakConfiguration() {
        return new JsonObject().put("realm", configuration.getOauth2AuthServerRealm())
                   .put("realm-public-key", configuration.getOauth2AuthServerPublicKey())
                   .put("auth-server-url", configuration.getOauth2AuthServerURL())
                   .put("ssl-required", "external")
                   .put("resource", configuration.getOauth2ClientID())
                   .put("credentials", new JsonObject()
                                           .put("secret", configuration.getOauth2ClientSecret()));
    }

    @Override
    public void handle(HttpServerRequest request) {
        logger.info("Routing HTTP Request: {} {}", request.method(), request.uri());
        router.accept(request);
    }

    public static MainHandlerImpl create(Vertx vertx, Configuration configuration, Storage storage) {
        return new MainHandlerImpl(vertx, configuration, storage);
    }
}
