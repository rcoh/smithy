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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.IdentifierBindingIndex;
import software.amazon.smithy.model.knowledge.KnowledgeIndex;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.MapUtils;
import software.amazon.smithy.utils.SetUtils;

/**
 * Index of resources to their CloudFormation identifiers
 * and properties.
 *
 * <p>This index performs no validation that the identifiers
 * and reference valid shapes.
 */
public final class ResourceIndex implements KnowledgeIndex {

    static final Set<Mutability> FULLY_MUTABLE = SetUtils.of(
            Mutability.CREATE, Mutability.READ, Mutability.WRITE);

    private final Model model;
    private final Map<ShapeId, Map<String, ResourcePropertyDefinition>> resourcePropertyMutabilities = new HashMap<>();
    private final Map<ShapeId, Set<ShapeId>> resourceExcludedProperties = new HashMap<>();
    private final Map<ShapeId, Set<String>> resourcePrimaryIdentifiers = new HashMap<>();
    private final Map<ShapeId, List<Set<String>>> resourceAdditionalIdentifiers = new HashMap<>();

    /**
     * CloudFormation-specific property mutability options.
     */
    public enum Mutability {
        CREATE,
        READ,
        WRITE
    }

    public ResourceIndex(Model model) {
        this.model = model;

        OperationIndex operationIndex = OperationIndex.of(model);
        model.shapes(ResourceShape.class)
                .flatMap(shape -> Trait.flatMapStream(shape, ResourceTrait.class))
                .forEach(pair -> {
                    ResourceShape resource = pair.getLeft();
                    ShapeId resourceId = resource.getId();

                    // Start with the explicit resource identifiers.
                    resourcePrimaryIdentifiers.put(resourceId, SetUtils.copyOf(resource.getIdentifiers().keySet()));
                    setIdentifierMutabilities(resource);

                    // Use the read lifecycle's input to collect the additional identifiers
                    // and its output to collect readable properties.
                    resource.getRead().ifPresent(operationId -> {
                        operationIndex.getInput(operationId).ifPresent(input -> {
                            addAdditionalIdentifiers(resource, computeResourceAdditionalIdentifiers(input));
                        });
                        operationIndex.getOutput(operationId).ifPresent(output -> {
                            updatePropertyMutabilities(resourceId, operationId, output,
                                    SetUtils.of(Mutability.READ), this::addReadMutability);
                        });
                    });

                    // Use the put lifecycle's input to collect put-able properties.
                    resource.getPut().ifPresent(operationId -> {
                        operationIndex.getInput(operationId).ifPresent(input -> {
                            updatePropertyMutabilities(resourceId, operationId, input,
                                    SetUtils.of(Mutability.CREATE, Mutability.WRITE), this::addPutMutability);
                        });
                    });

                    // Use the create lifecycle's input to collect creatable properties.
                    resource.getCreate().ifPresent(operationId -> {
                        operationIndex.getInput(operationId).ifPresent(input -> {
                            updatePropertyMutabilities(resourceId, operationId, input,
                                    SetUtils.of(Mutability.CREATE), this::addCreateMutability);
                        });
                    });

                    // Use the update lifecycle's input to collect writeable properties.
                    resource.getUpdate().ifPresent(operationId -> {
                        operationIndex.getInput(operationId).ifPresent(input -> {
                            updatePropertyMutabilities(resourceId, operationId, input,
                                    SetUtils.of(Mutability.WRITE), this::addWriteMutability);
                        });
                    });

                    // Apply any members found through the trait's additionalSchemas property.
                    for (ShapeId additionalSchema : pair.getRight().getAdditionalSchemas()) {
                        StructureShape shape = model.expectShape(additionalSchema, StructureShape.class);
                        updatePropertyMutabilities(resourceId, null, shape,
                                SetUtils.of(), Function.identity());
                    }
                });
    }

    public static ResourceIndex of(Model model) {
        return model.getKnowledge(ResourceIndex.class, ResourceIndex::new);
    }

