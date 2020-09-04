/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.aws.cloudformation.traits;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

public class ResourceTraitTest {
    @Test
    public void loadsFromModel() {
        Model result = Model.assembler()
                .discoverModels(getClass().getClassLoader())
                .addImport(getClass().getResource("cfn-resources.smithy"))
                .assemble()
                .unwrap();

        Shape fooResource = result.expectShape(ShapeId.from("smithy.example#FooResource"));
        assertTrue(fooResource.hasTrait(ResourceTrait.class));
        ResourceTrait fooTrait = fooResource.expectTrait(ResourceTrait.class);
        assertThat(fooTrait.getName(), equalTo("FooResource"));
        assertTrue(fooTrait.getAdditionalSchemas().isEmpty());

        Shape barResource = result.expectShape(ShapeId.from("smithy.example#BarResource"));
        assertTrue(barResource.hasTrait(ResourceTrait.class));
        ResourceTrait barTrait = barResource.expectTrait(ResourceTrait.class);
        assertThat(barTrait.getName(), equalTo("CustomResource"));
        assertFalse(barTrait.getAdditionalSchemas().isEmpty());
        assertThat(barTrait.getAdditionalSchemas(), contains(ShapeId.from("smithy.example#ExtraBarRequest")));
    }

    @Test
    public void handlesNameProperty() {
        Model result = Model.assembler()
                .discoverModels(getClass().getClassLoader())
                .addImport(getClass().getResource("test-service.smithy"))
                .assemble()
                .unwrap();

        assertThat(
                result.expectShape(ShapeId.from("smithy.example#FooResource"))
                        .expectTrait(ResourceTrait.class).getName(),
                equalTo("FooResource"));
        assertThat(
                result.expectShape(ShapeId.from("smithy.example#BarResource"))
                        .expectTrait(ResourceTrait.class).getName(),
                equalTo("Bar"));
        assertThat(
                result.expectShape(ShapeId.from("smithy.example#BazResource"))
                        .expectTrait(ResourceTrait.class).getName(),
                equalTo("Basil"));
    }
}
