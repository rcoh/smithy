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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.aws.cloudformation.traits.ResourceIndex.Mutability;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SetUtils;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

/**
 * Contains extracted resource property information.
 */
public final class ResourcePropertyDefinition implements ToSmithyBuilder<ResourcePropertyDefinition> {
    private final ShapeId shapeId;
    private final Set<Mutability> mutabilities;
    private final boolean hasExplicitMutability;

    private ResourcePropertyDefinition(Builder builder) {
        shapeId = Objects.requireNonNull(builder.shapeId);
        mutabilities = SetUtils.copyOf(builder.mutabilities);
        hasExplicitMutability = builder.hasExplicitMutability;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the shape ID used to represent this property.
     *
     * @return Returns the shape ID.
     */
    public ShapeId getShapeId() {
        return shapeId;
    }

    /**
     * Returns true if the property's mutability was configured explicitly
     * by the use of a trait instead of derived through its lifecycle
     * bindings within a resource.
     *
     * @return Returns true if the mutability is explicitly defined by a trait.
     *
     * @see MutabilityTrait
     */
    public boolean hasExplicitMutability() {
        return hasExplicitMutability;
    }

    /**
     * Gets all of the CloudFormation-specific property mutability options
     * associated with this resource property.
     *
     * @return Returns the mutabilities.
     */
    public Set<Mutability> getMutabilities() {
        return mutabilities;
    }

    @Override
    public Builder toBuilder() {
        return builder()
                .shapeId(shapeId)
                .mutabilities(mutabilities);
    }

    public static final class Builder implements SmithyBuilder<ResourcePropertyDefinition> {
        private ShapeId shapeId;
        private Set<Mutability> mutabilities = new HashSet<>();
        private boolean hasExplicitMutability = false;

        @Override
        public ResourcePropertyDefinition build() {
            return new ResourcePropertyDefinition(this);
        }

        public Builder shapeId(ShapeId shapeId) {
            this.shapeId = shapeId;
            return this;
        }

        public Builder mutabilities(Set<Mutability> mutabilities) {
            this.mutabilities = mutabilities;
            return this;
        }

        public Builder hasExplicitMutability(boolean hasExplicitMutability) {
            this.hasExplicitMutability = hasExplicitMutability;
            return this;
        }
    }
}