    /**
     * Get all members of the CloudFormation resource.
     *
     * @param resource ShapeID of a resource.
     * @return Returns all members that map to CloudFormation resource
     *   properties.
     */
    public Map<String, ResourcePropertyDefinition> getProperties(ToShapeId resource) {
        return resourcePropertyMutabilities.getOrDefault(resource.toShapeId(), MapUtils.of())
                .entrySet().stream()
                .filter(entry -> !getExcludedProperties(resource).contains(entry.getValue().getShapeId()))
                .collect(MapUtils.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Gets the specified member of the CloudFormation resource.
     *
     * @param resource ShapeID of a resource
     * @param propertyName Name of the property to retrieve
     * @return The property definition.
     */
    public Optional<ResourcePropertyDefinition> getProperty(ToShapeId resource, String propertyName) {
        return Optional.ofNullable(getProperties(resource).get(propertyName));
    }

    /**
     * Get create-specifiable-only members of the CloudFormation resource.
     *
     * These properties can be specified only during resource creation and
     * can be returned in a `read` or `list` request.
     *
     * @param resource ShapeID of a resource.
     * @return Returns create-only member names that map to CloudFormation resource
     *   properties.
     */
    public Set<String> getCreateOnlyProperties(ToShapeId resource) {
        return getConstrainedProperties(resource, definition -> {
            Set<Mutability> mutabilities = definition.getMutabilities();
            return mutabilities.contains(Mutability.CREATE) && !mutabilities.contains(Mutability.WRITE);
        });
    }

    /**
     * Get read-only members of the CloudFormation resource.
     *
     * These properties can be returned by a `read` or `list` request,
     * but cannot be set by the user.
     *
     * @param resource ShapeID of a resource.
     * @return Returns read-only member names that map to CloudFormation resource
     *   properties.
     */
    public Set<String> getReadOnlyProperties(ToShapeId resource) {
        return getConstrainedProperties(resource, definition -> {
            Set<Mutability> mutabilities = definition.getMutabilities();
            return mutabilities.size() == 1 && mutabilities.contains(Mutability.READ);
        });
    }

    /**
     * Get write-only members of the CloudFormation resource.
     *
     * These properties can be specified by the user, but cannot be
     * returned by a `read` or `list` request.
     *
     * @param resource ShapeID of a resource.
     * @return Returns write-only member names that map to CloudFormation resource
     *   properties.
     */
    public Set<String> getWriteOnlyProperties(ToShapeId resource) {
        return getConstrainedProperties(resource, definition -> {
            Set<Mutability> mutabilities = definition.getMutabilities();
            // Create and non-read properties need to be set as createOnly and writeOnly.
            if (mutabilities.size() == 1 && mutabilities.contains(Mutability.CREATE)) {
                return true;
            }

            // Otherwise, create and update, or update only become writeOnly.
            return mutabilities.contains(Mutability.WRITE) && !mutabilities.contains(Mutability.READ);
        });
    }

    private Set<String> getConstrainedProperties(
            ToShapeId resource,
            Predicate<ResourcePropertyDefinition> constraint
    ) {
        return getProperties(resource)
                .entrySet()
                .stream()
                .filter(property -> constraint.test(property.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Get members that have been explicitly excluded from the CloudFormation
     * resource.
     *
     * @param resource ShapeID of a resource.
     * @return Returns members that have been excluded from a CloudFormation
     *   resource.
     */
    public Set<ShapeId> getExcludedProperties(ToShapeId resource) {
        return resourceExcludedProperties.getOrDefault(resource.toShapeId(), SetUtils.of());
    }

    /**
     * Gets a set of member shape ids that represent the primary way
     * to identify a CloudFormation resource.
     *
     * @param resource ShapeID of a resource.
     * @return Returns the identifier set primarily used to access a
     *   CloudFormation resource.
     */
    public Set<String> getPrimaryIdentifiers(ToShapeId resource) {
        return resourcePrimaryIdentifiers.get(resource.toShapeId());
    }

    /**
     * Get a list of sets of member shape ids, each set can be used to identify
     * the CloudFormation resource in addition to its primary identifier(s).
     *
     * @param resource ShapeID of a resource.
     * @return Returns identifier sets used to access a CloudFormation resource.
     */
    public List<Set<String>> getAdditionalIdentifiers(ToShapeId resource) {
        return resourceAdditionalIdentifiers.getOrDefault(resource.toShapeId(), ListUtils.of());
    }

    private void setIdentifierMutabilities(ResourceShape resource) {
        Set<Mutability> mutability = getDefaultIdentifierMutabilities(resource);

        ShapeId resourceId = resource.getId();

        resource.getIdentifiers().forEach((name, shape) -> {
            setResourceProperty(resourceId, name, ResourcePropertyDefinition.builder()
                    .hasExplicitMutability(true)
                    .mutabilities(mutability)
                    .shapeId(shape)
                    .build());
        });
    }

    private void setResourceProperty(ShapeId resourceId, String name, ResourcePropertyDefinition property) {
        Map<String, ResourcePropertyDefinition> resourceProperties =
                resourcePropertyMutabilities.getOrDefault(resourceId, new HashMap<>());
        resourceProperties.put(name, property);
        resourcePropertyMutabilities.put(resourceId, resourceProperties);
    }

    private Set<Mutability> getDefaultIdentifierMutabilities(ResourceShape resource) {
        // If we have a put operation, the identifier will be specified
        // on creation. Otherwise, it's read only.
        if (resource.getPut().isPresent()) {
            return SetUtils.of(Mutability.CREATE, Mutability.READ);
        }

        return SetUtils.of(Mutability.READ);
    }

    private List<Map<String, ShapeId>> computeResourceAdditionalIdentifiers(StructureShape readInput) {
        List<Map<String, ShapeId>> identifiers = new ArrayList<>();
        for (MemberShape member : readInput.members()) {
            if (!member.hasTrait(AdditionalIdentifierTrait.class)) {
                continue;
            }

            identifiers.add(MapUtils.of(member.getMemberName(), member.getId()));
        }
        return identifiers;
    }

    private void addAdditionalIdentifiers(ResourceShape resource, List<Map<String, ShapeId>> addedIdentifiers) {
        if (addedIdentifiers.isEmpty()) {
            return;
        }
        ShapeId resourceId = resource.getId();

        List<Set<String>> newIdentifierNames = new ArrayList<>();
        // Make sure we have properties entries for the additional identifiers.
        for (Map<String, ShapeId> addedIdentifier : addedIdentifiers) {
            for (Map.Entry<String, ShapeId> idEntry : addedIdentifier.entrySet()) {
                setResourceProperty(resourceId, idEntry.getKey(), ResourcePropertyDefinition.builder()
                        .mutabilities(SetUtils.of(Mutability.READ))
                        .shapeId(idEntry.getValue())
                        .build());
            }
            newIdentifierNames.add(addedIdentifier.keySet());
        }

        List<Set<String>> currentIdentifiers =
                resourceAdditionalIdentifiers.getOrDefault(resourceId, new ArrayList<>());
        currentIdentifiers.addAll(newIdentifierNames);
        resourceAdditionalIdentifiers.put(resourceId, currentIdentifiers);
    }

    private void updatePropertyMutabilities(
            ShapeId resourceId,
            ShapeId operationId,
            StructureShape propertyContainer,
            Set<Mutability> defaultMutabilities,
            Function<Set<Mutability>, Set<Mutability>> updater
    ) {
        addExcludedProperties(resourceId, propertyContainer);

        for (MemberShape member : propertyContainer.members()) {
            // We've explicitly set identifier mutability based on how the
            // resource instance comes about, so only handle non-identifiers.
            if (operationMemberIsIdentifier(resourceId, operationId, member)) {
                continue;
            }

            String memberName = member.getMemberName();
            ResourcePropertyDefinition memberProperty = getProperties(resourceId).get(memberName);
            Set<Mutability> explicitMutability = getExplicitMutability(member, memberProperty);

            if (memberProperty != null) {
                // Validate that members with the same name target the same shape.
                model.getShape(memberProperty.getShapeId())
                        .flatMap(Shape::asMemberShape)
                        .filter(shape -> !member.getTarget().equals(shape.getTarget()))
                        .ifPresent(shape -> {
                            throw new RuntimeException(String.format("The derived CloudFormation resource "
                                    + "property for %s is composed of members that target different shapes: %s and %s",
                                    memberName, member.getTarget(), shape.getTarget()));
                        });

                // Apply updates to the mutability of the property.
                if (!memberProperty.hasExplicitMutability()) {
                    memberProperty = memberProperty.toBuilder()
                            .mutabilities(updater.apply(memberProperty.getMutabilities()))
                            .build();
                }
            } else {
                // Set the correct mutability for this new property.
                Set<Mutability> mutabilities = !explicitMutability.isEmpty()
                        ? explicitMutability
                        : defaultMutabilities;
                memberProperty = ResourcePropertyDefinition.builder()
                        .shapeId(member.getId())
                        .mutabilities(mutabilities)
                        .hasExplicitMutability(!explicitMutability.isEmpty())
                        .build();
            }

            setResourceProperty(resourceId, memberName, memberProperty);
        }
    }

    private void addExcludedProperties(ShapeId resourceId, StructureShape propertyContainer) {
        Set<ShapeId> currentExcludedProperties =
                resourceExcludedProperties.getOrDefault(resourceId, new HashSet<>());
        currentExcludedProperties.addAll(propertyContainer.accept(new ExcludedPropertiesVisitor()));
        resourceExcludedProperties.put(resourceId, currentExcludedProperties);
    }

    private boolean operationMemberIsIdentifier(ShapeId resourceId, ShapeId operationId, MemberShape member) {
        // The operationId will be null in the case of additionalSchemas, so
        // we shouldn't worry if these are bound to operation identifiers.
        if (operationId == null) {
            return false;
        }

        IdentifierBindingIndex index = IdentifierBindingIndex.of(model);
        Map<String, String> bindings = index.getOperationBindings(resourceId, operationId);
        String memberName = member.getMemberName();
        // Check for literal identifier bindings.
        for (String bindingMemberName : bindings.values()) {
            if (memberName.equals(bindingMemberName)) {
                return true;
            }
        }

        return false;
    }

    private Set<Mutability> getExplicitMutability(
            MemberShape member,
            ResourcePropertyDefinition memberProperty
    ) {
        if (memberProperty != null && memberProperty.hasExplicitMutability()) {
            return memberProperty.getMutabilities();
        }

        Optional<MutabilityTrait> traitOptional = member.getMemberTrait(model, MutabilityTrait.class);
        if (!traitOptional.isPresent()) {
            return SetUtils.of();
        }

        MutabilityTrait trait = traitOptional.get();
        if (trait.isFullyMutable()) {
            return FULLY_MUTABLE;
        } else if (trait.isCreateAndRead()) {
            return SetUtils.of(Mutability.CREATE, Mutability.READ);
        } else if (trait.isCreate()) {
            return SetUtils.of(Mutability.CREATE);
        } else if (trait.isRead()) {
            return SetUtils.of(Mutability.READ);
        } else if (trait.isWrite()) {
            return SetUtils.of(Mutability.WRITE);
        }
        return SetUtils.of();
    }

    private Set<Mutability> addReadMutability(Set<Mutability> mutabilities) {
        Set<Mutability> newMutabilities = new HashSet<>(mutabilities);
        newMutabilities.add(Mutability.READ);
        return SetUtils.copyOf(newMutabilities);
    }

    private Set<Mutability> addCreateMutability(Set<Mutability> mutabilities) {
        Set<Mutability> newMutabilities = new HashSet<>(mutabilities);
        newMutabilities.add(Mutability.CREATE);
        return SetUtils.copyOf(newMutabilities);
    }

    private Set<Mutability> addWriteMutability(Set<Mutability> mutabilities) {
        Set<Mutability> newMutabilities = new HashSet<>(mutabilities);
        newMutabilities.add(Mutability.WRITE);
        return SetUtils.copyOf(newMutabilities);
    }

    private Set<Mutability> addPutMutability(Set<Mutability> mutabilities) {
        return addWriteMutability(addCreateMutability(mutabilities));
    }

    private final class ExcludedPropertiesVisitor extends ShapeVisitor.Default<Set<ShapeId>> {
        @Override
        protected Set<ShapeId> getDefault(Shape shape) {
            return SetUtils.of();
        }

        @Override
        public Set<ShapeId> structureShape(StructureShape shape) {
            Set<ShapeId> excludedShapes = new HashSet<>();
            for (MemberShape member : shape.members()) {
                if (member.hasTrait(ExcludePropertyTrait.ID)) {
                    excludedShapes.add(member.getId());
                } else {
                    excludedShapes.addAll(model.expectShape(member.getTarget()).accept(this));
                }
            }
            return excludedShapes;
        }
    }
}
