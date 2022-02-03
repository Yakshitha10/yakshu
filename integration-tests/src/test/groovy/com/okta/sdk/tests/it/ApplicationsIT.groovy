/*
 * Copyright 2017-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.sdk.tests.it

import com.okta.commons.http.MediaType
import com.okta.sdk.client.Client
import com.okta.sdk.impl.ds.DefaultDataStore
import com.okta.sdk.resource.*
import com.okta.sdk.resource.common.*
import com.okta.sdk.resource.application.*
import com.okta.sdk.resource.group.Group
import com.okta.sdk.resource.group.GroupBuilder
import com.okta.sdk.resource.inline.hook.InlineHook
import com.okta.sdk.resource.inline.hook.InlineHookBuilder
import com.okta.sdk.resource.inline.hook.InlineHookChannelType
import com.okta.sdk.resource.inline.hook.InlineHookType
import com.okta.sdk.resource.schema.UserSchema
import com.okta.sdk.resource.schema.UserSchemaDefinitions
import com.okta.sdk.resource.schema.UserSchemaPublic
import com.okta.sdk.resource.group.User
import com.okta.sdk.tests.it.util.ITSupport
import org.testng.Assert
import org.testng.annotations.Test

import javax.xml.parsers.DocumentBuilderFactory

import static com.okta.sdk.tests.it.util.Util.assertNotPresent
import static com.okta.sdk.tests.it.util.Util.assertPresent
import static com.okta.sdk.tests.it.util.Util.expect
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

/**
 * Tests for {@code /api/v1/apps}.
 * @since 0.9.0
 */
class ApplicationsIT extends ITSupport {

    void doCrudTest(Application app) {

        // Create a resource
        def resource = create(client, app)
        registerForCleanup(resource as Deletable)

        // getting the resource again should result in the same object
        def readResource = read(client, resource.getId())
        assertThat readResource, notNullValue()

        // OpenIdConnectApplication contains a password when created, so it will not be the same when retrieved)
        if (!(app instanceof OpenIdConnectApplication)) {
            // getting the resource again should result in the same object
            assertThat readResource.getId(), equalTo(resource.getId())
        }

        // update the resource
        updateAndValidate(client, resource)

        // delete the resource
        deleteAndValidate(resource)
    }

    def create(Client client, Application app) {
        app.setLabel("java-sdk-it-" + UUID.randomUUID().toString())
        registerForCleanup(app as Deletable)
        return client.createApplication(app)
    }

    static def read(Client client, String id) {
        return client.getApplication(id)
    }

    static void updateAndValidate(Client client, def resource) {
        String newLabel = resource.label + "-update"
        resource.label = newLabel
        client.updateApplication(resource, resource.getId())

        // make sure the label was updated
        assertThat read(client, resource.getId()).label, equalTo(newLabel)
    }

    void deleteAndValidate(def resource) {
        client.deactivateApplication(resource.getId())
        client.delete(resource.getResourceHref(), resource)

        try {
            read(client, resource.getId())
            Assert.fail("Expected ResourceException (404)")
        } catch (ResourceException e) {
            assertThat e.status, equalTo(404)
        }
    }

    @Test (groups = "group1")
    void testClientIsReady() {
        assertThat "Expected client to be ready", client.isReady({ -> client.listApplications() })
    }

    @Test (groups = "group1")
    void basicListTest() {
        // Create a resource
        def resource = create(client, client.instantiate(AutoLoginApplication)
                                            .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
                                            .setVisibility(client.instantiate(ApplicationVisibility)
                                                .setAutoSubmitToolbar(false)
                                                .setHide(client.instantiate(ApplicationVisibilityHide)
                                                    .setIOS(false)
                                                    .setWeb(false)))
                                            .setSettings(client.instantiate(AutoLoginApplicationSettings)
                                                .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                                                    .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                                                    .setLoginUrl("https://swaprimaryloginurl.okta.com"))
                                                .setNotes(
                                                    client.instantiate(ApplicationSettingsNotes)
                                                    .setAdmin("Notes for Admin")
                                                    .setEnduser("Notes for EndUser")
                                                )
                                            ))
        // search the resource collection looking for the new resource
        Optional optional = client.listApplications().stream()
                                .filter {it.getId() == resource.getId()}
                                .findFirst()

