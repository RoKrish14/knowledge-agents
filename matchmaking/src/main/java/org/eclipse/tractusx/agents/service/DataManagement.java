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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.Json;
import jakarta.ws.rs.InternalServerErrorException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.eclipse.tractusx.agents.AgentConfig;
import org.eclipse.tractusx.agents.jsonld.JsonLd;
import org.eclipse.tractusx.agents.model.Asset;
import org.eclipse.tractusx.agents.model.ContractAgreement;
import org.eclipse.tractusx.agents.model.ContractNegotiation;
import org.eclipse.tractusx.agents.model.ContractNegotiationRequest;
import org.eclipse.tractusx.agents.model.DcatCatalog;
import org.eclipse.tractusx.agents.model.IdResponse;
import org.eclipse.tractusx.agents.model.TransferProcess;
import org.eclipse.tractusx.agents.model.TransferRequest;
import org.eclipse.tractusx.agents.utils.Criterion;
import org.eclipse.tractusx.agents.utils.Monitor;
import org.eclipse.tractusx.agents.utils.QuerySpec;
import org.eclipse.tractusx.agents.utils.TypeManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;

/**
 * DataManagement
 * is a service wrapper around the management endpoint
 * of the EDC control plane
 */
public class DataManagement {
    /**
     * some constants when interacting with control plane
     */
    public static final String DSP_PATH = "%1$s/api/v1/dsp";
    public static final String CATALOG_CALL = "%1$s/v2/catalog/request";
    // catalog request 0.5.>=1
    public static final String CATALOG_REQUEST_BODY = "{" +
            "\"@context\": {}," +
            "\"protocol\": \"dataspace-protocol-http\"," +
            "\"counterPartyAddress\": \"%2$s\", " +
            "\"counterPartyId\": \"%1$s\", " +
            "\"querySpec\": %3$s }";

    public static final String ASSET_CREATE_CALL = "%1$s%2$s/assets";
    public static final String ASSET_UPDATE_CALL = "%1$s%2$s/assets";

    /**
     * template for skill asset creation
     */
    public static final String SKILL_ASSET_CREATE_BODY = "{\n" +
            "    \"@context\": {\n" +
            "        \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"," +
            "        \"rdf\": \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\",\n" +
            "        \"rdfs\": \"http://www.w3.org/2000/01/rdf-schema#\",\n" +
            "        \"cx-common\": \"https://w3id.org/catenax/ontology/common#\",\n" +
            "        \"xsd\": \"http://www.w3.org/2001/XMLSchema#\",\n" +
            "        \"sh\": \"http://www.w3.org/ns/shacl#\"\n" +
            "    },\n" +
            "    \"@id\": \"%1$s\", \n" +
            "    \"properties\": {\n" +
            "            \"name\": \"%2$s\",\n" +
            "            \"description\": \"%3$s\",\n" +
            "            \"version\": \"%4$s\",\n" +
            "            \"contenttype\": \"application/json, application/xml\",\n" +
            "%5$s" +
            "            \"rdf:type\": \"cx-common:SkillAsset\",\n" +
            "            \"rdfs:isDefinedBy\": \"%6$s\",\n" +
            "            \"cx-common:implementsProtocol\": \"cx-common:Protocol?w3c:http:SKILL\",\n" +
            "            \"cx-common:distributionMode\": \"%7$s\",\n" +
            "            \"cx-common:isFederated\": \"%8$b^^xsd:boolean\"\n" +
            "    },\n" +
            "    \"privateProperties\": {\n" +
            "            \"cx-common:query\":%9$s\n" +
            "    },\n" +
            "    \"dataAddress\": {\n" +
            "        \"id\":\"%1$s\",\n" +
            "        \"@type\": \"DataAddress\",\n" +
            "        \"type\": \"cx-common:Protocol?w3c:http:SKILL\",\n" +
            "        \"baseUrl\":\"https://w3id.org/catenax\",\n" +
            "        \"proxyPath\": \"false\",\n" +
            "        \"proxyMethod\": \"true\",\n" +
            "        \"proxyQueryParams\": \"true\",\n" +
            "        \"proxyBody\": \"true\",\n" +
            "        \"cx-common:allowServicePattern\": %10$s,\n" +
            "        \"cx-common:denyServicePattern\": %11$s\n" +
            "    }\n" +
            "}\n";

