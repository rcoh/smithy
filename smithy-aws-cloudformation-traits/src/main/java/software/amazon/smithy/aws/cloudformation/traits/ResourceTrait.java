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

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

/**
 * Indicates that a Smithy resource is a CloudFormation resource.
 */
public final class ResourceTrait extends AbstractTrait implements ToSmithyBuilder<ResourceTrait> {
    public static final ShapeId ID = ShapeId.from("aws.cloudformation#resource");
    private static final String NAME = "name";
    private static final String ADDITIONAL_SCHEMAS = "additionalSchemas";
    private static final List<String> PROPERTIES = ListUtils.of(NAME, ADDITIONAL_SCHEMAS);

    private final String defaultName;
    private final String name;
    private final List<ShapeId> additionalSchemas;

    private ResourceTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        defaultName = builder.defaultName;
        name = builder.name;
        additionalSchemas = ListUtils.copyOf(builder.additionalSchemas);
    }

    /**
     * Get the AWS CloudFormation resource name.
     *
     * @return Returns the name.
     */
    public String getName() {
        return name == null ? defaultName : name;
    }

    /**
     * Get the Smithy structure shape Ids for additional schema properties.
     *
     * @return Returns the additional schema shape Ids.
     */
    public List<ShapeId> getAdditionalSchemas() {
        return additionalSchemas;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Node createNode() {
        ObjectNode node = Node.objectNode();
        if (name != null) {
            node = node.withMember(NAME, name);
        }
        if (!additionalSchemas.isEmpty()) {
            ArrayNode schemas = additionalSchemas.stream()
                    .map(ShapeId::toString)
                    .map(Node::from)
                    .collect(ArrayNode.collect());
            node = node.withMember("additionalSchemas", schemas);
        }
        return node;
    }

    @Override
    public SmithyBuilder<ResourceTrait> toBuilder() {
        return builder().name(name).additionalSchemas(additionalSchemas);
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            Builder builder = builder().sourceLocation(value);
            ObjectNode objectNode = value.expectObjectNode();
            // Use a hidden defaultName property so we don't write out the
            // Shape's name when defaulting.
            builder.defaultName(target.getName());
            objectNode.getStringMember(NAME).ifPresent(node -> builder.name(node.getValue()));
            // Convert this ArrayNode of StringNodes to a List of ShapeId
            objectNode.getArrayMember(ADDITIONAL_SCHEMAS)
                    .map(array -> array.getElementsAs(n -> ShapeId.from(n.expectStringNode().getValue())))
                    .ifPresent(builder::additionalSchemas);
            return builder.build();
        }
    }

    public static final class Builder extends AbstractTraitBuilder<ResourceTrait, Builder> {
        private String defaultName;
        private String name;
        private final List<ShapeId> additionalSchemas = new ArrayList<>();

        private Builder() {}

        @Override
        public ResourceTrait build() {
            return new ResourceTrait(this);
        }

        public Builder defaultName(String defaultName) {
            this.defaultName = defaultName;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addAdditionalSchema(ShapeId additionalSchema) {
            this.additionalSchemas.add(additionalSchema);
            return this;
        }

        public Builder additionalSchemas(List<ShapeId> additionalSchemas) {
            this.additionalSchemas.clear();
            this.additionalSchemas.addAll(additionalSchemas);
            return this;
        }
    }
}
