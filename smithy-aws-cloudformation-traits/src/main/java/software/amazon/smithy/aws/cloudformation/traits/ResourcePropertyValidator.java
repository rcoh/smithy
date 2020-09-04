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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

/**
 * Validates that derived CloudFormation properties all have the same target.
 */
public final class ResourcePropertyValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        List<ValidationEvent> events = new ArrayList<>();

        OperationIndex operationIndex = OperationIndex.of(model);
        model.shapes(ResourceShape.class)
                .flatMap(shape -> Trait.flatMapStream(shape, ResourceTrait.class))
                .map(pair -> validateResourceProperties(model, operationIndex, pair.getLeft(), pair.getRight()))
                .forEach(events::addAll);

        return events;
    }

    private List<ValidationEvent> validateResourceProperties(
            Model model,
            OperationIndex operationIndex,
            ResourceShape resource,
            ResourceTrait trait
    ) {
        List<ValidationEvent> events = new ArrayList<>();

        Map<String, Set<ShapeId>> resourceProperties = getResourceProperties(model, operationIndex, resource, trait);
        for (Map.Entry<String, Set<ShapeId>> property : resourceProperties.entrySet()) {
            if (property.getValue().size() > 1) {
                events.add(error(resource, String.format("The %s property of the %s CloudFormation resource targets "
                        + "multiple shapes: %s. This should be resolved in the model or one of the members should be "
                        + "excluded from the conversion.", trait.getName(), property.getKey(), property.getValue())));
            }
        }

        return events;
    }

    private Map<String, Set<ShapeId>> getResourceProperties(
            Model model,
            OperationIndex operationIndex,
            ResourceShape resource,
            ResourceTrait trait
    ) {
        Map<String, Set<ShapeId>> resourceProperties = new HashMap<>();

        // Use the read lifecycle's input to collect the additional identifiers
        // and its output to collect readable properties.
        resource.getRead().ifPresent(operationId -> {
            operationIndex.getOutput(operationId).ifPresent(output ->
                    computeResourceProperties(resourceProperties, output));
        });

        // Use the put lifecycle's input to collect put-able properties.
        resource.getPut().ifPresent(operationId -> {
            operationIndex.getInput(operationId).ifPresent(input ->
                    computeResourceProperties(resourceProperties, input));
        });

        // Use the create lifecycle's input to collect creatable properties.
        resource.getCreate().ifPresent(operationId -> {
            operationIndex.getInput(operationId).ifPresent(input ->
                    computeResourceProperties(resourceProperties, input));
        });

        // Use the update lifecycle's input to collect writeable properties.
        resource.getUpdate().ifPresent(operationId -> {
            operationIndex.getInput(operationId).ifPresent(input ->
                    computeResourceProperties(resourceProperties, input));
        });

        // Apply any members found through the trait's additionalSchemas property.
        for (ShapeId additionalSchema : trait.getAdditionalSchemas()) {
            StructureShape shape = model.expectShape(additionalSchema, StructureShape.class);
            computeResourceProperties(resourceProperties, shape);
        }

        return resourceProperties;
    }

    private void computeResourceProperties(Map<String, Set<ShapeId>> resourceProperties, StructureShape shape) {
        for (Map.Entry<String, MemberShape> memberEntry : shape.getAllMembers().entrySet()) {
            MemberShape memberShape = memberEntry.getValue();

            // Skip explicitly excluded property definitions.
            if (memberShape.hasTrait(ExcludePropertyTrait.ID)) {
                continue;
            }

            // Use the correct property name.
            String propertyName = memberShape.getTrait(PropertyNameTrait.class)
                    .map(PropertyNameTrait::getValue)
                    .orElse(memberEntry.getKey());
            resourceProperties.computeIfAbsent(propertyName, name -> new TreeSet<>())
                    .add(memberShape.getTarget());
        }
    }
}