    /**
     * template for graph asset creation
     */
    public static final String GRAPH_ASSET_CREATE_BODY = "{\n" +
            "    \"@context\": {\n" +
            "        \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"," +
            "        \"rdf\": \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\",\n" +
            "        \"rdfs\": \"http://www.w3.org/2000/01/rdf-schema#\",\n" +
            "        \"cx-common\": \"https://w3id.org/catenax/ontology/common#\",\n" +
            "        \"xsd\": \"http://www.w3.org/2001/XMLSchema#\",\n" +
            "        \"sh\": \"http://www.w3.org/ns/shacl#\"\n" +
            "    },\n" +
            "    \"@id\": \"%1$s\", \n" +
            "    \"properties\": {\n" +
            "            \"name\": \"%2$s\",\n" +
            "            \"description\": \"%3$s\",\n" +
            "            \"version\": \"%4$s\",\n" +
            "            \"contenttype\": \"application/json, application/xml\",\n" +
            "%5$s" +
            "            \"rdf:type\": \"cx-common:GraphAsset\",\n" +
            "            \"rdfs:isDefinedBy\": \"%6$s\",\n" +
            "            \"cx-common:implementsProtocol\": \"cx-common:Protocol?w3c:http:SPARQL\",\n" +
            "            \"sh:shapesGraph\": \"%7$s\",\n" +
            "            \"cx-common:isFederated\": \"%8$b^^xsd:boolean\"\n" +
            "    },\n" +
            "    \"privateProperties\": {\n" +
            "    },\n" +
            "    \"dataAddress\": {\n" +
            "        \"id\": \"%1$s\", \n" +
            "        \"@type\": \"DataAddress\",\n" +
            "        \"type\": \"cx-common:Protocol?w3c:http:SPARQL\",\n" +
            "        \"baseUrl\":\"https://w3id.org/catenax\",\n" +
            "        \"proxyPath\": \"false\",\n" +
            "        \"proxyMethod\": \"true\",\n" +
            "        \"proxyQueryParams\": \"true\",\n" +
            "        \"proxyBody\": \"true\",\n" +
            "        \"cx-common:allowServicePattern\": %9$s,\n" +
            "        \"cx-common:denyServicePattern\": %10$s\n" +
            "    }\n" +
            "}\n";

    public static final String ASSET_CALL = "%s%s/assets/request";

    // negotiation request 0.7.>=3
    public static final String NEGOTIATION_REQUEST_BODY =
            "{\n" +
                    "    \"@context\": {\n" +
                    "        \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\",\n" +
                    "        \"edc\": \"https://w3id.org/edc/v0.0.1/ns/\"\n" +
                    "    },\n" +
                    "    \"@type\": \"https://w3id.org/edc/v0.0.1/ns/ContractRequest\",\n" +
                    "    \"counterPartyAddress\": \"%1$s\",\n" +
                    "    \"protocol\": \"dataspace-protocol-http\",\n" +
                    "    \"providerId\": \"%2$s\",\n" +
                    "    \"policy\": {\n" +
                    "        \"@context\": \"http://www.w3.org/ns/odrl.jsonld\",\n" +
                    "        \"@type\": \"odrl:Offer\",\n" +
                    "        \"@id\": \"%4$s\",\n" +
                    "        \"target\": \"%5$s\",\n" +
                    "        \"assigner\": \"%3$s\",\n" +
                    "        \"odrl:permission\": %6$s,\n" +
                    "        \"odrl:prohibition\": %7$s,\n" +
                    "        \"odrl:obligation\": %8$s\n" +
                    "    }\n" +
                    "}";

    public static final String NEGOTIATION_INITIATE_CALL = "%s/v2/contractnegotiations";
    public static final String NEGOTIATION_CHECK_CALL = "%s/v2/contractnegotiations/%s";
    public static final String TRANSFER_INITIATE_CALL = "%s/v2/transferprocesses";

    public static final String TRANSFER_REQUEST_BODY = "{\n" +
            "    \"@context\": {\n" +
            "        \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"\n" +
            "    },\n" +
            "    \"@type\": \"TransferRequest\",\n" +
            "    \"assetId\": \"%1$s\",\n" +
            "    \"contractId\": \"%3$s\",\n" +
            "    \"counterPartyAddress\": \"%2$s\",\n" +
            "    \"dataDestination\": {\n" +
            "        \"type\": \"HttpProxy\"\n" +
            "    },\n" +
            "    \"protocol\": \"dataspace-protocol-http\",\n" +
            "    \"transferType\": \"HttpData-PULL\",\n" +
            "    \"privateProperties\": {},\n" +
            "    \"callbackAddresses\": [\n" +
            "      {\n" +
            "        \"transactional\": false,\n" +
            "        \"uri\": \"%4$s\",\n" +
            "        \"events\": [\n" +
            "          \"transfer.process.started\"\n" +
            "        ]\n" +
            "      }\n" +
            "    ] \n" +
            "}";
    public static final String TRANSFER_CHECK_CALL = "%s/v2/transferprocesses/%s";
    public static final String AGREEMENT_CHECK_CALL = "%s/v2/contractagreements/%s";

