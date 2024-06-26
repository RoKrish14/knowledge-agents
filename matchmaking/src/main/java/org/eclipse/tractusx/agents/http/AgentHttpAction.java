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
package org.eclipse.tractusx.agents.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.http.HttpStatus;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.eclipse.tractusx.agents.TupleSet;
import org.slf4j.Logger;

import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * HttpAction which may either contain
 * a query or a predefined skill. In each case
 * the parameterization/input binding can be done either by
 * url parameters, by a binding set body or both.
 */
public class AgentHttpAction extends HttpAction {
    final String skill;
    final String graphs;
    final TupleSet tupleSet = new TupleSet();

    /**
     * regexes to deal with url parameters
     */
    public static final String URL_PARAM_REGEX = "(?<key>[^=&]+)=(?<value>[^&]+)";
    public static final Pattern URL_PARAM_PATTERN = Pattern.compile(URL_PARAM_REGEX);
    public static final String RESULTSET_CONTENT_TYPE = "application/sparql-results+json";

    /**
     * creates a new http action
     *
     * @param id call id
     * @param logger the used logging output
     * @param request servlet input
     * @param response servlet output
     * @param skill option skill reference
     */
    public AgentHttpAction(long id, Logger logger, HttpServletRequest request, HttpServletResponse response, String skill, String graphs) {
        super(id, logger, ActionCategory.ACTION, request, response);
        this.skill = skill;
        this.graphs = graphs;
        parseArgs(request, response);
        parseBody(request, response);
    }

    /**
     * parses parameters
     */
    protected void parseArgs(HttpServletRequest request, HttpServletResponse response) {
        String params = "";
        String uriParams = request.getQueryString();
        if (uriParams != null) {
            params = URLDecoder.decode(uriParams, UTF_8);
        }
        Matcher paramMatcher = URL_PARAM_PATTERN.matcher(params);
        Stack<TupleSet> ts = new Stack<>();
        ts.push(tupleSet);
        while (paramMatcher.find()) {
            String key = paramMatcher.group("key");
            String value = paramMatcher.group("value");
            while (key.startsWith("(")) {
                key = key.substring(1);
                ts.push(new TupleSet());
            }
            if (key.length() <= 0) {
                response.setStatus(HttpStatus.SC_BAD_REQUEST);
                return;
            }
            String realValue = value.replace(")", "");
            if (value.length() <= 0) {
                response.setStatus(HttpStatus.SC_BAD_REQUEST);
                return;
            }
            try {
                if (!"asset".equals(key) && !"query".equals(key)) {
                    ts.peek().add(key, realValue);
                }
            } catch (Exception e) {
                response.setStatus(HttpStatus.SC_BAD_REQUEST);
                return;
            }
            while (value.endsWith(")")) {
                TupleSet set1 = ts.pop();
                ts.peek().merge(set1);
                value = value.substring(0, value.length() - 1);
            }
        }
    }

    /**
     * parses the body
     */
    protected void parseBody(HttpServletRequest request, HttpServletResponse response) {
        if (RESULTSET_CONTENT_TYPE.equals(request.getContentType())) {
            ObjectMapper om = new ObjectMapper();
            try {
                JsonNode bindingSet = om.readTree(request.getInputStream());
                ArrayNode bindings = ((ArrayNode) bindingSet.get("results").get("bindings"));
                for (int count = 0; count < bindings.size(); count++) {
                    TupleSet ts = new TupleSet();
                    JsonNode binding = bindings.get(count);
                    Iterator<String> vars = binding.fieldNames();
                    while (vars.hasNext()) {
                        String var = vars.next();
                        JsonNode value = binding.get(var).get("value");
                        ts.add(var, value.textValue());
                    }
                    tupleSet.merge(ts);
                }
            } catch (Exception e) {
                response.setStatus(HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    /**
     * access
     *
     * @return optional skill
     */
    public String getSkill() {
        return skill;
    }

    /**
     * access
     *
     * @return optional skill
     */
    public String getGraphs() {
        return graphs;
    }

    /**
     * access
     *
     * @return the actual input bindings
     */
    public TupleSet getInputBindings() {
        return tupleSet;
    }
}
