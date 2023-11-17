// Copyright (c) 2022,2023 Contributors to the Eclipse Foundation
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
package org.eclipse.tractusx.agents.conforming.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import javax.validation.Valid;

/**
 * JsonResultset
 */
@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.JavaJerseyServerCodegen", date = "2023-03-23T11:28:11.277776230Z[GMT]")
public class JsonResultset {
    @JsonProperty("head")
    private JsonResultsetHead head = null;

    @JsonProperty("results")
    private JsonResultsetResults results = null;

    public JsonResultset head(JsonResultsetHead head) {
        this.head = head;
        return this;
    }

    /**
     * Get head
     *
     * @return head
     **/
    @JsonProperty("head")
    @Schema(description = "")
    @Valid
    public JsonResultsetHead getHead() {
        return head;
    }

    public void setHead(JsonResultsetHead head) {
        this.head = head;
    }

    public JsonResultset results(JsonResultsetResults results) {
        this.results = results;
        return this;
    }

    /**
     * Get results
     *
     * @return results
     **/
    @JsonProperty("results")
    @Schema(description = "")
    @Valid
    public JsonResultsetResults getResults() {
        return results;
    }

    public void setResults(JsonResultsetResults results) {
        this.results = results;
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, results);
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonResultset jsonResultset = (JsonResultset) o;
        return Objects.equals(this.head, jsonResultset.head) &&
                Objects.equals(this.results, jsonResultset.results);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class JsonResultset {\n");

        sb.append("    head: ").append(toIndentedString(head)).append("\n");
        sb.append("    results: ").append(toIndentedString(results)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