    /**
     * references to EDC services
     */
    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final AgentConfig config;

    /**
     * creates a service wrapper
     *
     * @param monitor     logger
     * @param typeManager serialization
     * @param httpClient  remoting
     * @param config      typed config
     */
    public DataManagement(Monitor monitor, TypeManager typeManager, OkHttpClient httpClient, AgentConfig config) {
        this.monitor = monitor;
        this.objectMapper = typeManager.getMapper();
        this.httpClient = httpClient;
        this.config = config;
    }

    /**
     * Search for a dedicated asset
     * TODO imperformant
     * TODO replace by accessing the federated data catalogue
     *
     * @param remoteControlPlaneIdsUrl url of the remote control plane ids endpoint
     * @param assetId                  (connector-unique) identifier of the asset
     * @return a collection of contract options to access the given asset
     * @throws IOException in case that the remote call did not succeed
     */
    public DcatCatalog findContractOffers(String remoteControlPlaneIdsUrl, String assetId) throws IOException {
        QuerySpec findAsset = QuerySpec.Builder.newInstance().filter(
                List.of(new Criterion("https://w3id.org/edc/v0.0.1/ns/id", "=", assetId))
        ).build();
        String partnerId = config.getDataspaceSynchronizationConnectors().entrySet()
                .stream().filter(entry -> entry.getValue().equals(remoteControlPlaneIdsUrl))
                .findFirst().map(entry -> entry.getKey()).orElse(UUID.randomUUID().toString());
        return getCatalog(partnerId, remoteControlPlaneIdsUrl, findAsset);
    }

