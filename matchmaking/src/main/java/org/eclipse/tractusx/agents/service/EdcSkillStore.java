// Copyright (c) 2022,2024 Contributors to the Eclipse Foundation
//
// See the NOTICE file(s) distributed with this work for additional
// information regarding copyright ownership.
//
// This program and the accompanying materials are made available under the
// terms of the Apache License, Version 2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations
// under the License.
//
// SPDX-License-Identifier: Apache-2.0
package org.eclipse.tractusx.agents.service;

import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.tractusx.agents.AgentConfig;
import org.eclipse.tractusx.agents.SkillDistribution;
import org.eclipse.tractusx.agents.SkillStore;
import org.eclipse.tractusx.agents.jsonld.JsonLd;
import org.eclipse.tractusx.agents.model.Asset;
import org.eclipse.tractusx.agents.utils.Criterion;
import org.eclipse.tractusx.agents.utils.QuerySpec;
import org.eclipse.tractusx.agents.utils.TypeManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Implements a skill store based on EDC assets
 */
public class EdcSkillStore implements SkillStore {

    DataManagement management;
    TypeManager typeManager;
    AgentConfig config;

    public EdcSkillStore(DataManagement management, TypeManager typeManager, AgentConfig config) {
        this.management = management;
        this.typeManager = typeManager;
        this.config = config;
    }

    @Override
    public boolean isSkill(String key) {
        Matcher matcher = config.getAssetReferencePattern().matcher(key);
        return matcher.matches() && matcher.group("asset").contains("Skill");
    }

    @Override
    public String put(String key, String skill, String name, String description, String version, String contract, SkillDistribution dist, boolean isFederated, String allowServicePatern, String denyServicePattern, String... ontologies) {
        if (name == null) {
            name = "No name given";
        }
        if (description == null) {
            description = "No description given";
        }
        if (version == null) {
            version = "unknown version";
        }
        if (contract == null) {
            contract = config.getDefaultSkillContract();
        }
        if (dist == null) {
            dist = SkillDistribution.ALL;
        }
        String ontologiesString = String.join(",", ontologies);
        try {
            return management.createOrUpdateSkill(
                    key,
                    name,
                    description,
                    version,
                    contract,
                    ontologiesString,
                    dist.getDistributionMode(),
                    isFederated,
                    typeManager.getMapper().writeValueAsString(TextNode.valueOf(skill)),
                    allowServicePatern,
                    denyServicePattern
            ).getId();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public SkillDistribution getDistribution(String key) {
        return findAsset(key).map(asset -> SkillDistribution.valueOfMode(JsonLd.asString(asset.getPublicProperties()
                .get("https://w3id.org/catenax/ontology/common#distributionMode")))).orElse(SkillDistribution.ALL);
    }

    /**
     * finds an asset
     */
    protected Optional<Asset> findAsset(String key) {
        QuerySpec findAsset = QuerySpec.Builder.newInstance().filter(
                List.of(new Criterion("https://w3id.org/edc/v0.0.1/ns/id", "=", key),
                        new Criterion("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "=", "cx-common:SkillAsset"))).build();
        try {
            // we need to filter until the criterion really works
            return management
                    .listAssets(findAsset).stream()
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> get(String key) {
        return findAsset(key).map(asset ->
                JsonLd.asString(asset.getPrivateProperties().get("https://w3id.org/catenax/ontology/common#query"))
        );
    }
}
