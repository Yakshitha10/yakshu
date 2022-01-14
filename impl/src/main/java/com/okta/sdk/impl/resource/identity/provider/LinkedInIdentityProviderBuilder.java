/*
 * Copyright 2020-Present Okta, Inc.
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
package com.okta.sdk.impl.resource.identity.provider;

import com.okta.sdk.client.Client;
import com.okta.sdk.resource.PolicyAccountLink;
import com.okta.sdk.resource.PolicyAccountLinkAction;
import com.okta.sdk.resource.PolicySubject;
import com.okta.sdk.resource.PolicyUserNameTemplate;
import com.okta.sdk.resource.Provisioning;
import com.okta.sdk.resource.ProvisioningAction;
import com.okta.sdk.resource.ProvisioningConditions;
import com.okta.sdk.resource.ProvisioningDeprovisionedAction;
import com.okta.sdk.resource.ProvisioningDeprovisionedCondition;
import com.okta.sdk.resource.ProvisioningGroups;
import com.okta.sdk.resource.ProvisioningGroupsAction;
import com.okta.sdk.resource.ProvisioningSuspendedAction;
import com.okta.sdk.resource.ProvisioningSuspendedCondition;
import com.okta.sdk.resource.identity.provider.IdentityProvider;
import com.okta.sdk.resource.identity.provider.IdentityProviderCredentials;
import com.okta.sdk.resource.identity.provider.IdentityProviderCredentialsClient;
import com.okta.sdk.resource.identity.provider.IdentityProviderPolicy;
import com.okta.sdk.resource.identity.provider.Protocol;
import com.okta.sdk.resource.identity.provider.ProtocolType;

public class LinkedInIdentityProviderBuilder extends DefaultIdentityProviderBuilder {

    @Override
    public IdentityProvider buildAndCreate(Client client) {

        return client.createIdentityProvider(client.instantiate(IdentityProvider.class)
            .setType(IdentityProvider.TypeValues.LINKEDIN)
            .setName(name)
            .setProtocol(client.instantiate(Protocol.class)
                .setType(ProtocolType.OAUTH2)
                .setScopes(scopes)
                .setCredentials(client.instantiate(IdentityProviderCredentials.class)
                    .setClient(client.instantiate(IdentityProviderCredentialsClient.class)
                        .setClientId(clientId)
                        .setClientSecret(clientSecret))))
            .setPolicy(client.instantiate(IdentityProviderPolicy.class)
                .setProvisioning(client.instantiate(Provisioning.class)
                    .setAction(ProvisioningAction.AUTO)
                    .setProfileMaster(isProfileMaster)
                    .setGroups(client.instantiate(ProvisioningGroups.class)
                        .setAction(ProvisioningGroupsAction.NONE))
                    .setConditions(client.instantiate(ProvisioningConditions.class)
                        .setDeprovisioned(client.instantiate(ProvisioningDeprovisionedCondition.class)
                            .setAction(ProvisioningDeprovisionedAction.NONE))
                        .setSuspended(client.instantiate(ProvisioningSuspendedCondition.class)
                            .setAction(ProvisioningSuspendedAction.NONE))))
                .setAccountLink(client.instantiate(PolicyAccountLink.class)
                    .setFilter(null)
                    .setAction(PolicyAccountLinkAction.AUTO))
                .setSubject(client.instantiate(PolicySubject.class)
                    .setUserNameTemplate(client.instantiate(PolicyUserNameTemplate.class)
                        .setTemplate(userName))
                    .setMatchType(matchType))
                .setMaxClockSkew(maxClockSkew)));
    }
}
