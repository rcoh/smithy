/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.model.loader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.PrivateTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

public class PreludeTest {
    @Test
    public void checksIfShapeIdIsInPrelude() {
        assertTrue(Prelude.isPreludeShape(ShapeId.from("smithy.api#String")));
        assertTrue(Prelude.isPreludeShape(ShapeId.from("smithy.api#PrimitiveLong")));
        assertTrue(Prelude.isPreludeShape(ShapeId.from("smithy.api#Foo")));
        assertFalse(Prelude.isPreludeShape(ShapeId.from("foo.baz#Bar")));
    }

    @Test
    public void checksIfShapeIdIsInPublicPrelude() {
        assertTrue(Prelude.isPublicPreludeShape(ShapeId.from("smithy.api#String")));
        assertTrue(Prelude.isPublicPreludeShape(ShapeId.from("smithy.api#PrimitiveLong")));
        assertFalse(Prelude.isPublicPreludeShape(ShapeId.from("smithy.api#IdRefTrait")));
        assertFalse(Prelude.isPreludeShape(ShapeId.from("foo.baz#Bar")));
    }

    @Test
    public void checkIfPrivateShapesAreReferenced() {
        Model model = Prelude.getPreludeModel();

        ModelTransformer transformer = ModelTransformer.create();
        Model result = transformer.scrubTraitDefinitions(model);
        Set<ShapeId> unreferencedPrivateShapes = result.shapes()
                .filter(shape -> shape.hasTrait(PrivateTrait.class))
                .map(Shape::getId)
                .collect(Collectors.toSet());

        assertThat(unreferencedPrivateShapes, emptyCollectionOf(ShapeId.class));
    }
}