        // make sure it exists
        assertThat "New resource with id ${resource.getId()} was not found in list resource.", optional.isPresent()
    }

    @Test (groups = "group1")
    void crudOpenIdConnect() {
        doCrudTest(client.instantiate(OpenIdConnectApplication)
                        .setSignOnMode(ApplicationSignOnMode.OPENID_CONNECT)
                        .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                            .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                                .setClientUri("https://example.com/client")
                                .setLogoUri("https://example.com/assets/images/logo-new.png")
                                .setRedirectUris(["https://example.com/oauth2/callback",
                                                  "myapp://callback"])
                                .setResponseTypes([OAuthResponseType.TOKEN,
                                                   OAuthResponseType.ID_TOKEN,
                                                   OAuthResponseType.CODE])
                                .setGrantTypes([OAuthGrantType.IMPLICIT,
                                                OAuthGrantType.AUTHORIZATION_CODE])
                                .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                                .setTosUri("https://example.com/client/tos")
                                .setPolicyUri("https://example.com/client/policy")))
                        .setCredentials(client.instantiate(OAuthApplicationCredentials)
                            .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                                .setClientId(UUID.randomUUID().toString())
                                .setAutoKeyRotation(true)
                                .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST))))
    }

    @Test (groups = "group1")
    void crudAutoLogin() {
        doCrudTest(client.instantiate(AutoLoginApplication)
                        .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
                        .setVisibility(client.instantiate(ApplicationVisibility)
                            .setAutoSubmitToolbar(false)
                            .setHide(client.instantiate(ApplicationVisibilityHide)
                                .setIOS(false)
                                .setWeb(false)))
                        .setSettings(client.instantiate(AutoLoginApplicationSettings)
                            .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                                .setRedirectUrl("http://swasecondaryredirecturl.okta.com")
                                .setLoginUrl("http://swaprimaryloginurl.okta.com"))))
    }

    @Test (groups = "bacon")
    void crudWsFed() {
        doCrudTest(client.instantiate(WsFederationApplication)
                        .setSignOnMode(ApplicationSignOnMode.WS_FEDERATION)
                        .setSettings(client.instantiate(WsFederationApplicationSettings)
                            .setApp(client.instantiate(WsFederationApplicationSettingsApplication)
                                .setAudienceRestriction( "urn:example:app")
                                .setGroupName(null)
                                .setGroupValueFormat("windowsDomainQualifiedName")
                                .setRealm("urn:example:app")
                                .setWReplyURL("https://example.com/")
                                .setAttributeStatements(null)
                                .setNameIDFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified")
                                .setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport")
                                .setSiteURL("https://example.com")
                                .setWReplyOverride(false)
                                .setGroupFilter(null)
                                .setUsernameAttribute("username"))))
    }

    @Test (groups = "group1")
    void crudSaml20() {

        String name = "java-sdk-it-" + UUID.randomUUID().toString()
        InlineHook createdInlineHook = InlineHookBuilder.instance()
            .setName(name)
            .setHookType(InlineHookType.SAML_TOKENS_TRANSFORM)
            .setChannelType(InlineHookChannelType.HTTP)
            .setUrl("https://www.example.com/inlineHooks")
            .setAuthorizationHeaderValue("Test-Api-Key")
            .addHeader("X-Test-Header", "Test header value")
            .buildAndCreate(client)
        registerForCleanup(createdInlineHook as Deletable)

        doCrudTest(client.instantiate(SamlApplication)
                        .setSignOnMode(ApplicationSignOnMode.SAML_2_0)
                        .setVisibility(client.instantiate(ApplicationVisibility)
                            .setAutoSubmitToolbar(false)
                            .setHide(client.instantiate(ApplicationVisibilityHide)
                            .setIOS(false)
                            .setWeb(false)))
                        .setSettings(client.instantiate(SamlApplicationSettings)
                            .setSignOn(client.instantiate(SamlApplicationSettingsSignOn)
                                .setDefaultRelayState("")
                                .setSsoAcsUrl("http://testorgone.okta")
                                .setIdpIssuer('https://www.okta.com/${org.externalKey}')
                                .setAudience("asdqwe123")
                                .setRecipient("http://testorgone.okta")
                                .setDestination("http://testorgone.okta")
                                .setSubjectNameIdTemplate('${user.userName}')
                                .setSubjectNameIdFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified")
                                .setResponseSigned(true)
                                .setAssertionSigned(true)
                                .setSignatureAlgorithm("RSA_SHA256")
                                .setDigestAlgorithm("SHA256")
                                .setHonorForceAuthn(true)
                                .setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport")
                                .setSpIssuer(null)
                                .setRequestCompressed(false)
                                .setInlineHooks(Arrays.asList(
                                    client
                                        .instantiate(SignOnInlineHook)
                                        .setId(createdInlineHook.getId())
                                ))
                                .setAttributeStatements(new ArrayList<SamlAttributeStatement>([
                                        client.instantiate(SamlAttributeStatement)
                                                .setType("EXPRESSION")
                                                .setName("Attribute")
                                                .setNamespace("urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified")
                                                .setValues(["Value"])])))))
    }

    @Test (groups = "group1")
    void crudSecurePasswordStore() {
        doCrudTest(client.instantiate(SecurePasswordStoreApplication)
                        .setSettings(client.instantiate(SecurePasswordStoreApplicationSettings)
                            .setApp(client.instantiate(SecurePasswordStoreApplicationSettingsApplication)
                                .setUrl("https://example.com/login.html")
                                .setPasswordField("#txtbox-password")
                                .setUsernameField("#txtbox-username")
                                .setOptionalField1("param1")
                                .setOptionalField1Value("somevalue")
                                .setOptionalField2("param2")
                                .setOptionalField2Value("yetanothervalue")
                                .setOptionalField3("param3")
                                .setOptionalField3Value("finalvalue"))))
    }

    @Test (groups = "group1")
    void crudBrowserPlugin() {
        doCrudTest(client.instantiate(SwaApplication)
                        .setSettings(client.instantiate(SwaApplicationSettings)
                            .setApp(client.instantiate(SwaApplicationSettingsApplication)
                                .setButtonField("btn-login")
                                .setPasswordField("txtbox-password")
                                .setUsernameField("txtbox-username")
                                .setUrl("https://example.com/login.html"))))
//                                        .setLoginUrlRegex("REGEX_EXPRESSION"))
    }

    @Test (groups = "group1")
    void crudBasicAuth() {
        doCrudTest(client.instantiate(BasicAuthApplication)
                        .setSettings(client.instantiate(BasicApplicationSettings)
                            .setApp(client.instantiate(BasicApplicationSettingsApplication)
                                .setAuthURL("https://example.com/auth.html")
                                .setUrl("https://example.com/login.html"))))
    }

    @Test (groups = "group1")
    void crudBasicAuth_editUsernameAndPassword() {
        doCrudTest(client.instantiate(BasicAuthApplication)
                        .setCredentials(client.instantiate(SchemeApplicationCredentials)
                            .setScheme(ApplicationCredentialsScheme.EDIT_USERNAME_AND_PASSWORD))
                        .setSettings(client.instantiate(BasicApplicationSettings)
                            .setApp(client.instantiate(BasicApplicationSettingsApplication)
                                .setAuthURL("https://example.com/auth.html")
                                .setUrl("https://example.com/login.html"))))
    }

    @Test (groups = "group1")
    void crudBasicAuth_adminSetsCredentials() {
        doCrudTest(client.instantiate(BasicAuthApplication)
                        .setCredentials(client.instantiate(SchemeApplicationCredentials)
                            .setScheme(ApplicationCredentialsScheme.ADMIN_SETS_CREDENTIALS)
                            .setRevealPassword(true))
                        .setSettings(client.instantiate(BasicApplicationSettings)
                            .setApp(client.instantiate(BasicApplicationSettingsApplication)
                                .setAuthURL("https://example.com/auth.html")
                                .setUrl("https://example.com/login.html"))))
    }

    @Test (groups = "group1")
    void invalidApplicationCredentialsSchemeTest() {
        expect(IllegalArgumentException, {
            client.instantiate(BasicAuthApplication)
                .setCredentials(client.instantiate(SchemeApplicationCredentials)
                    .setScheme(ApplicationCredentialsScheme.SDK_UNKNOWN)
                    .setRevealPassword(true))
                .setSettings(client.instantiate(BasicApplicationSettings)
                    .setApp(client.instantiate(BasicApplicationSettingsApplication)
                        .setAuthURL("https://example.com/auth.html")
                        .setUrl("https://example.com/login.html")))
        })
    }

    @Test (groups = "group1")
    void invalidOAuthEndpointAuthenticationMethodTest() {
        expect(IllegalArgumentException ,{client.instantiate(OpenIdConnectApplication)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE])
                    .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.SDK_UNKNOWN)))})
    }

    @Test (groups = "group1")
    void invalidOAuthResponseTypeTest() {
        expect(IllegalArgumentException ,{client.instantiate(OpenIdConnectApplication)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE,
                                       OAuthResponseType.SDK_UNKNOWN])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE])
                    .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST)))})
    }

    @Test (groups = "group1")
    void invalidOAuthGrantTypeTest() {
        expect(IllegalArgumentException, {client.instantiate(OpenIdConnectApplication)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE,
                                    OAuthGrantType.SDK_UNKNOWN])
                    .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST)))
        })
    }

    @Test (groups = "group1")
    void invalidOpenIdConnectApplicationTypeTest() {
        expect(IllegalArgumentException ,{
            client.instantiate(OpenIdConnectApplication)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE])
                    .setApplicationType(OpenIdConnectApplicationType.SDK_UNKNOWN)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST)))
        })
    }

    @Test (groups = "group1")
    void crudBookmark() {
        doCrudTest(client.instantiate(BookmarkApplication)
                        .setSettings(client.instantiate(BookmarkApplicationSettings)
                            .setApp(client.instantiate(BookmarkApplicationSettingsApplication)
                                .setRequestIntegration(false)
                                .setUrl("https://example.com/bookmark.htm"))))
    }

    @Test(enabled = false) // OKTA-75280
    void applicationKeysTest() {

        Client client = getClient()

        Application app1 = client.instantiate(AutoLoginApplication)
                .setLabel("app-${uniqueTestName}")
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com")))

        Application app2 = client.instantiate(AutoLoginApplication)
                .setLabel("app-${uniqueTestName}")
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com")))
        client.createApplication(app1)
        registerForCleanup(app1 as Deletable)
        client.createApplication(app2)
        registerForCleanup(app2 as Deletable)

        JsonWebKeyList app1Keys = client.listApplicationKeys(app1.getId())
        assertThat(app1Keys.size(), equalTo(1))

        JsonWebKey webKey = client.generateApplicationKey(app1.getId(), 5)
        assertThat(webKey, notNullValue())
        assertThat(client.listApplicationKeys(app1.getId()).size(), equalTo(2))

        JsonWebKey readWebKey = client.getApplicationKey(app1.getId(), webKey.getKid())
        assertThat(webKey, equalTo(readWebKey))

        JsonWebKey clonedWebKey = client.cloneApplicationKey(app1.getId(), webKey.getKid(), app2.getId())
        assertThat(clonedWebKey, notNullValue())

        JsonWebKeyList app2Keys = client.listKeys(app2.getId())
        assertThat(app2Keys.size(), equalTo(2))
    }

    @Test (groups = "group1")
    void deactivateActivateTest() {
        Application app = client.createApplication(client.instantiate(AutoLoginApplication)
                .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
                .setLabel("app-${uniqueTestName}")
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com"))))

        registerForCleanup(app as Deletable)

        assertThat(app.getStatus(), equalTo(ApplicationLifecycleStatus.ACTIVE))

        client.deactivateApplication(app.getId())
        assertThat(client.getApplication(app.getId()).getStatus(), equalTo(ApplicationLifecycleStatus.INACTIVE))

        client.activateApplication(app.getId())
        assertThat(client.getApplication(app.getId()).getStatus(), equalTo(ApplicationLifecycleStatus.ACTIVE))
    }

    // Quarantining this till OKTA-421154 is fixed
    @Test (groups = "bacon", enabled = false)
    void groupAssignmentWithNullBodyTest() {

        Application app = client.createApplication(client.instantiate(AutoLoginApplication)
                .setLabel("app-${uniqueTestName}")
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com"))))

        Group group = GroupBuilder.instance()
                .setName("app-test-group-${uniqueTestName}")
                .setDescription("IT created Group")
                .buildAndCreate(client)

        registerForCleanup(app as Deletable)
        registerForCleanup(group as Deletable)

        ApplicationGroupAssignment groupAssignment = app.createApplicationGroupAssignment(group.getId())
        assertThat(groupAssignment, notNullValue())
    }

    @Test (groups = "bacon", enabled = false)
    void groupAssignmentTest() {

        Application app = client.createApplication(client.instantiate(AutoLoginApplication)
                .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
                .setLabel("app-${uniqueTestName}")
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com"))))

        Group group = GroupBuilder.instance()
                .setName("app-test-group-${uniqueTestName}")
                .setDescription("IT created Group")
                .buildAndCreate(client)

        registerForCleanup(app as Deletable)
        registerForCleanup(group as Deletable)

        assertThat(client.listApplicationGroupAssignments(app.getId()).iterator().size(), equalTo(0))

        ApplicationGroupAssignment aga = client.instantiate(ApplicationGroupAssignment)
                                            .setPriority(2)

        ApplicationGroupAssignment groupAssignment = app.createApplicationGroupAssignment(group.getId())
        assertThat(groupAssignment, notNullValue())
        assertThat(groupAssignment.priority, equalTo(2))
        assertThat(client.listApplicationGroupAssignments(app.getId()).iterator().size(), equalTo(1))

        ApplicationGroupAssignment receivedGroupAssignment = client.getApplicationGroupAssignment(app.getId(), group.getId())
        assertThat(groupAssignment.getId(), equalTo(receivedGroupAssignment.getId()))
        assertThat(groupAssignment.getPriority(), equalTo(receivedGroupAssignment.getPriority()))

        // delete the assignment
        client.deleteApplicationGroupAssignment(app.getId(), groupAssignment.getId())
        assertThat(client.listApplicationGroupAssignments(app.getId()).iterator().size(), equalTo(0))
    }

    // Remove this groups tag after OKTA-337342 is resolved (Adding this tag disables the test in bacon PDV)
    @Test (groups = "bacon")
    void associateUserWithApplication() {

        Client client = getClient()
        User user1 = randomUser()
        User user2 = randomUser()

        String label = "app-${uniqueTestName}"
        Application app = client.instantiate(AutoLoginApplication)
                .setLabel(label)
                .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
                .setVisibility(client.instantiate(ApplicationVisibility)
                    .setAutoSubmitToolbar(false)
                    .setHide(client.instantiate(ApplicationVisibilityHide)
                        .setIOS(false)
                        .setWeb(false)))
                .setSettings(client.instantiate(AutoLoginApplicationSettings)
                    .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                        .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                        .setLoginUrl("https://swaprimaryloginurl.okta.com")))
        client.createApplication(app)
        registerForCleanup(app as Deletable)

        // issue: listApplicationUsers() occasionally throws HTTP 404, Okta E0000007 - Resource not found error.
        // adding a sleep after createApplication() helps resolve the above issue.
        sleep(getTestOperationDelay())

        AppUserList appUserList = client.listApplicationUsers(app.getId())
        assertThat appUserList.iterator().size(), equalTo(0)

        AppUser appUser1 = client.instantiate(AppUser)
            .setScope("USER")
            .setId(user1.getId())
            .setCredentials(client.instantiate(AppUserCredentials)
                .setUserName(user1.getProfile().getEmail())
                .setPassword(client.instantiate(AppUserPasswordCredential)
                    .setValue("super-secret1".toCharArray())))
        client.assignUserToApplication(appUser1, app.getId())

        AppUser appUser2 = client.instantiate(AppUser)
            .setScope("USER")
            .setId(user2.getId())
            .setCredentials(client.instantiate(AppUserCredentials)
                .setUserName(user2.getProfile().getEmail())
                .setPassword(client.instantiate(AppUserPasswordCredential)
                    .setValue("super-secret2".toCharArray())))

        assertThat(client.assignUserToApplication(appUser1, app.getId()), sameInstance(appUser1))
        assertThat(client.assignUserToApplication(appUser2, app.getId()), sameInstance(appUser2))

        // fix flakiness seen in PDV tests
        Thread.sleep(getTestOperationDelay())

        // now we should have 2
        assertThat appUserList.iterator().size(), equalTo(2)

        // delete just one
        client.deleteApplicationUser(app.getId(), appUser1.getId())

        // fix flakiness seen in PDV tests
        Thread.sleep(getTestOperationDelay())

        // now we should have 1
        assertThat appUserList.iterator().size(), equalTo(1)

        appUser2.getCredentials().setUserName("updated-"+user2.getProfile().getEmail())
        client.updateApplicationUser(appUser2, app.getId(), appUser2.getId())

        AppUser readAppUser = client.getApplicationUser(app.getId(), appUser2.getId())
        assertThat readAppUser.getCredentials().getUserName(), equalTo("updated-"+user2.getProfile().getEmail())
    }

    @Test (groups = "group1")
    void csrTest() {
        Client client = getClient()

        String label = "app-${uniqueTestName}"
        Application app = client.instantiate(OpenIdConnectApplication)
            .setLabel(label)
            .setSignOnMode(ApplicationSignOnMode.OPENID_CONNECT)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE])
                    .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST)))
        client.createApplication(app)
        registerForCleanup(app as Deletable)

        assertThat(app.getStatus(), equalTo(ApplicationLifecycleStatus.ACTIVE))

        // create csr metadata
        CsrMetadata csrMetadata = client.instantiate(CsrMetadata)
              .setSubject(client.instantiate(CsrMetadataSubject)
                  .setCountryName("US")
                  .setStateOrProvinceName("California")
                  .setLocalityName("San Francisco")
                  .setOrganizationName("Okta, Inc.")
                  .setOrganizationalUnitName("Dev")
                  .setCommonName("SP Issuer"))
              .setSubjectAltNames(client.instantiate(CsrMetadataSubjectAltNames)
                  .setDnsNames(["dev.okta.com"]))

        // generate csr with metadata
        Csr csr = client.generateCsrForApplication(csrMetadata, app.getId())

        // verify
        assertPresent(client.listCsrsForApplication(app.getId()), csr)

        // revoke csr
        client.revokeCsrFromApplication(app.getId(), csr.getId())

        // verify
        assertNotPresent(client.listCsrsForApplication(app.getId()), csr)
    }

    // Quarantining this till OKTA-421154 is fixed
    @Test (groups = "bacon")
    void oAuth2ScopeConsentGrantTest() {
        Client client = getClient()

        String label = "app-${uniqueTestName}"
        Application app = client.instantiate(OpenIdConnectApplication)
            .setLabel(label)
            .setSignOnMode(ApplicationSignOnMode.OPENID_CONNECT)
            .setSettings(client.instantiate(OpenIdConnectApplicationSettings)
                .setOAuthClient(client.instantiate(OpenIdConnectApplicationSettingsClient)
                    .setClientUri("https://example.com/client")
                    .setLogoUri("https://example.com/assets/images/logo-new.png")
                    .setRedirectUris(["https://example.com/oauth2/callback",
                                      "myapp://callback"])
                    .setResponseTypes([OAuthResponseType.TOKEN,
                                       OAuthResponseType.ID_TOKEN,
                                       OAuthResponseType.CODE])
                    .setGrantTypes([OAuthGrantType.IMPLICIT,
                                    OAuthGrantType.AUTHORIZATION_CODE])
                    .setApplicationType(OpenIdConnectApplicationType.NATIVE)
                    .setTosUri("https://example.com/client/tos")
                    .setPolicyUri("https://example.com/client/policy")))
            .setCredentials(client.instantiate(OAuthApplicationCredentials)
                .setOAuthClient(client.instantiate(ApplicationCredentialsOAuthClient)
                    .setClientId(UUID.randomUUID().toString())
                    .setAutoKeyRotation(true)
                    .setTokenEndpointAuthMethod(OAuthEndpointAuthenticationMethod.CLIENT_SECRET_POST)))
        client.createApplication(app)
        registerForCleanup(app as Deletable)

        assertThat(app.getStatus(), equalTo(ApplicationLifecycleStatus.ACTIVE))

        // grant consent
        OAuth2ScopeConsentGrant oAuth2ScopeConsentGrant = client.grantConsentToScope(client.instantiate(OAuth2ScopeConsentGrant)
            .setIssuer(client.dataStore.baseUrlResolver.baseUrl)
            .setScopeId("okta.apps.manage"), app.getId())

        // verify
        assertPresent(client.listScopeConsentGrants(app.getId()), client.getScopeConsentGrant(app.getId(), oAuth2ScopeConsentGrant.getId()))

        // revoke consent
        client.revokeScopeConsentGrant(app.getId(), oAuth2ScopeConsentGrant.getId())

        // verify
        assertNotPresent(client.listScopeConsentGrants(app.getId()), client.getScopeConsentGrant(app.getId(), oAuth2ScopeConsentGrant.getId()))
    }

    // Quarantining this till OKTA-421154 is fixed
    @Test (groups = "bacon")
    void testExecuteWithoutAcceptHeader() {
        def app = client.instantiate(SamlApplication)
            .setSignOnMode(ApplicationSignOnMode.SAML_2_0)
            .setVisibility(client.instantiate(ApplicationVisibility))
            .setSettings(client.instantiate(SamlApplicationSettings)
                .setSignOn(client.instantiate(SamlApplicationSettingsSignOn)
                    .setSsoAcsUrl("http://testorgone.okta")
                    .setAudience("asdqwe123")
                    .setRecipient("http://testorgone.okta")
                    .setDestination("http://testorgone.okta")
                    .setAssertionSigned(true)
                    .setSignatureAlgorithm("RSA_SHA256")
                    .setDigestAlgorithm("SHA256")
                )
            )
        def dataStore = (DefaultDataStore) client.getDataStore()
        def resource = create(client, app)
        def url = resource.getLinks().get("users")["href"]
        registerForCleanup(resource as Deletable)

        // issue: testExecuteWithoutAcceptHeader() occasionally throws HTTP 404, Okta E0000007 - Resource not found error.
        // adding a sleep after create() helps resolve the above issue.
        sleep(getTestOperationDelay())

        Resource response = dataStore.getResource(url as String, Application.class)

        assertThat(response.isEmpty(), is(false))
        assertThat(response.size(), is(3))
    }

    @Test (groups = "group1")
    void testExecuteAcceptIonPlusJson() {
        def app = client.instantiate(SamlApplication)
            .setSignOnMode(ApplicationSignOnMode.SAML_2_0)
            .setVisibility(client.instantiate(ApplicationVisibility))
            .setSettings(client.instantiate(SamlApplicationSettings)
                .setSignOn(client.instantiate(SamlApplicationSettingsSignOn)
                    .setSsoAcsUrl("http://testorgone.okta")
                    .setAudience("asdqwe123")
                    .setRecipient("http://testorgone.okta")
                    .setDestination("http://testorgone.okta")
                    .setAssertionSigned(true)
                    .setSignatureAlgorithm("RSA_SHA256")
                    .setDigestAlgorithm("SHA256")
                )
            )
        def dataStore = (DefaultDataStore) client.getDataStore()
        def resource = create(client, app)
        def url = resource.getLinks().get("users")["href"]
        def headers = Collections.singletonMap("Accept", Collections.singletonList("application/ion+json"))
        registerForCleanup(resource as Deletable)

        Resource response = dataStore.getResource(url as String, Application.class, null, headers)

        assertThat(response.isEmpty(), is(false))
        assertThat(response.size(), is(3))
    }

    @Test (groups = "group1")
    void testGetRawResponse() {
        def app = client.instantiate(SamlApplication)
            .setSignOnMode(ApplicationSignOnMode.SAML_2_0)
            .setVisibility(client.instantiate(ApplicationVisibility))
            .setSettings(client.instantiate(SamlApplicationSettings)
                .setSignOn(client.instantiate(SamlApplicationSettingsSignOn)
                    .setSsoAcsUrl("http://testorgone.okta")
                    .setAudience("asdqwe123")
                    .setRecipient("http://testorgone.okta")
                    .setDestination("http://testorgone.okta")
                    .setAssertionSigned(true)
                    .setSignatureAlgorithm("RSA_SHA256")
                    .setDigestAlgorithm("SHA256")
                )
            )
        def dataStore = (DefaultDataStore) client.getDataStore()
        def resource = create(client, app)
        def url = resource.getLinks().get("metadata")["href"]
        def headers = Collections.singletonMap("Accept", Collections.singletonList(MediaType.APPLICATION_XML as String))
        registerForCleanup(resource as Deletable)

        InputStream response = dataStore.getRawResponse(url as String, null, headers)

        assertThat(resource.isEmpty(), is(false))
        String x509Certificate = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(response)
            .getElementsByTagName("ds:X509Certificate")
            .item(0)
            .getFirstChild()
            .getNodeValue()
        assertThat(x509Certificate.isBlank(), is(false))
    }

    @Test (groups = "group1")
    void getApplicationUserSchemaTest() {

        Application createdApp = client.instantiate(AutoLoginApplication)
            .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
            .setLabel("app-${uniqueTestName}")
            .setVisibility(client.instantiate(ApplicationVisibility)
                .setAutoSubmitToolbar(false)
                .setHide(client.instantiate(ApplicationVisibilityHide)
                    .setIOS(false)
                    .setWeb(false)))
            .setSettings(client.instantiate(AutoLoginApplicationSettings)
                .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                    .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                    .setLoginUrl("https://swaprimaryloginurl.okta.com")))
        client.createApplication(createdApp)
        registerForCleanup(createdApp as Deletable)

        def userSchema = client.getApplicationUserSchema(createdApp.getId())
        assertThat(userSchema, notNullValue())
        assertThat(userSchema.getName(), notNullValue())
        assertThat(userSchema.getTitle(), notNullValue())
        assertThat(userSchema.getType(), notNullValue())
        assertThat(userSchema.getDefinitions(), notNullValue())

        def userSchemaBase = userSchema.getDefinitions().getBase()
        assertThat(userSchemaBase, notNullValue())
        userSchemaBase.getRequired().forEach({ requiredItem ->
            assertThat(userSchemaBase.getProperties().containsKey(requiredItem), equalTo(true))
        })
    }

    @Test (groups = "group1")
    void updateApplicationUserProfileTest() {

        Application createdApp = client.instantiate(AutoLoginApplication)
            .setSignOnMode(ApplicationSignOnMode.AUTO_LOGIN)
            .setLabel("app-${uniqueTestName}")
            .setVisibility(client.instantiate(ApplicationVisibility)
                .setAutoSubmitToolbar(false)
                .setHide(client.instantiate(ApplicationVisibilityHide)
                    .setIOS(false)
                    .setWeb(false)))
            .setSettings(client.instantiate(AutoLoginApplicationSettings)
                .setSignOn(client.instantiate(AutoLoginApplicationSettingsSignOn)
                    .setRedirectUrl("https://swasecondaryredirecturl.okta.com")
                    .setLoginUrl("https://swaprimaryloginurl.okta.com")))
        client.createApplication(createdApp)
        registerForCleanup(createdApp as Deletable)

        def userSchema = client.getApplicationUserSchema(createdApp.getId())
        assertThat(userSchema, notNullValue())
        assertThat(userSchema.getDefinitions(), notNullValue())

        def app = client.instantiate(UserSchema)
        app.setDefinitions(client.instantiate(UserSchemaDefinitions))
        app.getDefinitions().setCustom(client.instantiate(UserSchemaPublic))
        app.getDefinitions().getCustom().setProperties(new LinkedHashMap() {
            {
                put("twitterUserName",
                    new LinkedHashMap() {
                        {
                            put("title", "Twitter username")
                            put("description", "Username for twitter.com")
                            put("type", "string")
                            put("minLength", 1)
                            put("maxLength", 20)
                        }
                    })
            }
        })

        def updatedUserSchema = client.updateApplicationUserProfile(createdApp.getId(), app)
        assertThat(updatedUserSchema, notNullValue())
        assertThat(updatedUserSchema.getDefinitions().getCustom(), notNullValue())

        def userSchemaPublic = updatedUserSchema.getDefinitions().getCustom()
        assertThat(userSchemaPublic.getProperties().containsKey("twitterUserName"), equalTo(true))

        def customPropertyMap = userSchemaPublic.getProperties().get("twitterUserName")
        assertThat(customPropertyMap["title"], equalTo("Twitter username"))
        assertThat(customPropertyMap["description"], equalTo("Username for twitter.com"))
        assertThat(customPropertyMap["type"], equalTo("string"))
        assertThat(customPropertyMap["minLength"], equalTo(1))
        assertThat(customPropertyMap["maxLength"], equalTo(20))
    }
}