    /**
     * Access the catalogue
     *
     * @param partnerId                business partner id
     * @param remoteControlPlaneIdsUrl url of the remote control plane ids endpoint
     * @param spec                     query specification
     * @return catalog object
     * @throws IOException in case something went wrong
     */
    public DcatCatalog getCatalog(String partnerId, String remoteControlPlaneIdsUrl, QuerySpec spec) throws IOException {
        var url = String.format(CATALOG_CALL, config.getControlPlaneManagementUrl());
        var catalogSpec = String.format(CATALOG_REQUEST_BODY, partnerId, String.format(DSP_PATH, remoteControlPlaneIdsUrl), objectMapper.writeValueAsString(spec));

        var request = new Request.Builder().url(url).post(RequestBody.create(catalogSpec, MediaType.parse("application/json")));
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            return JsonLd.processCatalog(body.string());
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * Access the (provider control plane) catalogue
     *
     * @param spec query specification
     * @return catalog object
     * @throws IOException in case something went wrong
     */
    public List<Asset> listAssets(QuerySpec spec) throws IOException {

        String version = "/v3";
        var url = String.format(ASSET_CALL, config.getControlPlaneManagementProviderUrl(), version);
        var assetObject = (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(spec));
        assetObject.set("@context", objectMapper.createObjectNode());
        var assetSpec = objectMapper.writeValueAsString(assetObject);

        var request = new Request.Builder().url(url).post(RequestBody.create(assetSpec, MediaType.parse("application/json")));
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            return JsonLd.processAssetList(body.string());
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * helper to create or update assets
     *
     * @param assetSpec json text of the asset description
     * @return a response listing the id of the created/updated asset
     * @throws IOException in case something goes wrong
     */
    protected IdResponse createOrUpdateAsset(String assetId, String assetSpec) throws IOException {
        String version = "/v3";
        var url = String.format(ASSET_CREATE_CALL, config.getControlPlaneManagementProviderUrl(), version);
        var request = new Request.Builder().url(url).post(RequestBody.create(assetSpec, MediaType.parse("application/json")));
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            ResponseBody body = response.body();

            if (!response.isSuccessful() || body == null) {

                if (response.code() != 409 || body == null) {
                    throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
                }

                url = String.format(ASSET_UPDATE_CALL, config.getControlPlaneManagementProviderUrl(), version, assetId);
                var patchRequest = new Request.Builder().url(url).put(RequestBody.create(assetSpec, MediaType.parse("application/json")));
                config.getControlPlaneManagementHeaders().forEach(patchRequest::addHeader);

                try (var patchResponse = httpClient.newCall(patchRequest.build()).execute()) {
                    body = patchResponse.body();
                    if (!patchResponse.isSuccessful() || body == null) {
                        monitor.warning(format("Failure in updating the resource at %s. Ignoring", url));
                        return null;
                    }
                }
            }
            return JsonLd.processIdResponse(body.string());
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * creates or updates a given skill asset
     *
     * @param assetId             key
     * @param name                of skill
     * @param description         of skill
     * @param version             of skill
     * @param contract            of skill
     * @param ontologies          of skill
     * @param distributionMode    of skill
     * @param isFederated         whether it should be distributed
     * @param query               of skill
     * @param allowServicePattern option allow service pattern
     * @param denyServicePattern  optional deny service pattern
     * @return idresponse
     * @throws IOException in case interaction with EDC went wrong
     */
    public IdResponse createOrUpdateSkill(String assetId, String name, String description, String version, String contract,
                                          String ontologies, String distributionMode, boolean isFederated, String query, String allowServicePattern,
                                          String denyServicePattern) throws IOException {
        if (contract != null) {
            contract = String.format("            \"cx-common:publishedUnderContract\": \"%1$s\",\n", contract);
        } else {
            contract = "";
        }
        String body = SKILL_ASSET_CREATE_BODY;

        if (allowServicePattern == null) {
            allowServicePattern = config.getServiceAllowPattern().pattern();
        }
        if (denyServicePattern == null) {
            denyServicePattern = config.getServiceDenyPattern().pattern();
        }

        allowServicePattern     = Json.createValue(allowServicePattern).toString();
        denyServicePattern = Json.createValue(denyServicePattern).toString();

        var assetSpec = String.format(body, assetId, name, description, version, contract, ontologies, distributionMode,
                isFederated, query, allowServicePattern, denyServicePattern);

        return createOrUpdateAsset(assetId, assetSpec);
    }

    /**
     * creates or updates a given graph asset
     *
     * @param assetId key
     * @param name of graph
     * @param description of graph
     * @param version of graph
     * @param contract of graph
     * @param ontologies of graph
     * @param shape of graph
     * @param isFederated whether it should be distributed
     * @param allowServicePattern option allow service pattern
     * @param denyServicePattern  optional deny service pattern
     * @return idresponse
     * @throws IOException in case interaction with EDC went wrong
     */
    public IdResponse createOrUpdateGraph(String assetId, String name, String description, String version, String contract,
                                          String ontologies, String shape, boolean isFederated, String allowServicePattern,
                                          String denyServicePattern) throws IOException {
        if (contract != null) {
            contract = String.format("            \"cx-common:publishedUnderContract\": \"%1$s\",\n", contract);
        } else {
            contract = "";
        }
        String body = GRAPH_ASSET_CREATE_BODY;
        var assetSpec = String.format(body, assetId, name, description, version, contract, ontologies, shape, isFederated, allowServicePattern, denyServicePattern);
        return createOrUpdateAsset(assetId, assetSpec);
    }

    /**
     * deletes an existing aseet
     *
     * @param assetId key of the asset
     * @return idresponse
     */

    public IdResponse deleteAsset(String assetId) throws IOException {
        String version = "/v3";
        var url = String.format(ASSET_UPDATE_CALL, config.getControlPlaneManagementProviderUrl(), version, assetId);
        var request = new Request.Builder().url(url).delete();
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);
        try (var response = httpClient.newCall(request.build()).execute()) {
            ResponseBody body = response.body();
            if (response.isSuccessful() && body != null) {
                return JsonLd.processIdResponse(body.string());
            } else {
                monitor.warning(format("Failure in calling the control plane at %s. Ignoring", url));
                return null;
            }
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * initiates negotation
     *
     * @param negotiationRequest outgoing request
     * @return negotiation id
     * @throws IOException in case something went wronf
     */
    public String initiateNegotiation(ContractNegotiationRequest negotiationRequest) throws IOException {
        var url = String.format(NEGOTIATION_INITIATE_CALL, config.getControlPlaneManagementUrl());

        // use a version specific call
        String template = NEGOTIATION_REQUEST_BODY;

        var negotiateSpec = String.format(template,
                negotiationRequest.getConnectorAddress(),
                negotiationRequest.getLocalBusinessPartnerNumber(),
                negotiationRequest.getRemoteBusinessPartnerNumber(),
                negotiationRequest.getOffer().getOfferId(),
                negotiationRequest.getOffer().getAssetId(),
                negotiationRequest.getOffer().getPolicy().getPermissionAsString(),
                negotiationRequest.getOffer().getPolicy().getObligationAsString(),
                negotiationRequest.getOffer().getPolicy().getProhibitionAsString()
                );

        var requestBody = RequestBody.create(negotiateSpec, MediaType.parse("application/json"));

        var request = new Request.Builder()
                .url(url)
                .post(requestBody);
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }


            var negotiationId = JsonLd.processIdResponse(body.string()).getId();

            monitor.debug("Started negotiation with ID: " + negotiationId);

            return negotiationId;
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * return state of contract negotiation
     *
     * @param negotiationId id of the negotation to inbestigate
     * @return status of the negotiation
     * @throws IOException in case something went wrong
     */
    public ContractNegotiation getNegotiation(String negotiationId) throws IOException {
        var url = String.format(NEGOTIATION_CHECK_CALL, config.getControlPlaneManagementUrl(), negotiationId);
        var request = new Request.Builder()
                .url(url);
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            var negotiation = JsonLd.processContractNegotiation(body.string());
            monitor.debug(format("Negotiation %s is in state '%s' (agreementId: %s)", negotiationId, negotiation.getState(), negotiation.getContractAgreementId()));

            return negotiation;
        } catch (Exception e) {
            monitor.severe(format("Error in calling the Control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * get a contract agreement by its id
     *
     * @param agreementId id of the agreement
     * @return contract agreement
     * @throws IOException something wild happens
     */
    public ContractAgreement getAgreement(String agreementId) throws IOException {
        var url = String.format(AGREEMENT_CHECK_CALL, config.getControlPlaneManagementUrl(), URLEncoder.encode(agreementId, StandardCharsets.UTF_8));
        var request = new Request.Builder()
                .url(url);
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            var agreement = JsonLd.processContractAgreement(body.string());
            monitor.debug(format("Agreement %s found for asset %s", agreementId, agreement.getAssetId()));

            return agreement;
        } catch (Exception e) {
            monitor.severe(format("Error in calling the Control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * Initiates a transfer
     *
     * @param transferRequest request
     * @return transfer id
     * @throws IOException in case something went wrong
     */
    public String initiateHttpProxyTransferProcess(TransferRequest transferRequest) throws IOException {
        var url = String.format(TRANSFER_INITIATE_CALL, config.getControlPlaneManagementUrl());

        var transferSpec = String.format(TRANSFER_REQUEST_BODY,
                transferRequest.getAssetId(),
                transferRequest.getConnectorAddress(),
                transferRequest.getContractId(),
                transferRequest.getCallbackAddresses().get(0).getUri());

        var requestBody = RequestBody.create(transferSpec, MediaType.parse("application/json"));

        var request = new Request.Builder()
                .url(url)
                .post(requestBody);
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            // For debugging purposes:
            // var transferProcessId = TransferId.Builder.newInstance().id(body.string()).build();
            var transferProcessId = JsonLd.processIdResponse(body.string()).getId();

            monitor.debug(format("Transfer process (%s) initiated", transferProcessId));

            return transferProcessId;
        } catch (Exception e) {
            monitor.severe(format("Error in calling the control plane at %s", url), e);
            throw e;
        }
    }

    /**
     * return state of transfer process
     *
     * @param transferProcessId id of the transfer process
     * @return state of the transfer process
     * @throws IOException in case something went wrong
     */
    public TransferProcess getTransfer(String transferProcessId) throws IOException {
        var url = String.format(TRANSFER_CHECK_CALL, config.getControlPlaneManagementUrl(), transferProcessId);
        var request = new Request.Builder()
                .url(url);
        config.getControlPlaneManagementHeaders().forEach(request::addHeader);

        try (var response = httpClient.newCall(request.build()).execute()) {
            var body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new InternalServerErrorException(format("Control plane responded with: %s %s", response.code(), body != null ? body.string() : ""));
            }

            var process = JsonLd.processTransferProcess(body.string());
            monitor.info(format("Transfer %s is in state '%s'", transferProcessId, process.getState()));

            return process;
        } catch (Exception e) {
            monitor.severe(format("Error in calling the Control plane at %s", url), e);
            throw e;
        }
    }

}